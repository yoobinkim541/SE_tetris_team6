package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
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
import tetris.feature.network.HeartbeatPolicy;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.lobby.LinkStats;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.lobby.SocketLobbyClient;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.LobbyPayloads;
import tetris.feature.network.protocol.MatchPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.VersusRules;

/**
 * 개발자 모드 연결 통계 (LinkStats): PING/PONG과 수신 프레임에서 RTT·수신 간격·수신 빈도를 모은다.
 * 가짜 시계와 수동 pulse로 시간 순서를 결정적으로 만든다.
 */
@Timeout(30)
class LinkStatsTest {
    private static final long WAIT_SECONDS = 5;

    private final AtomicLong now = new AtomicLong(1_000L);
    private final LinkHealthLobbyTest.ManualPulse pulse = new LinkHealthLobbyTest.ManualPulse();
    private ExecutorService hostExec;
    private SocketLobbyClient host;
    private final List<AutoCloseable> rawResources = new ArrayList<>();

    @BeforeEach
    void setUp() {
        hostExec = Executors.newSingleThreadExecutor();
        host = new SocketLobbyClient(
                hostExec,
                new PeerEstablisher(hostExec),
                () -> new LocalMatchEngine(now::get, id -> OBlock::new),
                now::get,
                pulse,
                HeartbeatPolicy.DEFAULT);
    }

    @AfterEach
    void tearDown() throws Exception {
        runOn(hostExec, host::close);
        for (AutoCloseable r : rawResources) {
            r.close();
        }
        hostExec.shutdownNow();
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

    private BlockingQueue<LobbySnapshot> subscribeSnapshots() throws Exception {
        BlockingQueue<LobbySnapshot> q = new LinkedBlockingQueue<>();
        callOn(hostExec, () -> host.subscribe(q::add));
        return q;
    }

    private static <T> T awaitQueue(BlockingQueue<T> q, Predicate<T> predicate) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (true) {
            long left = deadline - System.nanoTime();
            assertTrue(left > 0, "기대한 상태가 제한 시간 안에 오지 않았다");
            T item = q.poll(left, TimeUnit.NANOSECONDS);
            assertNotNull(item, "기대한 상태가 제한 시간 안에 오지 않았다");
            if (predicate.test(item)) return item;
        }
    }

    private Optional<LinkStats> stats() throws Exception {
        return callOn(hostExec, host::linkStats);
    }

    /** 통계가 기대한 조건을 만족할 때까지 기다린다 (수신 스레드 처리 시점을 시간으로 맞추지 않는다) */
    private LinkStats awaitStats(Predicate<LinkStats> predicate) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (true) {
            Optional<LinkStats> current = stats();
            if (current.isPresent() && predicate.test(current.get())) return current.get();
            assertTrue(System.nanoTime() < deadline, "기대한 통계가 제한 시간 안에 오지 않았다: " + current);
            Thread.onSpinWait();
        }
    }

    private LinkHealthLobbyTest.RawPeer connectRawPeer(BlockingQueue<LobbySnapshot> hs) throws Exception {
        Socket socket = new Socket();
        CompletionStage<Void> stage = callOn(hostExec, () -> host.host(0));
        int port = callOn(hostExec, host::localPort);
        socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        LinkHealthLobbyTest.RawPeer peer = trackRaw(new LinkHealthLobbyTest.RawPeer(socket));
        assertEquals(MessageType.HELLO, peer.next().type());
        peer.send(ControlPayloads.Hello.message(PeerRole.CLIENT));
        stage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        awaitQueue(hs, s -> s.state() == State.LOBBY);
        return peer;
    }

    /** PING 하나를 보내고 정해진 RTT 뒤 PONG을 돌려준다. 반영은 호출자가 awaitStats로 확인한다 */
    private void roundTrip(LinkHealthLobbyTest.RawPeer peer, long rtt) throws Exception {
        runOn(hostExec, () -> pulse.currentTick().run());
        ControlPayloads.Ping ping = ControlPayloads.Ping.from(peer.next(MessageType.PING).payload());
        now.addAndGet(rtt);
        peer.send(ping.pong());
    }

    /** 보드를 채우지 않은 유효한 STATE 메시지. 호스트는 클라이언트 경기가 없으므로 내용을 쓰지 않고 프레임만 센다 */
    private static Message stateMessage(long revision) {
        int[][] empty = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        var view = new MatchSnapshot.PlayerView(empty, new int[][] {{1}}, 0, List.of());
        return MatchPayloads.stateMessage(
                new MatchSnapshot("stats-match", revision, view, view, -1, MatchPhase.COUNTDOWN, null));
    }

    @Test
    void 연결_전에는_통계가_없다() throws Exception {
        assertTrue(stats().isEmpty(), "INITIAL에는 연결이 없다");

        callOn(hostExec, () -> host.host(0));
        assertTrue(stats().isEmpty(), "호스트 대기 중에는 아직 연결이 없다");
    }

    @Test
    void PONG_전에는_RTT가_모두_마이너스_1이다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        LinkHealthLobbyTest.RawPeer peer = connectRawPeer(hs);
        runOn(hostExec, () -> pulse.currentTick().run());
        peer.next(MessageType.PING);

        LinkStats stats = awaitStats(s -> s.pingsSent() >= 1);
        assertEquals(Role.HOST, stats.role());
        assertFalse(stats.peerAddress().isBlank(), "상대 주소를 보여 줘야 한다");
        assertEquals(0, stats.pongsReceived());
        assertEquals(-1L, stats.lastRttMillis());
        assertEquals(-1L, stats.minRttMillis());
        assertEquals(-1L, stats.avgRttMillis());
        assertEquals(-1L, stats.maxRttMillis());
        assertEquals(0, stats.rttSamples());
    }

    @Test
    void PONG_RTT로_최근_최소_평균_최대를_계산한다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        LinkHealthLobbyTest.RawPeer peer = connectRawPeer(hs);

        long[] rtts = {10L, 20L, 60L};
        for (long rtt : rtts) {
            roundTrip(peer, rtt);
            long expected = rtt;
            awaitStats(s -> s.lastRttMillis() == expected && s.rttSamples() >= 1);
        }

        LinkStats stats = awaitStats(s -> s.rttSamples() == 3);
        assertEquals(60L, stats.lastRttMillis());
        assertEquals(10L, stats.minRttMillis());
        assertEquals(30L, stats.avgRttMillis(), "정수 나눗셈 평균");
        assertEquals(60L, stats.maxRttMillis());
        assertEquals(3L, stats.pingsSent());
        assertEquals(3L, stats.pongsReceived());
    }

    @Test
    void RTT는_최근_20개만_유지한다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        LinkHealthLobbyTest.RawPeer peer = connectRawPeer(hs);

        for (int i = 1; i <= 25; i++) {
            roundTrip(peer, i);
            long expected = i;
            awaitStats(s -> s.pongsReceived() == expected);
        }

        LinkStats stats = awaitStats(s -> s.rttSamples() == 20);
        assertEquals(25L, stats.lastRttMillis());
        assertEquals(6L, stats.minRttMillis(), "가장 오래된 5개(RTT 1~5)는 버린다");
        assertEquals(25L, stats.maxRttMillis());
        assertEquals(15L, stats.avgRttMillis(), "(6+…+25)/20 = 310/20");
        assertEquals(25L, stats.pongsReceived());
    }

    @Test
    void 일초_창의_수신_프레임과_STATE를_센다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        LinkHealthLobbyTest.RawPeer peer = connectRawPeer(hs);
        long t0 = now.get();

        for (int i = 0; i < 3; i++) {
            peer.send(new ControlPayloads.Ping(900L + i, 0L).pong()); // 모르는 nonce: RTT에는 안 쓰인다
        }
        awaitStats(s -> s.framesLastSecond() == 3);

        now.set(t0 + 900L);
        peer.send(stateMessage(1), stateMessage(2));
        awaitStats(s -> s.statesLastSecond() == 2 && s.framesLastSecond() == 5);

        now.set(t0 + 1_500L);
        LinkStats stats = awaitStats(s -> s.framesLastSecond() == 2);
        assertEquals(2, stats.statesLastSecond(), "1초 창 안의 STATE만 센다");
        assertEquals(0, stats.pongsReceived(), "모르는 nonce의 PONG은 RTT에 쓰지 않는다");
    }

    @Test
    void 마지막_수신_후_경과_시간() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        LinkHealthLobbyTest.RawPeer peer = connectRawPeer(hs);

        now.set(1_100L);
        peer.send(new ControlPayloads.Ping(777L, 0L).pong());
        awaitStats(s -> s.framesLastSecond() >= 1);

        now.set(1_450L);
        assertEquals(350L, awaitStats(s -> s.millisSinceLastFrame() == 350L).millisSinceLastFrame());
    }

    @Test
    void 재접속하면_통계가_초기화된다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        LinkHealthLobbyTest.RawPeer first = connectRawPeer(hs);
        roundTrip(first, 40L);
        awaitStats(s -> s.rttSamples() == 1);

        runOn(hostExec, host::leave);
        awaitQueue(hs, s -> s.state() == State.INITIAL);
        assertTrue(stats().isEmpty(), "나간 뒤에는 연결이 없다");

        connectRawPeer(hs);
        LinkStats fresh = awaitStats(s -> true);
        assertEquals(0L, fresh.pingsSent());
        assertEquals(0L, fresh.pongsReceived());
        assertEquals(0, fresh.rttSamples());
        assertEquals(-1L, fresh.lastRttMillis());
        assertEquals(0, fresh.framesLastSecond());
        assertEquals(0, fresh.statesLastSecond());
        assertEquals(0L, fresh.millisSinceLastFrame());
    }
}
