package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.lobby.SocketLobbyClient;
import tetris.feature.network.match.ClientBattleClient;
import tetris.feature.network.match.DispatcherPulse;
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
import tetris.feature.setting.GameAction;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.*;

/** S9-C1/C2: 실제 loopback 소켓 위 호스트 권위 경기 핸드오프·입력·상태·결과 (R3-READY, R3-LIVE, R3-LOSS) */
@Timeout(30)
class P2pMatchTest {
    private static final long WAIT = 5;

    /** 테스트가 직접 tick을 부르는 pulse. tick은 호스트 dispatcher에서 실행해야 한다 */
    static final class ManualPulse implements MatchPulse {
        Runnable tick;
        boolean stopped;
        @Override public Handle start(int periodMillis, Runnable tick) {
            this.tick = tick;
            return () -> stopped = true;
        }
    }

    final AtomicLong now = new AtomicLong();
    final ManualPulse pulse = new ManualPulse();
    ExecutorService hostExec, clientExec;
    SocketLobbyClient host, client;
    final List<AutoCloseable> raw = new ArrayList<>();

    @BeforeEach
    void setUp() {
        hostExec = Executors.newSingleThreadExecutor();
        clientExec = Executors.newSingleThreadExecutor();
        host = new SocketLobbyClient(hostExec, new PeerEstablisher(hostExec),
                () -> new LocalMatchEngine(now::get, id -> OBlock::new), now::get, pulse);
        client = new SocketLobbyClient(clientExec);
    }

    @AfterEach
    void tearDown() throws Exception {
        run(hostExec, host::close);
        run(clientExec, client::close);
        for (AutoCloseable r : raw) r.close();
        hostExec.shutdownNow();
        clientExec.shutdownNow();
    }

    static void run(ExecutorService exec, Runnable action) throws Exception {
        call(exec, () -> { action.run(); return null; });
    }

    static <T> T call(ExecutorService exec, Callable<T> action) throws Exception {
        try {
            return exec.submit(action).get(WAIT, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            if (e.getCause() instanceof Error err) throw err;
            throw e;
        }
    }

    static <T> T await(BlockingQueue<T> queue, Predicate<T> predicate) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT);
        while (true) {
            long left = deadline - System.nanoTime();
            assertTrue(left > 0, "expected value did not arrive");
            T value = queue.poll(left, TimeUnit.NANOSECONDS);
            assertNotNull(value, "expected value did not arrive");
            if (predicate.test(value)) return value;
        }
    }

    BlockingQueue<LobbySnapshot> lobbyQueue(ExecutorService exec, SocketLobbyClient lobby) throws Exception {
        BlockingQueue<LobbySnapshot> q = new LinkedBlockingQueue<>();
        call(exec, () -> lobby.subscribe(q::add));
        return q;
    }

    BlockingQueue<MatchSnapshot> battleQueue(ExecutorService exec, SocketLobbyClient lobby) throws Exception {
        BlockingQueue<MatchSnapshot> q = new LinkedBlockingQueue<>();
        call(exec, () -> lobby.battleClient().orElseThrow().subscribe(q::add));
        return q;
    }

    void startMatch(BlockingQueue<LobbySnapshot> hs, BlockingQueue<LobbySnapshot> cs, MatchConfig config) throws Exception {
        CompletionStage<Void> hostStage = call(hostExec, () -> host.host(0));
        int port = call(hostExec, host::localPort);
        CompletionStage<Void> clientStage = call(clientExec, () -> client.connect("127.0.0.1", port));
        hostStage.toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        clientStage.toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        await(hs, s -> s.state() == State.LOBBY);
        await(cs, s -> s.state() == State.LOBBY);
        run(hostExec, () -> host.selectConfig(config));
        await(cs, s -> s.configRevision() == 1);
        run(clientExec, () -> client.ready(1, true));
        await(hs, LobbySnapshot::peerReady);
        run(hostExec, () -> host.ready(1, true));
        await(hs, s -> s.state() == State.STARTING);
        await(cs, s -> s.state() == State.PLAYING);
    }

    void tick() throws Exception {
        run(hostExec, () -> pulse.tick.run());
    }

    @Test
    void readyHandoffRealInputAndIdenticalForfeitResultOverLoopback() throws Exception {
        var hs = lobbyQueue(hostExec, host);
        var cs = lobbyQueue(clientExec, client);
        startMatch(hs, cs, MatchConfig.of(BattleMode.CLASSIC));

        assertTrue(call(clientExec, client::battleClient).isPresent(), "client receiver exists right after START");
        assertEquals(Optional.empty(), call(clientExec, () -> client.battleClient().map(b -> (Object) b)
                .filter(b -> b instanceof HostBattleClient)), "the client never runs an engine");
        var hostBattle = battleQueue(hostExec, host);
        await(hs, s -> s.state() == State.PLAYING);
        var clientBattle = battleQueue(clientExec, client);

        String matchId = call(hostExec, host::matchId).orElseThrow();
        assertEquals(Optional.of(matchId), call(clientExec, client::matchId));
        MatchSnapshot first = await(clientBattle, s -> s.phase() == MatchPhase.PLAYING);
        assertEquals(matchId, first.matchId());

        // client presses with its own P1 keys; it controls only player TWO on the host
        run(clientExec, () -> client.battleClient().orElseThrow()
                .send(new PlayerCommand(PlayerId.ONE, GameAction.HARD_DROP, true, 0)));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT);
        long twoScore = 0;
        while (twoScore == 0 && System.nanoTime() < deadline) {
            now.addAndGet(16);
            tick();
            twoScore = call(hostExec, () -> ((HostBattleClient) host.battleClient().orElseThrow()).snapshot().two().score());
        }
        assertTrue(twoScore > 0, "client input reached the host engine as player TWO");
        assertEquals(0, call(hostExec, () -> ((HostBattleClient) host.battleClient().orElseThrow()).snapshot().one().score()));
        now.addAndGet(HostBattleClient.STATE_INTERVAL_MILLIS);
        tick();
        long expected = twoScore;
        await(clientBattle, s -> s.two().score() == expected);

        // the host forfeits: both sides see the same final result and return to RESULT
        run(hostExec, () -> host.battleClient().orElseThrow()
                .send(new PlayerCommand(PlayerId.ONE, GameAction.QUIT, true, 1)));
        MatchSnapshot hostEnd = await(hostBattle, s -> s.phase() == MatchPhase.FINISHED);
        MatchSnapshot clientEnd = await(clientBattle, s -> s.phase() == MatchPhase.FINISHED);
        assertEquals(MatchResult.win(PlayerId.TWO, MatchResult.EndReason.FORFEIT), hostEnd.result());
        assertEquals(hostEnd.result(), clientEnd.result());
        assertEquals(hostEnd.revision(), clientEnd.revision());
        assertEquals(hostEnd.two().score(), clientEnd.two().score());
        await(hs, s -> s.state() == State.RESULT);
        await(cs, s -> s.state() == State.RESULT);
        assertTrue(pulse.stopped, "host pulse stops with the match");

        // same connection goes back to LOBBY with the votes cleared (F-07)
        run(clientExec, client::returnToLobby);
        run(hostExec, host::returnToLobby);
        LobbySnapshot hostLobby = await(hs, s -> s.state() == State.LOBBY);
        assertFalse(hostLobby.localReady());
        assertEquals(Optional.empty(), call(hostExec, host::battleClient));
        assertEquals(State.LOBBY, await(cs, s -> s.state() == State.LOBBY).state());
    }

    @Test
    void clientPauseIsAppliedByTheHostWithoutApproval() throws Exception {
        var hs = lobbyQueue(hostExec, host);
        var cs = lobbyQueue(clientExec, client);
        startMatch(hs, cs, MatchConfig.of(BattleMode.TIMED));
        var hostBattle = battleQueue(hostExec, host);
        var clientBattle = battleQueue(clientExec, client);
        await(clientBattle, s -> s.phase() == MatchPhase.PLAYING);

        now.addAndGet(1_500);
        tick();

        run(clientExec, () -> client.battleClient().orElseThrow()
                .send(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, true, 0)));
        await(hostBattle, s -> s.phase() == MatchPhase.PAUSED);
        MatchSnapshot paused = await(clientBattle, s -> s.phase() == MatchPhase.PAUSED);
        assertEquals(VersusRules.DEFAULT_TIME_LIMIT_MILLIS - 1_500, paused.remainingMillis(), "remaining time is the host's");
    }

    @Test
    void connectionLossMidMatchClosesTheBattleAndReturnsToInitial() throws Exception {
        var hs = lobbyQueue(hostExec, host);
        var cs = lobbyQueue(clientExec, client);
        startMatch(hs, cs, MatchConfig.of(BattleMode.CLASSIC));
        battleQueue(hostExec, host);
        run(clientExec, client::leave);
        await(hs, s -> s.state() == State.INITIAL);
        assertEquals(Optional.empty(), call(hostExec, host::battleClient));
        assertTrue(pulse.stopped, "pulse released with the connection");
    }

    // ── raw peer: forged / stale messages ─────────────

    static final class RawPeer implements AutoCloseable {
        final Socket socket;
        final FrameEncoder encoder = new FrameEncoder();
        final FrameDecoder decoder = new FrameDecoder();
        final ArrayDeque<Message> buffered = new ArrayDeque<>();
        RawPeer(Socket socket) { this.socket = socket; }
        void send(Message... messages) throws IOException, ProtocolException {
            OutputStream out = socket.getOutputStream();
            for (Message m : messages) out.write(encoder.encode(m));
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
            while ((m = next()) != null) if (m.type() == type) return m;
            return null;
        }
        @Override public void close() throws IOException { socket.close(); }
    }

    @Test
    void hostRejectsForgedPlayerAndStaleMatchInputs() throws Exception {
        var hs = lobbyQueue(hostExec, host);
        CompletionStage<Void> stage = call(hostExec, () -> host.host(0));
        int port = call(hostExec, host::localPort);
        RawPeer peer = new RawPeer(new Socket(InetAddress.getLoopbackAddress(), port));
        raw.add(peer);
        assertEquals(MessageType.HELLO, peer.next().type());
        peer.send(ControlPayloads.Hello.message(PeerRole.CLIENT));
        stage.toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        run(hostExec, () -> host.selectConfig(MatchConfig.of(BattleMode.CLASSIC)));
        peer.send(LobbyPayloads.readyMessage(1, true));
        await(hs, LobbySnapshot::peerReady);
        run(hostExec, () -> host.ready(1, true));
        Message start = peer.next(MessageType.START);
        String matchId = start.matchId();
        HostBattleClient battle = (HostBattleClient) call(hostExec, () -> host.battleClient().orElseThrow());

        peer.send(MatchPayloads.inputMessage(matchId, new PlayerCommand(PlayerId.ONE, GameAction.HARD_DROP, true, 0)),
                MatchPayloads.inputMessage("old-match", new PlayerCommand(PlayerId.TWO, GameAction.HARD_DROP, true, 1)),
                MatchPayloads.inputMessage(matchId, new PlayerCommand(PlayerId.TWO, GameAction.HARD_DROP, true, 2)),
                MatchPayloads.inputMessage(matchId, new PlayerCommand(PlayerId.TWO, GameAction.HARD_DROP, false, 3)));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT);
        // 네 INPUT이 모두 처리될 때까지 기다린다. 거부 2건만 보고 판정하면 유효 입력이 아직 수신 전일 수 있다
        while ((call(hostExec, battle::rejectedInputs) < 2 || call(hostExec, battle::acceptedRemoteInputs) < 2)
                && System.nanoTime() < deadline) Thread.onSpinWait();
        now.addAndGet(16);
        tick();
        assertEquals(2, call(hostExec, battle::rejectedInputs), "forged player ONE and stale matchId");
        MatchSnapshot state = call(hostExec, battle::snapshot);
        assertEquals(0, state.one().score(), "the client can never move the host's board");
        assertTrue(state.two().score() > 0, "the valid input counted once");
        long scored = state.two().score();
        peer.send(MatchPayloads.inputMessage(matchId, new PlayerCommand(PlayerId.TWO, GameAction.HARD_DROP, true, 3)));
        long dupDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
        while (call(hostExec, battle::rejectedInputs) < 3 && System.nanoTime() < dupDeadline) Thread.onSpinWait();
        now.addAndGet(16);
        tick();
        assertEquals(3, call(hostExec, battle::rejectedInputs), "duplicate remote sequence rejected");
        assertEquals(scored, call(hostExec, battle::snapshot).two().score(), "duplicate sequence ignored");
    }

    @Test
    void clientShowsOnlyNewerStatesOfTheCurrentMatch() throws Exception {
        var cs = lobbyQueue(clientExec, client);
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        raw.add(server);
        CompletableFuture<Socket> accepted = CompletableFuture.supplyAsync(() -> {
            try { return server.accept(); } catch (IOException e) { throw new RuntimeException(e); }
        });
        CompletionStage<Void> stage = call(clientExec, () -> client.connect("127.0.0.1", server.getLocalPort()));
        RawPeer peer = new RawPeer(accepted.get(WAIT, TimeUnit.SECONDS));
        raw.add(peer);
        assertEquals(MessageType.HELLO, peer.next().type());
        peer.send(ControlPayloads.Hello.message(PeerRole.HOST));
        stage.toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        MatchConfig config = MatchConfig.of(BattleMode.CLASSIC);
        peer.send(MatchPayloads.configMessage(config, 1), LobbyPayloads.readyMessage(1, true));
        await(cs, LobbySnapshot::peerReady);
        run(clientExec, () -> client.ready(1, true));
        peer.next(MessageType.READY);
        peer.send(LobbyPayloads.startMessage("m-1", 1));
        await(cs, s -> s.state() == State.PLAYING);
        var battle = battleQueue(clientExec, client);
        assertEquals(MatchPhase.COUNTDOWN, battle.take().phase(), "waits for the first host STATE");

        var engine = new LocalMatchEngine(() -> 0, id -> OBlock::new);
        engine.start(config, "m-1");
        for (int i = 0; i < 5; i++) engine.advance(0);
        MatchSnapshot five = engine.snapshot();
        MatchSnapshot older = new MatchSnapshot("m-1", 3, five.one(), five.two(), -1, MatchPhase.PLAYING, null);
        MatchSnapshot otherMatch = new MatchSnapshot("m-0", 9, five.one(), five.two(), -1, MatchPhase.PLAYING, null);
        peer.send(MatchPayloads.stateMessage(five), MatchPayloads.stateMessage(older),
                MatchPayloads.stateMessage(otherMatch),
                MatchPayloads.resultMessage("m-0", MatchResult.win(PlayerId.ONE, MatchResult.EndReason.FORFEIT)),
                MatchPayloads.resultMessage("m-1", MatchResult.win(PlayerId.TWO, MatchResult.EndReason.FORFEIT)));
        assertEquals(5, battle.poll(WAIT, TimeUnit.SECONDS).revision());
        MatchSnapshot end = battle.poll(WAIT, TimeUnit.SECONDS);
        assertEquals(MatchPhase.FINISHED, end.phase(), "older revision and other match were dropped");
        assertEquals(PlayerId.TWO, end.result().winner());
        await(cs, s -> s.state() == State.RESULT);
        run(clientExec, () -> client.battleClient().orElseThrow()
                .send(new PlayerCommand(PlayerId.ONE, GameAction.HARD_DROP, true, 0)));
        run(clientExec, client::returnToLobby);
        assertEquals(State.LOBBY, await(cs, s -> s.state() == State.LOBBY).state());
        assertTrue(call(clientExec, client::battleClient).isEmpty());
        run(clientExec, client::leave);
        assertNull(peer.next(), "RESULT 뒤 send는 INPUT 프레임을 보내지 않는다");
    }

    /** Review I-2: 호스트가 START를 보낸 직후 클라이언트가 준비를 취소해도 같은 경기에 들어간다 (호스트 권위) */
    @Test
    void clientFollowsTheHostStartEvenIfItsLastVoteWasWithdrawnInFlight() throws Exception {
        var cs = lobbyQueue(clientExec, client);
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        raw.add(server);
        CompletableFuture<Socket> accepted = CompletableFuture.supplyAsync(() -> {
            try { return server.accept(); } catch (IOException e) { throw new RuntimeException(e); }
        });
        CompletionStage<Void> stage = call(clientExec, () -> client.connect("127.0.0.1", server.getLocalPort()));
        RawPeer peer = new RawPeer(accepted.get(WAIT, TimeUnit.SECONDS));
        raw.add(peer);
        assertEquals(MessageType.HELLO, peer.next().type());
        peer.send(ControlPayloads.Hello.message(PeerRole.HOST));
        stage.toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        peer.send(MatchPayloads.configMessage(MatchConfig.of(BattleMode.CLASSIC), 1), LobbyPayloads.readyMessage(1, true));
        await(cs, LobbySnapshot::peerReady);
        run(clientExec, () -> client.ready(1, true));
        run(clientExec, () -> client.ready(1, false)); // withdrawn while the host's START is already on the wire
        peer.send(LobbyPayloads.startMessage("m-race", 1));
        await(cs, s -> s.state() == State.PLAYING);
        assertEquals(Optional.of("m-race"), call(clientExec, client::matchId));
        assertTrue(call(clientExec, client::battleClient).isPresent(), "the client must not leave the host playing alone");
    }

    // ── STATE coalescing (unit) ─────────────

    @Test
    void hostIgnoresDuplicateRemotePauseSequence() throws Exception {
        AtomicLong clock = new AtomicLong();
        ManualPulse manual = new ManualPulse();
        var battle = new HostBattleClient("m", MatchConfig.of(BattleMode.CLASSIC),
                new LocalMatchEngine(clock::get, id -> OBlock::new), clock::get, manual, m -> true, s -> {});
        battle.onRemote(MatchPayloads.inputMessage("m", new PlayerCommand(PlayerId.TWO, GameAction.PAUSE, true, 5)));
        assertEquals(MatchPhase.PAUSED, battle.snapshot().phase());
        assertEquals(0, battle.rejectedInputs());

        battle.onRemote(MatchPayloads.inputMessage("m", new PlayerCommand(PlayerId.TWO, GameAction.PAUSE, true, 5)));
        assertEquals(MatchPhase.PAUSED, battle.snapshot().phase());
        assertEquals(1, battle.rejectedInputs());
    }

    @Test
    void earlyPhaseChangeStateDoesNotDelayTheNextPeriodicState() throws Exception {
        List<Message> sent = new ArrayList<>();
        AtomicLong clock = new AtomicLong();
        ManualPulse manual = new ManualPulse();
        var battle = new HostBattleClient("m", MatchConfig.of(BattleMode.CLASSIC),
                new LocalMatchEngine(clock::get, id -> OBlock::new), clock::get, manual,
                m -> { sent.add(m); return true; }, s -> {});
        assertEquals(1, sent.size(), "t=0 첫 STATE");

        clock.set(5);
        battle.send(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, true, 0));
        assertEquals(2, sent.size(), "t=5 PAUSE 즉시 STATE");
        assertEquals(MatchPhase.PAUSED, MatchPayloads.readState(sent.getLast()).phase());

        clock.set(6);
        battle.send(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, false, 1));
        battle.send(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, true, 2));
        assertEquals(3, sent.size(), "t=6 RESUME 즉시 STATE");
        assertEquals(MatchPhase.PLAYING, MatchPayloads.readState(sent.getLast()).phase());

        clock.set(33);
        manual.tick.run();
        assertEquals(4, sent.size(), "t=33 주기 STATE가 밀리지 않고 전송된다");
    }

    @Test
    void stateIsCappedNearThirtyHertzAndResultFollowsTheFinalState() throws Exception {
        List<Message> sent = new ArrayList<>();
        AtomicLong clock = new AtomicLong();
        ManualPulse manual = new ManualPulse();
        List<MatchSnapshot> finished = new ArrayList<>();
        var battle = new HostBattleClient("m", MatchConfig.of(BattleMode.CLASSIC),
                new LocalMatchEngine(clock::get, id -> OBlock::new), clock::get, manual, m -> { sent.add(m); return true; },
                finished::add);
        for (int t = 16; t <= 1000; t += 16) {
            clock.set(t);
            manual.tick.run();
        }
        long states = sent.stream().filter(m -> m.type() == MessageType.STATE).count();
        assertTrue(states >= 25 && states <= 32, "about 30 STATE per second, was " + states);
        int before = sent.size();
        battle.send(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, true, 0));
        assertEquals(before + 1, sent.size());
        assertEquals(MessageType.STATE, sent.getLast().type(), "a phase change is sent at once");
        assertEquals(MatchPhase.PAUSED, MatchPayloads.readState(sent.getLast()).phase());
        battle.send(new PlayerCommand(PlayerId.ONE, GameAction.QUIT, true, 1));
        assertEquals(MessageType.RESULT, sent.getLast().type());
        assertEquals(MessageType.STATE, sent.get(sent.size() - 2).type(), "final STATE precedes RESULT");
        assertEquals(1, sent.stream().filter(m -> m.type() == MessageType.RESULT).count());
        assertEquals(1, finished.size());
        assertTrue(manual.stopped);
        manual.tick.run();
        assertEquals(1, sent.stream().filter(m -> m.type() == MessageType.RESULT).count(), "result sent exactly once");
    }

    // ── real pulse latency observation (S9-C2) ─────────────

    @Test
    void firstInputReachesBothScreensWellUnderTwoHundredMillisOnLoopback() throws Exception {
        host.close();
        host = new SocketLobbyClient(hostExec, new PeerEstablisher(hostExec),
                () -> new LocalMatchEngine(MatchClock.system(), id -> OBlock::new), MatchClock.system(),
                new DispatcherPulse(hostExec));
        var hs = lobbyQueue(hostExec, host);
        var cs = lobbyQueue(clientExec, client);
        startMatch(hs, cs, MatchConfig.of(BattleMode.CLASSIC));
        var hostBattle = battleQueue(hostExec, host);
        var clientBattle = battleQueue(clientExec, client);
        await(clientBattle, s -> s.phase() == MatchPhase.PLAYING);
        long sent = System.nanoTime();
        run(clientExec, () -> client.battleClient().orElseThrow()
                .send(new PlayerCommand(PlayerId.ONE, GameAction.MOVE_LEFT, true, 0)));
        await(hostBattle, s -> firstColumn(s.two().cells()) == 3);
        long hostMillis = (System.nanoTime() - sent) / 1_000_000;
        await(clientBattle, s -> firstColumn(s.two().cells()) == 3);
        long clientMillis = (System.nanoTime() - sent) / 1_000_000;
        System.out.println("[S9-C2 loopback] input->host screen " + hostMillis + " ms, input->client screen "
                + clientMillis + " ms");
        assertTrue(clientMillis < 200, "loopback round trip must stay under the 200 ms budget, was " + clientMillis);
    }

    static int firstColumn(int[][] cells) {
        for (int[] row : cells) for (int c = 0; c < row.length; c++) if (row[c] != 0) return c;
        return -1;
    }
}
