package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.component.block.OBlock;
import tetris.feature.network.HeartbeatPolicy;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.lobby.LinkHealth;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.lobby.SocketLobbyClient;
import tetris.feature.network.match.MatchPulse;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.FrameDecoder;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.LobbyPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;

/** S10-C1: SocketLobbyClient 위의 heartbeat·지연·회복·끊김·종료 상태 검증 (R3-LAG, R3-DISCONNECT) */
@Timeout(30)
class LinkHealthLobbyTest {
    private static final long WAIT_SECONDS = 5;
    private static final MatchConfig CLASSIC = MatchConfig.of(BattleMode.CLASSIC);

    static final class ManualPulse implements MatchPulse {
        final List<Runnable> ticks = new ArrayList<>();
        final List<Boolean> closed = new ArrayList<>();

        @Override
        public Handle start(int periodMillis, Runnable tick) {
            int idx = ticks.size();
            ticks.add(tick);
            closed.add(false);
            return () -> closed.set(idx, true);
        }

        Runnable currentTick() {
            assertTrue(!ticks.isEmpty(), "heartbeat pulse가 시작되지 않았다");
            return ticks.getLast();
        }

        boolean isLastClosed() {
            assertTrue(!closed.isEmpty(), "heartbeat pulse가 시작되지 않았다");
            return closed.getLast();
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
    private final ManualPulse pulse = new ManualPulse();
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

    private BlockingQueue<LinkHealth> subscribeHealth() throws Exception {
        BlockingQueue<LinkHealth> q = new LinkedBlockingQueue<>();
        callOn(hostExec, () -> host.subscribeHealth(q::add));
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

    /** 호스트가 RTT가 rtt인 PONG을 처리할 때까지 기다린다 */
    private void awaitRtt(long rtt) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (callOn(hostExec, host::health).rttMillis() != rtt) {
            assertTrue(System.nanoTime() < deadline, "PONG RTT " + rtt + "ms가 반영되지 않았다");
            Thread.onSpinWait();
        }
    }

    private RawPeer connectRawPeer(BlockingQueue<LobbySnapshot> hs) throws Exception {
        Socket socket = new Socket();
        socket.setReceiveBufferSize(1024);
        CompletionStage<Void> stage = callOn(hostExec, () -> host.host(0));
        int port = callOn(hostExec, host::localPort);
        socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        RawPeer peer = trackRaw(new RawPeer(socket));
        assertEquals(MessageType.HELLO, peer.next().type());
        peer.send(ControlPayloads.Hello.message(PeerRole.CLIENT));
        stage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        awaitQueue(hs, s -> s.state() == State.LOBBY);
        return peer;
    }

    @Test
    void 연결되면_NORMAL을_알리고_heartbeat가_PING을_보낸다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        BlockingQueue<LinkHealth> hq = subscribeHealth();
        assertEquals(LinkHealth.NONE, hq.poll(WAIT_SECONDS, TimeUnit.SECONDS));

        RawPeer peer = connectRawPeer(hs);
        LinkHealth normal = awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);
        assertEquals(-1L, normal.rttMillis());

        runOn(hostExec, () -> pulse.currentTick().run());
        Message pingMsg = peer.next();
        assertNotNull(pingMsg);
        assertEquals(MessageType.PING, pingMsg.type());
        ControlPayloads.Ping ping = ControlPayloads.Ping.from(pingMsg.payload());
        assertEquals(1L, ping.nonce());
    }

    @Test
    void PONG_RTT가_3회_200ms를_넘으면_LAGGING_150ms이하_3회면_NORMAL() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        BlockingQueue<LinkHealth> hq = subscribeHealth();
        RawPeer peer = connectRawPeer(hs);
        awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);

        // PONG마다 RTT를 1ms씩 달리 해서, 다음 PING을 보내기 전에 호스트가 이 PONG을 반영했는지 확인한다.
        // 확인 없이 다음 tick을 돌리면 늦게 처리된 PONG의 RTT가 다음 시계 증가분만큼 커져 판정이 흔들린다
        for (int i = 0; i < 3; i++) {
            long rtt = 250L + i;
            runOn(hostExec, () -> pulse.currentTick().run());
            ControlPayloads.Ping ping = ControlPayloads.Ping.from(peer.next(MessageType.PING).payload());
            now.addAndGet(rtt);
            peer.send(ping.pong());
            awaitRtt(rtt);
        }
        LinkHealth lagging = awaitQueue(hq, h -> h.status() == LinkHealth.Status.LAGGING);
        assertEquals(252L, lagging.rttMillis());

        for (int i = 0; i < 3; i++) {
            long rtt = 120L + i;
            runOn(hostExec, () -> pulse.currentTick().run());
            ControlPayloads.Ping ping = ControlPayloads.Ping.from(peer.next(MessageType.PING).payload());
            now.addAndGet(rtt);
            peer.send(ping.pong());
            awaitRtt(rtt);
        }
        LinkHealth recovered = awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);
        assertEquals(122L, recovered.rttMillis());
    }

    @Test
    void 부분_프레임만_오면_5초_뒤_LOST로_끊기고_INITIAL이다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        BlockingQueue<LinkHealth> hq = subscribeHealth();
        RawPeer peer = connectRawPeer(hs);
        awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);

        now.addAndGet(2_000L);
        peer.socket.getOutputStream().write(new byte[] {0, 0});
        peer.socket.getOutputStream().flush();

        now.addAndGet(3_000L);
        runOn(hostExec, () -> pulse.currentTick().run());

        awaitQueue(hq, h -> h.status() == LinkHealth.Status.LOST);
        awaitQueue(hs, s -> s.state() == State.INITIAL);
        assertTrue(pulse.isLastClosed());
    }

    @Test
    void 모르는_nonce의_PONG은_RTT에_쓰지_않는다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        BlockingQueue<LinkHealth> hq = subscribeHealth();
        RawPeer peer = connectRawPeer(hs);
        awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);

        runOn(hostExec, () -> host.selectConfig(CLASSIC));
        awaitQueue(hs, s -> s.configRevision() == 1);
        peer.next(MessageType.CONFIG);

        now.addAndGet(300L);
        peer.send(
                new ControlPayloads.Ping(999L, 12345L).pong(),
                LobbyPayloads.readyMessage(1, true));
        awaitQueue(hs, LobbySnapshot::peerReady);

        LinkHealth current = callOn(hostExec, host::health);
        assertEquals(LinkHealth.Status.NORMAL, current.status());
        assertEquals(-1L, current.rttMillis());
    }

    @Test
    void 상대가_LEAVE하면_PEER_LEFT를_한_번_알린다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        BlockingQueue<LinkHealth> hq = subscribeHealth();
        RawPeer peer = connectRawPeer(hs);
        awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);

        peer.send(new ControlPayloads.Leave("user left").message());
        peer.close();

        awaitQueue(hq, h -> h.status() == LinkHealth.Status.PEER_LEFT);
        awaitQueue(hs, s -> s.state() == State.INITIAL);
        runOn(hostExec, () -> {});
        assertTrue(hq.isEmpty(), "PEER_LEFT는 한 번만 발행되어야 한다");
        assertTrue(pulse.isLastClosed());
    }

    @Test
    void 상대가_소켓을_닫으면_LOST를_한_번_알린다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        BlockingQueue<LinkHealth> hq = subscribeHealth();
        RawPeer peer = connectRawPeer(hs);
        awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);

        peer.close();

        awaitQueue(hq, h -> h.status() == LinkHealth.Status.LOST);
        awaitQueue(hs, s -> s.state() == State.INITIAL);
        runOn(hostExec, () -> {});
        assertTrue(hq.isEmpty(), "LOST는 한 번만 발행되어야 한다");
        assertTrue(pulse.isLastClosed());
    }

    @Test
    void 상대가_읽지_않아_송신_큐가_넘치면_LOST를_한_번_알린다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        BlockingQueue<LinkHealth> hq = subscribeHealth();
        connectRawPeer(hs);
        awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (callOn(hostExec, () -> host.health().status()) != LinkHealth.Status.LOST
                && System.nanoTime() < deadline) {
            runOn(hostExec, () -> {
                for (int i = 0; i < 64; i++) {
                    if (host.health().status() == LinkHealth.Status.LOST) break;
                    try {
                        host.selectConfig(CLASSIC);
                    } catch (IllegalStateException e) {
                        break;
                    }
                }
            });
        }

        awaitQueue(hq, h -> h.status() == LinkHealth.Status.LOST);
        awaitQueue(hs, s -> s.state() == State.INITIAL);
        runOn(hostExec, () -> {});
        assertTrue(hq.isEmpty(), "LOST는 한 번만 발행되어야 한다");
        assertTrue(pulse.isLastClosed());
    }

    @Test
    void 이쪽이_leave하면_발행_없이_NONE이고_heartbeat가_멈춘다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        BlockingQueue<LinkHealth> hq = subscribeHealth();
        connectRawPeer(hs);
        awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);
        hq.clear();

        runOn(hostExec, host::leave);
        awaitQueue(hs, s -> s.state() == State.INITIAL);

        assertEquals(LinkHealth.NONE, callOn(hostExec, host::health));
        assertTrue(hq.isEmpty(), "이쪽 leave는 health 이벤트를 발행하지 않는다");
        assertTrue(pulse.isLastClosed());
    }

    @Test
    void 이쪽이_leave하면_상대에게_LEAVE를_보내고_닫는다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        RawPeer peer = connectRawPeer(hs);
        runOn(hostExec, host::leave);
        Message leave = peer.next(MessageType.LEAVE);
        assertNotNull(leave, "상대가 PEER_LEFT로 안내할 수 있도록 LEAVE가 먼저 와야 한다");
        assertEquals("user", ControlPayloads.Leave.from(leave.payload()).reason());
        Assertions.assertNull(peer.next(), "LEAVE 뒤에는 연결이 닫힌다");
    }

    @Test
    void 연결된_뒤_cancel은_heartbeat를_멈추지_않는다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        BlockingQueue<LinkHealth> hq = subscribeHealth();
        RawPeer peer = connectRawPeer(hs);
        awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);
        runOn(hostExec, host::cancel);
        assertEquals(LinkHealth.Status.NORMAL, callOn(hostExec, host::health).status());
        Assertions.assertFalse(pulse.isLastClosed(), "연결 수립 중이 아니면 cancel은 아무것도 하지 않는다");
        runOn(hostExec, () -> pulse.currentTick().run());
        assertEquals(MessageType.PING, peer.next(MessageType.PING).type());
    }

    @Test
    void 재접속_뒤_이전_연결의_heartbeat는_새_연결에_영향이_없다() throws Exception {
        BlockingQueue<LobbySnapshot> hs = subscribeSnapshots();
        BlockingQueue<LinkHealth> hq = subscribeHealth();
        connectRawPeer(hs);
        awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);
        Runnable firstTick = pulse.currentTick();

        runOn(hostExec, host::leave);
        awaitQueue(hs, s -> s.state() == State.INITIAL);

        connectRawPeer(hs);
        awaitQueue(hq, h -> h.status() == LinkHealth.Status.NORMAL);
        hq.clear();

        now.addAndGet(10_000L);
        runOn(hostExec, firstTick);

        assertEquals(LinkHealth.Status.NORMAL, callOn(hostExec, host::health).status());
        assertTrue(hq.isEmpty(), "이전 연결의 heartbeat는 새 연결을 바꾸지 않는다");
    }
}
