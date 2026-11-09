package tetris.feature.network.lobby;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import tetris.feature.network.CloseReason;
import tetris.feature.network.ConnectionHealth;
import tetris.feature.network.HeartbeatPolicy;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.PeerLink;
import tetris.feature.network.PeerListener;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.match.ClientBattleClient;
import tetris.feature.network.match.DispatcherPulse;
import tetris.feature.network.match.HostBattleClient;
import tetris.feature.network.match.MatchPulse;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.LobbyPayloads;
import tetris.feature.network.protocol.MatchPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.save.RecentPeers;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.MatchClock;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.MatchSnapshot;

/**
 * {@link PeerEstablisher}와 {@link PeerLink} 위에서 동작하는 소켓 {@link LobbyClient} 구현 (S8-C2, S10-C1).
 *
 * <p>모든 명령 호출·상태 갱신·스냅샷 전달·비동기 stage 완료는 주입한 {@code dispatcher}(앱은 Swing EDT)
 * 한 스레드에서 순서대로 실행된다.
 *
 * <p>S9: START 뒤 경기 핸드오프. 호스트는 {@link #battleClient()}를 처음 부를 때 엔진을 시작하고(STARTING → PLAYING),
 * 클라이언트는 START를 받는 즉시 STATE 수신기를 만든다(TCP 순서상 첫 STATE보다 START가 먼저 온다).
 * 경기가 끝나면 RESULT, {@link #returnToLobby()}로 같은 연결의 LOBBY로 돌아간다. 연결이 끊기면 경기도 함께 닫는다.
 *
 * <p>S10-C1: 연결이 LOBBY로 수립되면 heartbeat PING을 주기적으로 보내고, 수신 프레임과 PONG RTT로
 * {@link ConnectionHealth}를 갱신해 {@link LinkHealth} 구독자에게 상태 변화를 알린다.
 */
public final class SocketLobbyClient implements LobbyClient {
    private static final LobbySnapshot INITIAL_SNAPSHOT =
            new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null);
    private static final int MAX_PENDING_PINGS = 16;
    private static final Executor DEFAULT_IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lobby-recent-peers-io");
        thread.setDaemon(true);
        return thread;
    });

    private static final class Subscriber {
        final Consumer<LobbySnapshot> listener;
        boolean active = true;

        Subscriber(Consumer<LobbySnapshot> listener) {
            this.listener = listener;
        }
    }

    private static final class HealthSubscriber {
        final Consumer<LinkHealth> listener;
        boolean active = true;

        HealthSubscriber(Consumer<LinkHealth> listener) {
            this.listener = listener;
        }
    }

    private final Executor dispatcher;
    private final PeerEstablisher establisher;
    private final List<Subscriber> subscribers = new ArrayList<>();
    private final List<HealthSubscriber> healthSubscribers = new ArrayList<>();

    private LobbySnapshot snapshot = INITIAL_SNAPSHOT;
    private LinkHealth currentHealth = LinkHealth.NONE;
    private PeerEstablisher.Attempt activeAttempt;
    private CompletableFuture<Void> pendingStage;
    private PeerLink activeLink;
    private int boundPort = -1;
    private long connectionSequence;
    private String currentMatchId;
    private boolean closed;
    private final Supplier<LocalMatchEngine> engines;
    private final MatchClock clock;
    private final MatchPulse pulse;
    private final HeartbeatPolicy policy;
    private final RecentPeers recentPeers;
    private final Executor io;
    private final ArrayDeque<LobbySnapshot> pendingPublications = new ArrayDeque<>();
    private boolean publishing;
    private final ArrayDeque<LinkHealth> pendingHealthPublications = new ArrayDeque<>();
    private boolean publishingHealth;
    private final LinkedHashMap<Long, Long> pendingPings = new LinkedHashMap<>();
    private ConnectionHealth connectionHealth;
    private MatchPulse.Handle heartbeatHandle;
    private long heartbeatNonce;
    private HostBattleClient hostBattle;
    private ClientBattleClient clientBattle;

    public SocketLobbyClient(Executor dispatcher) {
        this(dispatcher, new PeerEstablisher(dispatcher));
    }

    public SocketLobbyClient(Executor dispatcher, PeerEstablisher establisher) {
        this(dispatcher, establisher, LocalMatchEngine::new, MatchClock.system(), new DispatcherPulse(dispatcher));
    }

    /** 엔진·시계·pulse를 주입해 경기 핸드오프를 결정적으로 검증한다 */
    public SocketLobbyClient(Executor dispatcher, PeerEstablisher establisher, Supplier<LocalMatchEngine> engines,
                             MatchClock clock, MatchPulse pulse) {
        this(dispatcher, establisher, engines, clock, pulse, HeartbeatPolicy.DEFAULT);
    }

    /** heartbeat 정책까지 주입해 연결 건강 판정을 결정적으로 검증한다 (S10-C1) */
    public SocketLobbyClient(Executor dispatcher, PeerEstablisher establisher, Supplier<LocalMatchEngine> engines,
                             MatchClock clock, MatchPulse pulse, HeartbeatPolicy policy) {
        this(dispatcher, establisher, engines, clock, pulse, policy, RecentPeers.NONE, DEFAULT_IO);
    }

    /** 최근 접속 주소 저장소와 디스크 I/O 실행기까지 주입한다 (S10-C2) */
    public SocketLobbyClient(Executor dispatcher, PeerEstablisher establisher, Supplier<LocalMatchEngine> engines,
                             MatchClock clock, MatchPulse pulse, HeartbeatPolicy policy,
                             RecentPeers recentPeers, Executor io) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.establisher = Objects.requireNonNull(establisher, "establisher");
        this.engines = Objects.requireNonNull(engines, "engines");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pulse = Objects.requireNonNull(pulse, "pulse");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.recentPeers = Objects.requireNonNull(recentPeers, "recentPeers");
        this.io = Objects.requireNonNull(io, "io");
    }

    /**
     * 저장된 최근 접속 성공 주소 목록(최신순, 최대 5개)을 돌려준다 (S10-C2).
     *
     * <p>파일 읽기가 포함될 수 있으므로 호출 측은 시작 시 한 번 읽거나 EDT 밖에서 부른다.
     */
    public List<String> recentPeers() {
        try {
            return recentPeers.load();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** 현재 연결 건강 상태와 최근 RTT (S10-C1) */
    public LinkHealth health() {
        return currentHealth;
    }

    /** START 확정 이후 경기 핸드오프(S9)에 넘길 matchId */
    public Optional<String> matchId() {
        return Optional.ofNullable(currentMatchId);
    }

    /** 호스트 대기·로비 중 바인드된 로컬 포트(클라이언트나 대기 전은 -1) */
    public int localPort() {
        return boundPort;
    }

    @Override
    public CompletionStage<Void> host(int port) {
        ensureOpen();
        ensureInitial();
        CompletableFuture<Void> stage = new CompletableFuture<>();
        publish(new LobbySnapshot(Role.HOST, State.HOSTING, null, 0, false, false, null));
        PeerEstablisher.Attempt attempt = establisher.host(port);
        this.activeAttempt = attempt;
        this.pendingStage = stage;
        this.boundPort = attempt.localPort();
        attempt.result().whenComplete((link, error) -> onAttemptCompleted(attempt, stage, link, error));
        return stage;
    }

    @Override
    public CompletionStage<Void> connect(String address, int port) {
        ensureOpen();
        ensureInitial();
        CompletableFuture<Void> stage = new CompletableFuture<>();
        publish(new LobbySnapshot(Role.CLIENT, State.CONNECTING, null, 0, false, false, null));
        PeerEstablisher.Attempt attempt = establisher.connect(address, port);
        this.activeAttempt = attempt;
        this.pendingStage = stage;
        this.boundPort = -1;
        attempt.result().whenComplete((link, error) -> onAttemptCompleted(attempt, stage, link, error));
        return stage;
    }

    @Override
    public void cancel() {
        ensureOpen();
        stopHeartbeat();
        currentHealth = LinkHealth.NONE;
        if (activeAttempt == null) {
            return;
        }
        PeerEstablisher.Attempt attempt = activeAttempt;
        CompletableFuture<Void> stage = pendingStage;
        activeAttempt = null;
        pendingStage = null;
        boundPort = -1;
        currentMatchId = null;
        attempt.cancel();
        if (stage != null) {
            stage.cancel(false);
        }
        if (snapshot.state() != State.INITIAL) {
            publish(INITIAL_SNAPSHOT);
        }
    }

    @Override
    public void selectConfig(MatchConfig config) {
        ensureOpen();
        Objects.requireNonNull(config, "config");
        if (snapshot.role() != Role.HOST || snapshot.state() != State.LOBBY || activeLink == null) {
            throw new IllegalStateException("only the host in LOBBY selects config");
        }
        if (snapshot.configRevision() >= Integer.MAX_VALUE) {
            throw new IllegalStateException("config revision exhausted");
        }
        PeerLink link = activeLink;
        LobbySnapshot next = snapshot.selectConfig(config);
        publish(next);
        sendOrDisconnect(link, MatchPayloads.configMessage(config, Math.toIntExact(next.configRevision())));
    }

    @Override
    public void ready(long configRevision, boolean ready) {
        ensureOpen();
        LobbySnapshot next = snapshot.readyAtRevision(configRevision, ready);
        if (activeLink == null) {
            throw new IllegalStateException("no active peer link");
        }
        PeerLink link = activeLink;
        publish(next);
        if (!sendOrDisconnect(link, LobbyPayloads.readyMessage(Math.toIntExact(next.configRevision()), ready))) {
            return;
        }
        if (next.role() == Role.HOST) {
            tryStartMatch();
        }
    }

    @Override
    public void leave() {
        ensureOpen();
        releaseActiveWork();
        if (snapshot.state() != State.INITIAL) {
            publish(INITIAL_SNAPSHOT);
        }
    }

    @Override
    public Optional<BattleClient> battleClient() {
        ensureOpen();
        if (snapshot.role() == Role.HOST && snapshot.state() == State.STARTING && hostBattle == null
                && activeLink != null && currentMatchId != null) {
            PeerLink link = activeLink;
            hostBattle = new HostBattleClient(currentMatchId, snapshot.config(), engines.get(), clock, pulse,
                    link::send, this::onMatchFinished);
            if (snapshot.state() == State.STARTING) publishState(State.PLAYING, true, true);
        }
        State state = snapshot.state();
        if (state != State.STARTING && state != State.PLAYING && state != State.RESULT) return Optional.empty();
        return Optional.ofNullable(hostBattle != null ? hostBattle : clientBattle);
    }

    @Override
    public void returnToLobby() {
        ensureOpen();
        if (snapshot.state() != State.RESULT) throw new IllegalStateException("returnToLobby requires RESULT");
        closeBattle();
        currentMatchId = null;
        publishState(State.LOBBY, false, snapshot.peerReady());
    }

    private void onMatchFinished(MatchSnapshot finalState) {
        if (closed || !finalState.matchId().equals(currentMatchId)) return;
        if (snapshot.state() == State.PLAYING || snapshot.state() == State.STARTING) {
            publishState(State.RESULT, false, false);
        }
    }

    private void publishState(State state, boolean localReady, boolean peerReady) {
        publish(new LobbySnapshot(snapshot.role(), state, snapshot.config(), snapshot.configRevision(),
                localReady, peerReady, snapshot.peerIdentity()));
    }

    private void closeBattle() {
        HostBattleClient host = hostBattle;
        ClientBattleClient client = clientBattle;
        hostBattle = null;
        clientBattle = null;
        if (host != null) host.close();
        if (client != null) client.close();
    }

    @Override
    public Subscription subscribe(Consumer<LobbySnapshot> listener) {
        Objects.requireNonNull(listener, "listener");
        ensureOpen();
        Subscriber sub = new Subscriber(listener);
        subscribers.add(sub);
        listener.accept(snapshot);
        return () -> {
            if (sub.active) {
                sub.active = false;
                subscribers.remove(sub);
            }
        };
    }

    @Override
    public Subscription subscribeHealth(Consumer<LinkHealth> listener) {
        Objects.requireNonNull(listener, "listener");
        ensureOpen();
        HealthSubscriber sub = new HealthSubscriber(listener);
        healthSubscribers.add(sub);
        listener.accept(currentHealth);
        return () -> {
            if (sub.active) {
                sub.active = false;
                healthSubscribers.remove(sub);
            }
        };
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        releaseActiveWork();
        for (Subscriber sub : subscribers) {
            sub.active = false;
        }
        subscribers.clear();
        for (HealthSubscriber sub : healthSubscribers) {
            sub.active = false;
        }
        healthSubscribers.clear();
    }

    private void onAttemptCompleted(
            PeerEstablisher.Attempt attempt,
            CompletableFuture<Void> stage,
            PeerLink link,
            Throwable error) {
        if (closed || activeAttempt != attempt) {
            if (link != null) {
                link.close();
            }
            return;
        }
        activeAttempt = null;
        pendingStage = null;

        if (error != null) {
            boundPort = -1;
            currentMatchId = null;
            if (snapshot.state() != State.INITIAL) {
                publish(INITIAL_SNAPSHOT);
            }
            Throwable cause = unwrap(error);
            if (cause instanceof CancellationException) {
                stage.cancel(false);
            } else {
                stage.completeExceptionally(cause);
            }
            return;
        }

        activeLink = link;
        currentMatchId = null;
        startHeartbeat(link);
        Role lobbyRole = (link.role() == PeerRole.HOST) ? Role.HOST : Role.CLIENT;
        if (lobbyRole == Role.CLIENT) {
            String peerAddress = link.peerAddress();
            try {
                io.execute(() -> {
                    try {
                        recentPeers.record(peerAddress);
                    } catch (RuntimeException ignored) {
                        // 최근 주소 저장 실패가 접속이나 EDT를 막지 않는다
                    }
                });
            } catch (RuntimeException ignored) {
                // 실행기 거부 시에도 접속은 유지한다
            }
        }
        String peerIdentity = link.peerAddress() + "#" + (++connectionSequence);
        publish(new LobbySnapshot(lobbyRole, State.LOBBY, null, 0, false, false, peerIdentity));
        if (!closed && activeLink == link) {
            link.attach(new LinkListener(link));
        }
        stage.complete(null);
    }

    private void startHeartbeat(PeerLink link) {
        stopHeartbeat();
        connectionHealth = new ConnectionHealth(policy, clock.nowMillis());
        publishHealth(new LinkHealth(LinkHealth.Status.NORMAL, -1));
        heartbeatHandle = pulse.start((int) policy.intervalMillis(), () -> heartbeat(link));
    }

    private void stopHeartbeat() {
        MatchPulse.Handle handle = heartbeatHandle;
        heartbeatHandle = null;
        connectionHealth = null;
        pendingPings.clear();
        heartbeatNonce = 0;
        if (handle != null) {
            handle.close();
        }
    }

    private void heartbeat(PeerLink link) {
        if (closed || activeLink != link || connectionHealth == null) {
            return;
        }
        long now = clock.nowMillis();
        long nonce = ++heartbeatNonce;
        if (pendingPings.size() >= MAX_PENDING_PINGS) {
            Iterator<Long> it = pendingPings.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        pendingPings.put(nonce, now);
        if (!sendOrDisconnect(link, new ControlPayloads.Ping(nonce, System.nanoTime()).message())) {
            return;
        }
        ConnectionHealth.Status evaluated = connectionHealth.evaluate(now);
        if (evaluated == ConnectionHealth.Status.LOST) {
            terminateConnectionWithHealth(link, LinkHealth.Status.LOST);
        } else {
            syncHealthFromConnection();
        }
    }

    private boolean sendOrDisconnect(PeerLink link, Message message) {
        if (activeLink != link) {
            return false;
        }
        if (!link.send(message)) {
            terminateConnectionWithHealth(link, LinkHealth.Status.LOST);
            return false;
        }
        return true;
    }

    private void syncHealthFromConnection() {
        if (connectionHealth == null) {
            return;
        }
        LinkHealth.Status mapped = switch (connectionHealth.status()) {
            case NORMAL -> LinkHealth.Status.NORMAL;
            case LAGGING -> LinkHealth.Status.LAGGING;
            case LOST -> LinkHealth.Status.LOST;
        };
        publishHealth(new LinkHealth(mapped, connectionHealth.lastRttMillis()));
    }

    private void terminateConnectionWithHealth(PeerLink link, LinkHealth.Status terminalStatus) {
        if (closed || activeLink != link) {
            return;
        }
        long lastRtt = connectionHealth != null ? connectionHealth.lastRttMillis() : -1L;
        stopHeartbeat();
        activeLink = null;
        boundPort = -1;
        currentMatchId = null;
        closeBattle();
        link.close();
        LinkHealth nextHealth = new LinkHealth(terminalStatus, lastRtt);
        boolean statusChanged = nextHealth.status() != currentHealth.status();
        currentHealth = nextHealth;
        if (snapshot.state() != State.INITIAL) {
            publish(INITIAL_SNAPSHOT);
        }
        if (statusChanged) {
            notifyHealthSubscribers(nextHealth);
        }
    }

    private void publishHealth(LinkHealth next) {
        boolean statusChanged = next.status() != currentHealth.status();
        currentHealth = next;
        if (!statusChanged) {
            return;
        }
        notifyHealthSubscribers(next);
    }

    private void notifyHealthSubscribers(LinkHealth next) {
        pendingHealthPublications.addLast(next);
        if (publishingHealth) return;
        publishingHealth = true;
        try {
            while (!pendingHealthPublications.isEmpty()) {
                LinkHealth value = pendingHealthPublications.removeFirst();
                for (HealthSubscriber sub : List.copyOf(healthSubscribers)) {
                    if (sub.active && !closed) {
                        sub.listener.accept(value);
                    }
                }
            }
        } finally {
            publishingHealth = false;
            pendingHealthPublications.clear();
        }
    }

    private void tryStartMatch() {
        if (snapshot.role() != Role.HOST
                || snapshot.state() != State.LOBBY
                || snapshot.config() == null
                || !snapshot.localReady()
                || !snapshot.peerReady()
                || activeLink == null) {
            return;
        }
        PeerLink link = activeLink;
        String generatedMatchId = UUID.randomUUID().toString();
        int rev = Math.toIntExact(snapshot.configRevision());
        currentMatchId = generatedMatchId;
        if (!sendOrDisconnect(link, LobbyPayloads.startMessage(generatedMatchId, rev))) {
            return;
        }
        publish(new LobbySnapshot(
                Role.HOST,
                State.STARTING,
                snapshot.config(),
                snapshot.configRevision(),
                true,
                true,
                snapshot.peerIdentity()));
    }

    private void releaseActiveWork() {
        PeerEstablisher.Attempt attempt = activeAttempt;
        CompletableFuture<Void> stage = pendingStage;
        PeerLink link = activeLink;
        stopHeartbeat();
        currentHealth = LinkHealth.NONE;
        activeAttempt = null;
        pendingStage = null;
        activeLink = null;
        boundPort = -1;
        currentMatchId = null;
        closeBattle();
        if (attempt != null) {
            attempt.cancel();
        }
        if (stage != null) {
            stage.cancel(false);
        }
        if (link != null) {
            link.close();
        }
    }

    private void publish(LobbySnapshot next) {
        snapshot = next;
        // 구독자 콜백 안에서 다시 발행해도(예: STARTING을 받고 battleClient()로 PLAYING) 모든 구독자가 같은 순서로 받는다
        pendingPublications.addLast(next);
        if (publishing) return;
        publishing = true;
        try {
            while (!pendingPublications.isEmpty()) {
                LobbySnapshot value = pendingPublications.removeFirst();
                for (Subscriber sub : List.copyOf(subscribers)) {
                    if (sub.active && !closed) {
                        sub.listener.accept(value);
                    }
                }
            }
        } finally {
            publishing = false;
            pendingPublications.clear();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("LobbyClient is closed");
        }
    }

    private void ensureInitial() {
        if (snapshot.state() != State.INITIAL) {
            throw new IllegalStateException("expected INITIAL state but was " + snapshot.state());
        }
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private final class LinkListener implements PeerListener {
        private final PeerLink link;

        LinkListener(PeerLink link) {
            this.link = link;
        }

        @Override
        public void onMessage(Message message) {
            if (closed || activeLink != link) {
                return;
            }
            long now = clock.nowMillis();
            if (connectionHealth != null) {
                connectionHealth.onFrame(now);
                syncHealthFromConnection();
            }
            try {
                handleMessage(message, now);
            } catch (ProtocolException e) {
                link.send(new ControlPayloads.Failure(e.code(), String.valueOf(e.getMessage())).message());
                terminateConnectionWithHealth(link, LinkHealth.Status.LOST);
            }
        }

        private void handleMessage(Message message, long now) throws ProtocolException {
            switch (message.type()) {
                case CONFIG -> {
                    if (snapshot.role() != Role.CLIENT) {
                        return; // 호스트가 받은 CONFIG는 무시한다
                    }
                    MatchConfig nextConfig = MatchPayloads.readConfig(message);
                    State state = snapshot.state();
                    // 호스트가 먼저 로비로 돌아가 설정을 바꿨을 수 있다. RESULT에서도 받아 둔다
                    if ((state != State.LOBBY && state != State.RESULT)
                            || message.configRevision() <= snapshot.configRevision()) {
                        return;
                    }
                    publish(new LobbySnapshot(
                            Role.CLIENT,
                            state,
                            nextConfig,
                            message.configRevision(),
                            false,
                            false,
                            snapshot.peerIdentity()));
                }
                case READY -> {
                    boolean peerReady = LobbyPayloads.readReady(message);
                    State state = snapshot.state();
                    // RESULT 화면에 남아 있는 동안 온 상대 준비도 기억했다가 로비로 돌아갈 때 반영한다
                    if ((state != State.LOBBY && state != State.RESULT)
                            || snapshot.config() == null
                            || message.configRevision() != snapshot.configRevision()) {
                        return;
                    }
                    publish(new LobbySnapshot(
                            snapshot.role(),
                            state,
                            snapshot.config(),
                            snapshot.configRevision(),
                            snapshot.localReady(),
                            peerReady,
                            snapshot.peerIdentity()));
                    if (snapshot.role() == Role.HOST && state == State.LOBBY) {
                        tryStartMatch();
                    }
                }
                case START -> {
                    if (snapshot.role() != Role.CLIENT) {
                        return;
                    }
                    // 호스트 권위: 호스트는 이쪽 READY를 받은 뒤에만 START를 보낸다. 그 사이 준비를 취소했더라도
                    // 같은 설정 revision이면 따라 들어가야 호스트 혼자 경기하는 일이 없다
                    if (snapshot.state() == State.LOBBY
                            && snapshot.config() != null
                            && message.configRevision() == snapshot.configRevision()) {
                        currentMatchId = message.matchId();
                        clientBattle = new ClientBattleClient(currentMatchId, snapshot.config(), link::send,
                                SocketLobbyClient.this::onMatchFinished);
                        publish(new LobbySnapshot(
                                Role.CLIENT,
                                State.STARTING,
                                snapshot.config(),
                                snapshot.configRevision(),
                                true,
                                true,
                                snapshot.peerIdentity()));
                        if (clientBattle != null && snapshot.state() == State.STARTING) {
                            publishState(State.PLAYING, true, true);
                        }
                    }
                }
                case INPUT -> {
                    if (hostBattle != null) hostBattle.onRemote(message);
                }
                case STATE, RESULT -> {
                    if (clientBattle != null) clientBattle.onRemote(message);
                }
                case PING -> {
                    ControlPayloads.Ping ping = ControlPayloads.Ping.from(message.payload());
                    sendOrDisconnect(link, ping.pong());
                }
                case PONG -> {
                    ControlPayloads.Ping pong = ControlPayloads.Ping.from(message.payload());
                    Long sentAt = pendingPings.remove(pong.nonce());
                    if (sentAt != null && connectionHealth != null) {
                        connectionHealth.onRtt(Math.max(0L, now - sentAt), now);
                        syncHealthFromConnection();
                    }
                }
                case LEAVE -> {
                    ControlPayloads.Leave.from(message.payload());
                    terminateConnectionWithHealth(link, LinkHealth.Status.PEER_LEFT);
                }
                case ERROR -> {
                    ControlPayloads.Failure.from(message.payload());
                    terminateConnectionWithHealth(link, LinkHealth.Status.LOST);
                }
                default -> {
                    // 로비 단계 밖의 메시지는 무시한다
                }
            }
        }

        @Override
        public void onClosed(CloseReason reason, String detail) {
            terminateConnectionWithHealth(link, LinkHealth.Status.LOST);
        }
    }
}
