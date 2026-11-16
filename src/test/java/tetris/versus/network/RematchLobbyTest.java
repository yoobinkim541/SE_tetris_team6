package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.component.block.OBlock;
import tetris.feature.network.HeartbeatPolicy;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.lobby.SocketLobbyClient;
import tetris.feature.network.match.ClientBattleClient;
import tetris.feature.network.match.HostBattleClient;
import tetris.feature.network.match.MatchPulse;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.FrameDecoder;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.LobbyPayloads;
import tetris.feature.network.protocol.MatchPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.save.RecentPeers;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchResult;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;

/** S10-C2: 접속 성공 주소 기록과 같은 연결 10판 연속 재대전·이전 판 메시지 무시 검증 (R3-REMATCH, R3-RECENT) */
@Timeout(30)
class RematchLobbyTest {
    private static final long WAIT_SECONDS = 5;
    private static final MatchConfig CLASSIC = MatchConfig.of(BattleMode.CLASSIC);

    static final class ManualPulse implements MatchPulse {
        Runnable tick;

        @Override
        public Handle start(int periodMillis, Runnable tick) {
            this.tick = tick;
            return () -> {};
        }
    }

    static final class RawPeer implements AutoCloseable {
        final Socket socket;
        final FrameEncoder encoder = new FrameEncoder();
        final FrameDecoder decoder = new FrameDecoder();
        final ArrayDeque<Message> buffered = new ArrayDeque<>();

        RawPeer(Socket socket) {
            this.socket = socket;
        }

        void send(Message... messages) throws IOException, ProtocolException {
            OutputStream out = socket.getOutputStream();
            for (Message m : messages) {
                out.write(encoder.encode(m));
            }
            out.flush();
        }

        Message next() throws IOException, ProtocolException {
            InputStream in = socket.getInputStream();
            byte[] buf = new byte[4096];
            while (buffered.isEmpty()) {
                int n = in.read(buf);
                if (n < 0) return null;
                buffered.addAll(decoder.feed(buf, 0, n));
            }
            return buffered.removeFirst();
        }

        Message next(MessageType type) throws Exception {
            Message m;
            while ((m = next()) != null) {
                if (m.type() == type) return m;
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private final AtomicLong now = new AtomicLong(1_000L);
    private final ManualPulse hostPulse = new ManualPulse();
    private final ManualPulse clientPulse = new ManualPulse();
    private ExecutorService hostExec;
    private ExecutorService clientExec;
    private ExecutorService ioExec;
    private SocketLobbyClient host;
    private SocketLobbyClient client;
    private final List<AutoCloseable> rawResources = new ArrayList<>();

    @BeforeEach
    void setUp() {
        hostExec = Executors.newSingleThreadExecutor();
        clientExec = Executors.newSingleThreadExecutor();
        ioExec = Executors.newSingleThreadExecutor();
        host = new SocketLobbyClient(
                hostExec,
                new PeerEstablisher(hostExec),
                () -> new LocalMatchEngine(now::get, id -> OBlock::new),
                now::get,
                hostPulse,
                HeartbeatPolicy.DEFAULT,
                RecentPeers.NONE,
                ioExec);
        client = new SocketLobbyClient(
                clientExec,
                new PeerEstablisher(clientExec),
                () -> new LocalMatchEngine(now::get, id -> OBlock::new),
                now::get,
                clientPulse,
                HeartbeatPolicy.DEFAULT,
                RecentPeers.NONE,
                ioExec);
    }

    @AfterEach
    void tearDown() throws Exception {
        runOn(hostExec, host::close);
        runOn(clientExec, client::close);
        for (AutoCloseable r : rawResources) {
            r.close();
        }
        hostExec.shutdownNow();
        clientExec.shutdownNow();
        ioExec.shutdownNow();
    }

    private <T extends AutoCloseable> T trackRaw(T resource) {
        rawResources.add(resource);
        return resource;
    }

    private static void runOn(ExecutorService exec, Runnable action) throws Exception {
        callOn(exec, () -> {
            action.run();
            return null;
        });
    }

    private static <T> T callOn(ExecutorService exec, Callable<T> action) throws Exception {
        try {
            return exec.submit(action).get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            if (e.getCause() instanceof Error err) throw err;
            throw e;
        }
    }

    private static BlockingQueue<LobbySnapshot> lobbyQueue(ExecutorService exec, SocketLobbyClient lobby)
            throws Exception {
        BlockingQueue<LobbySnapshot> q = new LinkedBlockingQueue<>();
        callOn(exec, () -> lobby.subscribe(q::add));
        return q;
    }

    private static BlockingQueue<MatchSnapshot> battleQueue(ExecutorService exec, SocketLobbyClient lobby)
            throws Exception {
        BlockingQueue<MatchSnapshot> q = new LinkedBlockingQueue<>();
        callOn(exec, () -> lobby.battleClient().orElseThrow().subscribe(q::add));
        return q;
    }

    private static <T> T awaitQueue(BlockingQueue<T> q, Predicate<T> predicate) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (true) {
            long left = deadline - System.nanoTime();
            assertTrue(left > 0, "기대한 상태가 제한 시간 안에 오지 않았다");
            T value = q.poll(left, TimeUnit.NANOSECONDS);
            assertNotNull(value, "기대한 상태가 제한 시간 안에 오지 않았다");
            if (predicate.test(value)) return value;
        }
    }

    @Test
    void 접속에_성공한_주소만_기록한다() throws Exception {
        List<String> recorded = new CopyOnWriteArrayList<>();
        CountDownLatch recordedLatch = new CountDownLatch(1);
        RecentPeers fakeRecent = new RecentPeers() {
            @Override
            public List<String> load() {
                return List.copyOf(recorded);
            }

            @Override
            public void record(String address) {
                recorded.add(address);
                recordedLatch.countDown();
            }
        };

        runOn(hostExec, host::close);
        runOn(clientExec, client::close);
        host = new SocketLobbyClient(
                hostExec,
                new PeerEstablisher(hostExec),
                () -> new LocalMatchEngine(now::get, id -> OBlock::new),
                now::get,
                hostPulse,
                HeartbeatPolicy.DEFAULT,
                fakeRecent,
                ioExec);
        client = new SocketLobbyClient(
                clientExec,
                new PeerEstablisher(clientExec),
                () -> new LocalMatchEngine(now::get, id -> OBlock::new),
                now::get,
                clientPulse,
                HeartbeatPolicy.DEFAULT,
                fakeRecent,
                ioExec);

        BlockingQueue<LobbySnapshot> hs = lobbyQueue(hostExec, host);
        BlockingQueue<LobbySnapshot> cs = lobbyQueue(clientExec, client);

        // 1) 닫힌 포트 접속 실패 -> 기록 없음
        int closedPort;
        try (ServerSocket ss = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = ss.getLocalPort();
        }
        CompletionStage<Void> failedStage = callOn(clientExec, () -> client.connect("127.0.0.1", closedPort));
        assertThrows(ExecutionException.class, () -> failedStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS));
        awaitQueue(cs, s -> s.state() == State.INITIAL);
        runOn(ioExec, () -> {});
        assertTrue(recorded.isEmpty(), "접속 실패는 주소를 기록하지 않는다");

        // 2) 접속 중 취소 -> 기록 없음
        ServerSocket silentServer = trackRaw(new ServerSocket(0, 1, InetAddress.getLoopbackAddress()));
        callOn(clientExec, () -> client.connect("127.0.0.1", silentServer.getLocalPort()));
        runOn(clientExec, client::cancel);
        awaitQueue(cs, s -> s.state() == State.INITIAL);
        runOn(ioExec, () -> {});
        assertTrue(recorded.isEmpty(), "취소한 접속은 주소를 기록하지 않는다");

        // 3) 정상 접속(호스트 + 클라이언트) -> 클라이언트만 1회 기록
        CompletionStage<Void> hostStage = callOn(hostExec, () -> host.host(0));
        int port = callOn(hostExec, host::localPort);
        CompletionStage<Void> clientStage = callOn(clientExec, () -> client.connect(" 127.0.0.1 ", port));
        hostStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        clientStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        awaitQueue(hs, s -> s.state() == State.LOBBY);
        awaitQueue(cs, s -> s.state() == State.LOBBY);

        assertTrue(recordedLatch.await(WAIT_SECONDS, TimeUnit.SECONDS));
        runOn(ioExec, () -> {});
        assertEquals(List.of("127.0.0.1"), recorded);
        assertEquals(List.of("127.0.0.1"), callOn(clientExec, client::recentPeers));
    }

    @Test
    void 저장_실패가_접속을_막지_않는다() throws Exception {
        CountDownLatch attempted = new CountDownLatch(1);
        RecentPeers throwingRecent = new RecentPeers() {
            @Override
            public List<String> load() {
                return List.of();
            }

            @Override
            public void record(String address) {
                attempted.countDown();
                throw new IllegalStateException("disk full");
            }
        };

        runOn(clientExec, client::close);
        client = new SocketLobbyClient(
                clientExec,
                new PeerEstablisher(clientExec),
                () -> new LocalMatchEngine(now::get, id -> OBlock::new),
                now::get,
                clientPulse,
                HeartbeatPolicy.DEFAULT,
                throwingRecent,
                ioExec);

        BlockingQueue<LobbySnapshot> hs = lobbyQueue(hostExec, host);
        BlockingQueue<LobbySnapshot> cs = lobbyQueue(clientExec, client);

        CompletionStage<Void> hostStage = callOn(hostExec, () -> host.host(0));
        int port = callOn(hostExec, host::localPort);
        CompletionStage<Void> clientStage = callOn(clientExec, () -> client.connect("127.0.0.1", port));

        hostStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        clientStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        awaitQueue(hs, s -> s.state() == State.LOBBY);
        awaitQueue(cs, s -> s.state() == State.LOBBY);
        assertTrue(attempted.await(WAIT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    void 같은_연결로_10판을_연속으로_한다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = lobbyQueue(hostExec, host);
        BlockingQueue<LobbySnapshot> cs = lobbyQueue(clientExec, client);

        CompletionStage<Void> hostStage = callOn(hostExec, () -> host.host(0));
        int port = callOn(hostExec, host::localPort);
        CompletionStage<Void> clientStage = callOn(clientExec, () -> client.connect("127.0.0.1", port));
        hostStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        clientStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);

        LobbySnapshot hostFirstLobby = awaitQueue(hs, s -> s.state() == State.LOBBY);
        LobbySnapshot clientFirstLobby = awaitQueue(cs, s -> s.state() == State.LOBBY);
        String hostPeerId = hostFirstLobby.peerIdentity();
        String clientPeerId = clientFirstLobby.peerIdentity();

        runOn(hostExec, () -> host.selectConfig(CLASSIC));
        awaitQueue(hs, s -> s.configRevision() == 1);
        awaitQueue(cs, s -> s.configRevision() == 1);

        Set<String> matchIds = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            runOn(clientExec, () -> client.ready(1, true));
            awaitQueue(hs, LobbySnapshot::peerReady);
            runOn(hostExec, () -> host.ready(1, true));
            awaitQueue(hs, s -> s.state() == State.STARTING);
            awaitQueue(cs, s -> s.state() == State.PLAYING);

            BlockingQueue<MatchSnapshot> hostBattle = battleQueue(hostExec, host);
            awaitQueue(hs, s -> s.state() == State.PLAYING);
            BlockingQueue<MatchSnapshot> clientBattle = battleQueue(clientExec, client);

            String matchId = callOn(hostExec, () -> host.matchId().orElseThrow());
            assertEquals(matchId, callOn(clientExec, () -> client.matchId().orElseThrow()));
            assertTrue(matchIds.add(matchId), "매 판마다 새로운 matchId가 발급되어야 한다");

            runOn(hostExec, () -> host.battleClient().orElseThrow()
                    .send(new PlayerCommand(PlayerId.ONE, GameAction.QUIT, true, 0)));
            awaitQueue(hostBattle, s -> s.phase() == MatchPhase.FINISHED);
            awaitQueue(clientBattle, s -> s.phase() == MatchPhase.FINISHED);

            LobbySnapshot hostResult = awaitQueue(hs, s -> s.state() == State.RESULT);
            LobbySnapshot clientResult = awaitQueue(cs, s -> s.state() == State.RESULT);
            assertFalse(hostResult.localReady() || hostResult.peerReady());
            assertFalse(clientResult.localReady() || clientResult.peerReady());

            runOn(hostExec, host::returnToLobby);
            runOn(clientExec, client::returnToLobby);

            LobbySnapshot hostLobby = awaitQueue(hs, s -> s.state() == State.LOBBY);
            LobbySnapshot clientLobby = awaitQueue(cs, s -> s.state() == State.LOBBY);
            assertFalse(hostLobby.localReady());
            assertFalse(clientLobby.localReady());
            assertEquals(hostPeerId, hostLobby.peerIdentity());
            assertEquals(clientPeerId, clientLobby.peerIdentity());
        }
        assertEquals(10, matchIds.size());
    }

    @Test
    void 재대전_뒤_이전_판_메시지는_무시한다() throws Exception {
        // 1) 호스트에 이전 matchId의 INPUT 주입 -> 새 판 상태 불변, rejectedInputs 증가
        BlockingQueue<LobbySnapshot> hs = lobbyQueue(hostExec, host);
        CompletionStage<Void> hostStage = callOn(hostExec, () -> host.host(0));
        int port = callOn(hostExec, host::localPort);
        RawPeer rawClient = trackRaw(new RawPeer(new Socket(InetAddress.getLoopbackAddress(), port)));
        assertEquals(MessageType.HELLO, rawClient.next().type());
        rawClient.send(ControlPayloads.Hello.message(PeerRole.CLIENT));
        hostStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        awaitQueue(hs, s -> s.state() == State.LOBBY);

        runOn(hostExec, () -> host.selectConfig(CLASSIC));
        assertEquals(MessageType.CONFIG, rawClient.next(MessageType.CONFIG).type());
        rawClient.send(LobbyPayloads.readyMessage(1, true));
        awaitQueue(hs, LobbySnapshot::peerReady);
        runOn(hostExec, () -> host.ready(1, true));
        String match1 = rawClient.next(MessageType.START).matchId();

        callOn(hostExec, () -> host.battleClient().orElseThrow());
        runOn(hostExec, () -> host.battleClient().orElseThrow()
                .send(new PlayerCommand(PlayerId.ONE, GameAction.QUIT, true, 0)));
        rawClient.next(MessageType.RESULT);
        awaitQueue(hs, s -> s.state() == State.RESULT);
        runOn(hostExec, host::returnToLobby);
        awaitQueue(hs, s -> s.state() == State.LOBBY);

        rawClient.send(LobbyPayloads.readyMessage(1, true));
        awaitQueue(hs, LobbySnapshot::peerReady);
        runOn(hostExec, () -> host.ready(1, true));
        String match2 = rawClient.next(MessageType.START).matchId();
        assertNotEquals(match1, match2);

        HostBattleClient battle2 = (HostBattleClient) callOn(hostExec, () -> host.battleClient().orElseThrow());
        MatchSnapshot hostBefore = callOn(hostExec, battle2::snapshot);

        rawClient.send(
                MatchPayloads.inputMessage(match1, new PlayerCommand(PlayerId.TWO, GameAction.PAUSE, true, 0)),
                new ControlPayloads.Ping(77L, 0L).message());
        rawClient.next(MessageType.PONG);

        assertEquals(1, callOn(hostExec, battle2::rejectedInputs));
        MatchSnapshot hostAfter = callOn(hostExec, battle2::snapshot);
        assertEquals(hostBefore.revision(), hostAfter.revision());
        assertEquals(MatchPhase.PLAYING, hostAfter.phase());

        // 2) 클라이언트에 이전 matchId의 STATE·RESULT 주입 -> 새 판 상태·revision 불변
        BlockingQueue<LobbySnapshot> cs = lobbyQueue(clientExec, client);
        ServerSocket rawServer = trackRaw(new ServerSocket(0, 1, InetAddress.getLoopbackAddress()));
        CompletableFuture<Socket> accepted = CompletableFuture.supplyAsync(() -> {
            try {
                return rawServer.accept();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        CompletionStage<Void> clientStage =
                callOn(clientExec, () -> client.connect("127.0.0.1", rawServer.getLocalPort()));
        RawPeer rawHost = trackRaw(new RawPeer(accepted.get(WAIT_SECONDS, TimeUnit.SECONDS)));
        assertEquals(MessageType.HELLO, rawHost.next().type());
        rawHost.send(ControlPayloads.Hello.message(PeerRole.HOST));
        clientStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        awaitQueue(cs, s -> s.state() == State.LOBBY);

        rawHost.send(MatchPayloads.configMessage(CLASSIC, 1), LobbyPayloads.readyMessage(1, true));
        awaitQueue(cs, LobbySnapshot::peerReady);
        runOn(clientExec, () -> client.ready(1, true));
        rawHost.next(MessageType.READY);
        rawHost.send(
                LobbyPayloads.startMessage("c-match-1", 1),
                MatchPayloads.resultMessage("c-match-1", MatchResult.win(PlayerId.ONE, MatchResult.EndReason.FORFEIT)));
        awaitQueue(cs, s -> s.state() == State.RESULT);
        runOn(clientExec, client::returnToLobby);
        awaitQueue(cs, s -> s.state() == State.LOBBY);

        rawHost.send(LobbyPayloads.readyMessage(1, true));
        awaitQueue(cs, LobbySnapshot::peerReady);
        runOn(clientExec, () -> client.ready(1, true));
        rawHost.next(MessageType.READY);
        rawHost.send(LobbyPayloads.startMessage("c-match-2", 1));
        awaitQueue(cs, s -> s.state() == State.PLAYING);

        LocalMatchEngine sampleEngine = new LocalMatchEngine(() -> 0, id -> OBlock::new);
        sampleEngine.start(CLASSIC, "c-match-2");
        sampleEngine.advance(0);
        MatchSnapshot validState = sampleEngine.snapshot();

        BlockingQueue<MatchSnapshot> clientBattleQueue = battleQueue(clientExec, client);
        rawHost.send(MatchPayloads.stateMessage(validState));
        MatchSnapshot currentNew = awaitQueue(clientBattleQueue, s -> s.revision() == validState.revision());
        assertEquals(MatchPhase.PLAYING, currentNew.phase());

        MatchSnapshot staleState = new MatchSnapshot(
                "c-match-1", 99, validState.one(), validState.two(), -1, MatchPhase.PAUSED, null);
        rawHost.send(
                MatchPayloads.stateMessage(staleState),
                MatchPayloads.resultMessage("c-match-1", MatchResult.win(PlayerId.ONE, MatchResult.EndReason.FORFEIT)),
                new ControlPayloads.Ping(88L, 0L).message());
        rawHost.next(MessageType.PONG);

        ClientBattleClient clientBattle =
                (ClientBattleClient) callOn(clientExec, () -> client.battleClient().orElseThrow());
        MatchSnapshot afterStale = callOn(clientExec, clientBattle::snapshot);
        assertEquals("c-match-2", afterStale.matchId());
        assertEquals(validState.revision(), afterStale.revision());
        assertEquals(MatchPhase.PLAYING, afterStale.phase());
    }
}
