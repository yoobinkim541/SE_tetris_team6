package tetris.feature.network.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import tetris.feature.network.CloseReason;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.PeerLink;
import tetris.feature.network.PeerListener;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.match.ClientBattleClient;
import tetris.feature.network.match.DispatcherPulse;
import tetris.feature.network.match.HostBattleClient;
import tetris.feature.network.match.MatchPulse;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.LobbyPayloads;
import tetris.feature.network.protocol.MatchPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.MatchClock;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.MatchSnapshot;

/**
 * {@link PeerEstablisher}와 {@link PeerLink} 위에서 동작하는 소켓 {@link LobbyClient} 구현 (S8-C2).
 *
 * <p>모든 명령 호출·상태 갱신·스냅샷 전달·비동기 stage 완료는 주입한 {@code dispatcher}(앱은 Swing EDT)
 * 한 스레드에서 순서대로 실행된다.
 *
 * <p>S9: START 뒤 경기 핸드오프. 호스트는 {@link #battleClient()}를 처음 부를 때 엔진을 시작하고(STARTING → PLAYING),
 * 클라이언트는 START를 받는 즉시 STATE 수신기를 만든다(TCP 순서상 첫 STATE보다 START가 먼저 온다).
 * 경기가 끝나면 RESULT, {@link #returnToLobby()}로 같은 연결의 LOBBY로 돌아간다. 연결이 끊기면 경기도 함께 닫는다.
 */
public final class SocketLobbyClient implements LobbyClient {
    private static final LobbySnapshot INITIAL_SNAPSHOT =
            new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null);

    private static final class Subscriber {
        final Consumer<LobbySnapshot> listener;
        boolean active = true;

        Subscriber(Consumer<LobbySnapshot> listener) {
            this.listener = listener;
        }
    }

    private final Executor dispatcher;
    private final PeerEstablisher establisher;
    private final List<Subscriber> subscribers = new ArrayList<>();

    private LobbySnapshot snapshot = INITIAL_SNAPSHOT;
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
    private final java.util.ArrayDeque<LobbySnapshot> pendingPublications = new java.util.ArrayDeque<>();
    private boolean publishing;
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
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.establisher = Objects.requireNonNull(establisher, "establisher");
        this.engines = Objects.requireNonNull(engines, "engines");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pulse = Objects.requireNonNull(pulse, "pulse");
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
        LobbySnapshot next = snapshot.selectConfig(config);
        publish(next);
        activeLink.send(MatchPayloads.configMessage(config, Math.toIntExact(next.configRevision())));
    }

    @Override
    public void ready(long configRevision, boolean ready) {
        ensureOpen();
        LobbySnapshot next = snapshot.readyAtRevision(configRevision, ready);
        if (activeLink == null) {
            throw new IllegalStateException("no active peer link");
        }
        publish(next);
        activeLink.send(LobbyPayloads.readyMessage(Math.toIntExact(next.configRevision()), ready));
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
        Role lobbyRole = (link.role() == PeerRole.HOST) ? Role.HOST : Role.CLIENT;
        String peerIdentity = link.peerAddress() + "#" + (++connectionSequence);
        publish(new LobbySnapshot(lobbyRole, State.LOBBY, null, 0, false, false, peerIdentity));
        if (!closed && activeLink == link) {
            link.attach(new LinkListener(link));
        }
        stage.complete(null);
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
        String generatedMatchId = UUID.randomUUID().toString();
        int rev = Math.toIntExact(snapshot.configRevision());
        currentMatchId = generatedMatchId;
        activeLink.send(LobbyPayloads.startMessage(generatedMatchId, rev));
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
            try {
                handleMessage(message);
            } catch (ProtocolException e) {
                link.send(new ControlPayloads.Failure(e.code(), String.valueOf(e.getMessage())).message());
                disconnectToInitial();
            }
        }

        private void handleMessage(Message message) throws ProtocolException {
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
                    if (snapshot.state() == State.LOBBY
                            && snapshot.config() != null
                            && message.configRevision() == snapshot.configRevision()
                            && snapshot.localReady()
                            && snapshot.peerReady()) {
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
                    link.send(ping.pong());
                }
                case LEAVE, ERROR -> disconnectToInitial();
                default -> {
                    // 로비 단계 밖의 메시지는 무시한다
                }
            }
        }

        @Override
        public void onClosed(CloseReason reason, String detail) {
            if (closed || activeLink != link) {
                return;
            }
            activeLink = null;
            boundPort = -1;
            currentMatchId = null;
            closeBattle();
            if (snapshot.state() != State.INITIAL) {
                publish(INITIAL_SNAPSHOT);
            }
        }

        private void disconnectToInitial() {
            activeLink = null;
            boundPort = -1;
            currentMatchId = null;
            closeBattle();
            link.close();
            if (snapshot.state() != State.INITIAL) {
                publish(INITIAL_SNAPSHOT);
            }
        }
    }
}
