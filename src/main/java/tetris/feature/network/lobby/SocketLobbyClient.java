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
import tetris.feature.network.CloseReason;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.PeerLink;
import tetris.feature.network.PeerListener;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.LobbyPayloads;
import tetris.feature.network.protocol.MatchPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.MatchConfig;

/**
 * {@link PeerEstablisher}와 {@link PeerLink} 위에서 동작하는 소켓 {@link LobbyClient} 구현 (S8-C2).
 *
 * <p>모든 명령 호출·상태 갱신·스냅샷 전달·비동기 stage 완료는 주입한 {@code dispatcher}(앱은 Swing EDT)
 * 한 스레드에서 순서대로 실행된다.
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

    public SocketLobbyClient(Executor dispatcher) {
        this(dispatcher, new PeerEstablisher(dispatcher));
    }

    public SocketLobbyClient(Executor dispatcher, PeerEstablisher establisher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.establisher = Objects.requireNonNull(establisher, "establisher");
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
        return Optional.empty();
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
        for (Subscriber sub : List.copyOf(subscribers)) {
            if (sub.active && !closed) {
                sub.listener.accept(next);
            }
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
                    if (snapshot.state() != State.LOBBY || message.configRevision() <= snapshot.configRevision()) {
                        return;
                    }
                    publish(new LobbySnapshot(
                            Role.CLIENT,
                            State.LOBBY,
                            nextConfig,
                            message.configRevision(),
                            false,
                            false,
                            snapshot.peerIdentity()));
                }
                case READY -> {
                    boolean peerReady = LobbyPayloads.readReady(message);
                    if (snapshot.state() != State.LOBBY
                            || snapshot.config() == null
                            || message.configRevision() != snapshot.configRevision()) {
                        return;
                    }
                    publish(new LobbySnapshot(
                            snapshot.role(),
                            State.LOBBY,
                            snapshot.config(),
                            snapshot.configRevision(),
                            snapshot.localReady(),
                            peerReady,
                            snapshot.peerIdentity()));
                    if (snapshot.role() == Role.HOST) {
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
                        publish(new LobbySnapshot(
                                Role.CLIENT,
                                State.STARTING,
                                snapshot.config(),
                                snapshot.configRevision(),
                                true,
                                true,
                                snapshot.peerIdentity()));
                    }
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
            if (snapshot.state() != State.INITIAL) {
                publish(INITIAL_SNAPSHOT);
            }
        }

        private void disconnectToInitial() {
            activeLink = null;
            boundPort = -1;
            currentMatchId = null;
            link.close();
            if (snapshot.state() != State.INITIAL) {
                publish(INITIAL_SNAPSHOT);
            }
        }
    }
}
