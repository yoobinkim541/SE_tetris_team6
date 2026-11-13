package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.EstablishException;
import tetris.feature.network.EstablishFailure;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.lobby.SocketLobbyClient;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.FrameDecoder;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.LobbyPayloads;
import tetris.feature.network.protocol.MatchPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;

/** S8-C2: 실제 loopback TCP 소켓 위의 SocketLobbyClient 통합 검증 (R3-READY, R3-MODES, R3-NETTEST) */
@Timeout(30)
class SocketLobbyClientTest {
    private static final long WAIT_SECONDS = 5;
    private static final MatchConfig CLASSIC = MatchConfig.of(BattleMode.CLASSIC);
    private static final MatchConfig TIMED = MatchConfig.of(BattleMode.TIMED);
    private static final MatchConfig ITEM = MatchConfig.of(BattleMode.ITEM);

    private ExecutorService hostExec;
    private ExecutorService clientExec;
    private SocketLobbyClient hostLobby;
    private SocketLobbyClient clientLobby;
    private final List<AutoCloseable> rawResources = new ArrayList<>();

    @BeforeEach
    void setUp() {
        hostExec = Executors.newSingleThreadExecutor();
        clientExec = Executors.newSingleThreadExecutor();
        hostLobby = new SocketLobbyClient(hostExec);
        clientLobby = new SocketLobbyClient(clientExec);
    }

    @AfterEach
    void tearDown() throws Exception {
        runOn(hostExec, hostLobby::close);
        runOn(clientExec, clientLobby::close);
        for (AutoCloseable resource : rawResources) {
            resource.close();
        }
        hostExec.shutdownNow();
        clientExec.shutdownNow();
    }

    private <T extends AutoCloseable> T trackRaw(T resource) {
        rawResources.add(resource);
        return resource;
    }

    private static void runOn(ExecutorService exec, Runnable action) throws Exception {
        try {
            exec.submit(action).get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            if (e.getCause() instanceof Error err) throw err;
            throw e;
        }
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

    private static BlockingQueue<LobbySnapshot> subscribeQueue(ExecutorService exec, SocketLobbyClient lobby)
            throws Exception {
        BlockingQueue<LobbySnapshot> queue = new LinkedBlockingQueue<>();
        callOn(exec, () -> lobby.subscribe(queue::add));
        return queue;
    }

    private static LobbySnapshot awaitSnapshot(
            BlockingQueue<LobbySnapshot> queue, Predicate<LobbySnapshot> predicate) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (true) {
            long remaining = deadline - System.nanoTime();
            assertTrue(remaining > 0, "기대한 LobbySnapshot 상태가 제한 시간 안에 오지 않았다");
            LobbySnapshot snap = queue.poll(remaining, TimeUnit.NANOSECONDS);
            assertNotNull(snap, "기대한 LobbySnapshot 상태가 제한 시간 안에 오지 않았다");
            if (predicate.test(snap)) {
                return snap;
            }
        }
    }

    private void enterLobby(
            BlockingQueue<LobbySnapshot> hostSnaps,
            BlockingQueue<LobbySnapshot> clientSnaps) throws Exception {
        CompletionStage<Void> hostStage = callOn(hostExec, () -> hostLobby.host(0));
        int port = callOn(hostExec, hostLobby::localPort);
        assertTrue(port > 0);
        CompletionStage<Void> clientStage = callOn(clientExec, () -> clientLobby.connect("127.0.0.1", port));

        hostStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        clientStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);

        awaitSnapshot(hostSnaps, s -> s.state() == State.LOBBY);
        awaitSnapshot(clientSnaps, s -> s.state() == State.LOBBY);
    }

    private static Message readFrame(InputStream in, FrameDecoder decoder) throws IOException, ProtocolException {
        byte[] buf = new byte[512];
        int n;
        while ((n = in.read(buf)) != -1) {
            List<Message> msgs = decoder.feed(buf, 0, n);
            if (!msgs.isEmpty()) {
                return msgs.getFirst();
            }
        }
        return null;
    }

    @Test
    void 호스트와_클라이언트가_LOBBY에_들어가고_stage가_완료된다() throws Exception {
        BlockingQueue<LobbySnapshot> hostSnaps = subscribeQueue(hostExec, hostLobby);
        BlockingQueue<LobbySnapshot> clientSnaps = subscribeQueue(clientExec, clientLobby);

        assertEquals(State.INITIAL, hostSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS).state());
        assertEquals(State.INITIAL, clientSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS).state());

        CompletionStage<Void> hostStage = callOn(hostExec, () -> hostLobby.host(0));
        assertEquals(State.HOSTING, hostSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS).state());
        int port = callOn(hostExec, hostLobby::localPort);

        CompletionStage<Void> clientStage = callOn(clientExec, () -> clientLobby.connect("127.0.0.1", port));
        assertEquals(State.CONNECTING, clientSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS).state());

        hostStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        clientStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);

        LobbySnapshot hostInLobby = hostSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        LobbySnapshot clientInLobby = clientSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(Role.HOST, hostInLobby.role());
        assertEquals(State.LOBBY, hostInLobby.state());
        assertNull(hostInLobby.config());
        assertEquals(0L, hostInLobby.configRevision());
        assertFalse(hostInLobby.localReady());
        assertFalse(hostInLobby.peerReady());
        assertTrue(hostInLobby.peerIdentity().startsWith("127.0.0.1#"));

        assertEquals(Role.CLIENT, clientInLobby.role());
        assertEquals(State.LOBBY, clientInLobby.state());
        assertTrue(clientInLobby.peerIdentity().startsWith("127.0.0.1#"));
        assertEquals(Optional.empty(), callOn(hostExec, hostLobby::battleClient));
        assertEquals(Optional.empty(), callOn(hostExec, hostLobby::matchId));
    }

    @Test
    void 호스트_설정이_클라이언트에_전달되고_양쪽_준비가_풀린다() throws Exception {
        BlockingQueue<LobbySnapshot> hostSnaps = subscribeQueue(hostExec, hostLobby);
        BlockingQueue<LobbySnapshot> clientSnaps = subscribeQueue(clientExec, clientLobby);
        enterLobby(hostSnaps, clientSnaps);

        // 1) 호스트가 설정(rev=1) 후 호스트 준비(localReady=true, 클라 peerReady=true) 상태에서 설정 재선택 -> 양쪽 준비 해제(rev=2)
        runOn(hostExec, () -> hostLobby.selectConfig(CLASSIC));
        awaitSnapshot(hostSnaps, s -> s.configRevision() == 1 && CLASSIC.equals(s.config()));
        awaitSnapshot(clientSnaps, s -> s.configRevision() == 1 && CLASSIC.equals(s.config()));

        runOn(hostExec, () -> hostLobby.ready(1, true));
        awaitSnapshot(hostSnaps, s -> s.configRevision() == 1 && s.localReady());
        awaitSnapshot(clientSnaps, s -> s.configRevision() == 1 && s.peerReady());

        runOn(hostExec, () -> hostLobby.selectConfig(TIMED));
        LobbySnapshot hostRev2 = awaitSnapshot(hostSnaps, s -> s.configRevision() == 2);
        LobbySnapshot clientRev2 = awaitSnapshot(clientSnaps, s -> s.configRevision() == 2);
        assertEquals(TIMED, hostRev2.config());
        assertFalse(hostRev2.localReady());
        assertFalse(hostRev2.peerReady());
        assertEquals(TIMED, clientRev2.config());
        assertFalse(clientRev2.localReady());
        assertFalse(clientRev2.peerReady());

        // 2) 반대로 클라이언트 준비(클라 localReady=true, 호스트 peerReady=true) 상태에서 호스트 설정 변경 -> 양쪽 준비 해제(rev=3)
        runOn(clientExec, () -> clientLobby.ready(2, true));
        awaitSnapshot(clientSnaps, s -> s.configRevision() == 2 && s.localReady());
        awaitSnapshot(hostSnaps, s -> s.configRevision() == 2 && s.peerReady());

        runOn(hostExec, () -> hostLobby.selectConfig(ITEM));
        LobbySnapshot hostRev3 = awaitSnapshot(hostSnaps, s -> s.configRevision() == 3);
        LobbySnapshot clientRev3 = awaitSnapshot(clientSnaps, s -> s.configRevision() == 3);
        assertEquals(ITEM, hostRev3.config());
        assertFalse(hostRev3.localReady());
        assertFalse(hostRev3.peerReady());
        assertEquals(ITEM, clientRev3.config());
        assertFalse(clientRev3.localReady());
        assertFalse(clientRev3.peerReady());
    }

    @Test
    void 클라이언트는_설정을_바꿀_수_없고_호스트는_클라이언트가_보낸_CONFIG를_무시한다() throws Exception {
        BlockingQueue<LobbySnapshot> hostSnaps = subscribeQueue(hostExec, hostLobby);
        BlockingQueue<LobbySnapshot> clientSnaps = subscribeQueue(clientExec, clientLobby);
        enterLobby(hostSnaps, clientSnaps);

        runOn(hostExec, () -> hostLobby.selectConfig(CLASSIC));
        awaitSnapshot(clientSnaps, s -> s.configRevision() == 1);

        assertThrows(IllegalStateException.class, () -> runOn(clientExec, () -> clientLobby.selectConfig(ITEM)));

        // 원시 소켓 클라이언트가 강제로 CONFIG 프레임을 보내도 호스트는 무시한다
        runOn(hostExec, hostLobby::leave);
        awaitSnapshot(hostSnaps, s -> s.state() == State.INITIAL);

        AtomicReference<CountDownLatch> nextInboundDone = new AtomicReference<>(new CountDownLatch(0));
        Executor trackedHostExec = task -> hostExec.execute(() -> {
            task.run();
            nextInboundDone.get().countDown();
        });
        SocketLobbyClient rawTestHost = new SocketLobbyClient(trackedHostExec, new PeerEstablisher(trackedHostExec));
        trackRaw(() -> runOn(hostExec, rawTestHost::close));
        BlockingQueue<LobbySnapshot> rawHostSnaps = subscribeQueue(hostExec, rawTestHost);

        CompletionStage<Void> stage = callOn(hostExec, () -> rawTestHost.host(0));
        int port = callOn(hostExec, rawTestHost::localPort);
        Socket rawClient = trackRaw(new Socket(InetAddress.getLoopbackAddress(), port));
        FrameEncoder encoder = new FrameEncoder();
        FrameDecoder decoder = new FrameDecoder();

        Message hostHello = readFrame(rawClient.getInputStream(), decoder);
        assertEquals(MessageType.HELLO, hostHello.type());

        rawClient.getOutputStream().write(encoder.encode(ControlPayloads.Hello.message(PeerRole.CLIENT)));
        rawClient.getOutputStream().flush();
        stage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        awaitSnapshot(rawHostSnaps, s -> s.state() == State.LOBBY);

        runOn(hostExec, () -> rawTestHost.selectConfig(CLASSIC));
        Message hostConfig = readFrame(rawClient.getInputStream(), decoder);
        assertEquals(MessageType.CONFIG, hostConfig.type());
        assertEquals(1, hostConfig.configRevision());
        awaitSnapshot(rawHostSnaps, s -> s.configRevision() == 1);

        CountDownLatch rogueProcessed = new CountDownLatch(1);
        nextInboundDone.set(rogueProcessed);
        rawClient.getOutputStream().write(encoder.encode(MatchPayloads.configMessage(ITEM, 2)));
        rawClient.getOutputStream().flush();
        assertTrue(rogueProcessed.await(WAIT_SECONDS, TimeUnit.SECONDS));

        BlockingQueue<LobbySnapshot> currentCheck = subscribeQueue(hostExec, rawTestHost);
        LobbySnapshot current = currentCheck.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(CLASSIC, current.config());
        assertEquals(1L, current.configRevision());
        assertEquals(State.LOBBY, current.state());
    }

    @Test
    void 한쪽만_준비하면_시작하지_않는다() throws Exception {
        BlockingQueue<LobbySnapshot> hostSnaps = subscribeQueue(hostExec, hostLobby);
        BlockingQueue<LobbySnapshot> clientSnaps = subscribeQueue(clientExec, clientLobby);
        enterLobby(hostSnaps, clientSnaps);

        runOn(hostExec, () -> hostLobby.selectConfig(CLASSIC));
        awaitSnapshot(hostSnaps, s -> s.configRevision() == 1);
        awaitSnapshot(clientSnaps, s -> s.configRevision() == 1);

        runOn(hostExec, () -> hostLobby.ready(1, true));
        LobbySnapshot hostSnap1 = awaitSnapshot(hostSnaps, s -> s.localReady());
        LobbySnapshot clientSnap1 = awaitSnapshot(clientSnaps, s -> s.peerReady());
        assertEquals(State.LOBBY, hostSnap1.state());
        assertEquals(State.LOBBY, clientSnap1.state());
        assertEquals(Optional.empty(), callOn(hostExec, hostLobby::matchId));

        runOn(hostExec, () -> hostLobby.ready(1, false));
        awaitSnapshot(hostSnaps, s -> !s.localReady());
        awaitSnapshot(clientSnaps, s -> !s.peerReady());

        runOn(clientExec, () -> clientLobby.ready(1, true));
        LobbySnapshot clientSnap2 = awaitSnapshot(clientSnaps, s -> s.localReady());
        LobbySnapshot hostSnap2 = awaitSnapshot(hostSnaps, s -> s.peerReady());
        assertEquals(State.LOBBY, clientSnap2.state());
        assertEquals(State.LOBBY, hostSnap2.state());
        assertEquals(Optional.empty(), callOn(hostExec, hostLobby::matchId));
        assertEquals(Optional.empty(), callOn(clientExec, clientLobby::matchId));
    }

    @Test
    void 이전_revision의_READY는_무시한다() throws Exception {
        AtomicReference<CountDownLatch> inboundLatch = new AtomicReference<>(new CountDownLatch(0));
        Executor trackedHostExec = task -> hostExec.execute(() -> {
            task.run();
            inboundLatch.get().countDown();
        });
        SocketLobbyClient trackedHost = new SocketLobbyClient(trackedHostExec, new PeerEstablisher(trackedHostExec));
        trackRaw(() -> runOn(hostExec, trackedHost::close));
        BlockingQueue<LobbySnapshot> hostSnaps = subscribeQueue(hostExec, trackedHost);

        CompletionStage<Void> stage = callOn(hostExec, () -> trackedHost.host(0));
        int port = callOn(hostExec, trackedHost::localPort);
        Socket rawClient = trackRaw(new Socket(InetAddress.getLoopbackAddress(), port));
        FrameEncoder encoder = new FrameEncoder();
        FrameDecoder decoder = new FrameDecoder();

        assertEquals(MessageType.HELLO, readFrame(rawClient.getInputStream(), decoder).type());
        rawClient.getOutputStream().write(encoder.encode(ControlPayloads.Hello.message(PeerRole.CLIENT)));
        rawClient.getOutputStream().flush();
        stage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        awaitSnapshot(hostSnaps, s -> s.state() == State.LOBBY);

        runOn(hostExec, () -> trackedHost.selectConfig(CLASSIC));
        assertEquals(1, readFrame(rawClient.getInputStream(), decoder).configRevision());
        runOn(hostExec, () -> trackedHost.selectConfig(TIMED));
        assertEquals(2, readFrame(rawClient.getInputStream(), decoder).configRevision());
        runOn(hostExec, () -> trackedHost.ready(2, true));
        assertEquals(MessageType.READY, readFrame(rawClient.getInputStream(), decoder).type());
        awaitSnapshot(hostSnaps, s -> s.configRevision() == 2 && s.localReady());

        // 이전 revision(1)의 READY 전송 -> 무시되어야 하며 시작하지 않아야 한다
        CountDownLatch staleProcessed = new CountDownLatch(1);
        inboundLatch.set(staleProcessed);
        rawClient.getOutputStream().write(encoder.encode(LobbyPayloads.readyMessage(1, true)));
        rawClient.getOutputStream().flush();
        assertTrue(staleProcessed.await(WAIT_SECONDS, TimeUnit.SECONDS));

        BlockingQueue<LobbySnapshot> checkSnaps = subscribeQueue(hostExec, trackedHost);
        LobbySnapshot afterStale = checkSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(State.LOBBY, afterStale.state());
        assertFalse(afterStale.peerReady());
        assertEquals(Optional.empty(), callOn(hostExec, trackedHost::matchId));
    }

    @Test
    void 양쪽_준비되면_START가_한_번_오고_matchId가_같다() throws Exception {
        // 1) 두 SocketLobbyClient 간 양방향 STARTING 및 동일 matchId 확인
        BlockingQueue<LobbySnapshot> hostSnaps = subscribeQueue(hostExec, hostLobby);
        BlockingQueue<LobbySnapshot> clientSnaps = subscribeQueue(clientExec, clientLobby);
        enterLobby(hostSnaps, clientSnaps);

        runOn(hostExec, () -> hostLobby.selectConfig(CLASSIC));
        awaitSnapshot(hostSnaps, s -> s.configRevision() == 1);
        awaitSnapshot(clientSnaps, s -> s.configRevision() == 1);

        runOn(clientExec, () -> clientLobby.ready(1, true));
        runOn(hostExec, () -> hostLobby.ready(1, true));

        LobbySnapshot hostStarting = awaitSnapshot(hostSnaps, s -> s.state() == State.STARTING);
        LobbySnapshot clientStarting = awaitSnapshot(clientSnaps, s -> s.state() == State.STARTING);
        assertTrue(hostStarting.localReady() && hostStarting.peerReady());
        assertTrue(clientStarting.localReady() && clientStarting.peerReady());

        Optional<String> hostMatchId = callOn(hostExec, hostLobby::matchId);
        Optional<String> clientMatchId = callOn(clientExec, clientLobby::matchId);
        assertTrue(hostMatchId.isPresent());
        assertEquals(hostMatchId, clientMatchId);

        assertThrows(IllegalStateException.class, () -> runOn(hostExec, () -> hostLobby.selectConfig(TIMED)));
        assertThrows(IllegalStateException.class, () -> runOn(hostExec, () -> hostLobby.ready(1, false)));

        // 2) 원시 관찰 측에서 와이어 위 START 프레임이 정확히 1개만 오는지 확인
        runOn(hostExec, hostLobby::leave);
        awaitSnapshot(hostSnaps, s -> s.state() == State.INITIAL);

        CompletionStage<Void> hostStage2 = callOn(hostExec, () -> hostLobby.host(0));
        int port2 = callOn(hostExec, hostLobby::localPort);
        Socket observer = trackRaw(new Socket(InetAddress.getLoopbackAddress(), port2));
        FrameEncoder encoder = new FrameEncoder();
        FrameDecoder decoder = new FrameDecoder();

        assertEquals(MessageType.HELLO, readFrame(observer.getInputStream(), decoder).type());
        observer.getOutputStream().write(encoder.encode(ControlPayloads.Hello.message(PeerRole.CLIENT)));
        observer.getOutputStream().flush();
        hostStage2.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);

        runOn(hostExec, () -> hostLobby.selectConfig(TIMED));
        assertEquals(MessageType.CONFIG, readFrame(observer.getInputStream(), decoder).type());
        runOn(hostExec, () -> hostLobby.ready(1, true));
        assertEquals(MessageType.READY, readFrame(observer.getInputStream(), decoder).type());

        observer.getOutputStream().write(encoder.encode(LobbyPayloads.readyMessage(1, true)));
        // 중복 READY를 연달아 보내도 START는 한 번만 와야 한다
        observer.getOutputStream().write(encoder.encode(LobbyPayloads.readyMessage(1, true)));
        observer.getOutputStream().flush();

        Message startFrame = readFrame(observer.getInputStream(), decoder);
        assertEquals(MessageType.START, startFrame.type());
        assertEquals(1, startFrame.configRevision());
        assertEquals(callOn(hostExec, hostLobby::matchId).orElseThrow(), startFrame.matchId());

        runOn(hostExec, hostLobby::leave);
        Message last = readFrame(observer.getInputStream(), decoder);
        assertEquals(MessageType.LEAVE, last == null ? null : last.type(), "START 1개 뒤에는 나가기 LEAVE만 온다");
        assertNull(readFrame(observer.getInputStream(), decoder), "LEAVE 뒤에는 닫힌다");
    }

    @Test
    void 중복_START는_무시한다() throws Exception {
        AtomicReference<CountDownLatch> inboundLatch = new AtomicReference<>(new CountDownLatch(0));
        Executor trackedClientExec = task -> clientExec.execute(() -> {
            task.run();
            inboundLatch.get().countDown();
        });
        SocketLobbyClient trackedClient =
                new SocketLobbyClient(trackedClientExec, new PeerEstablisher(trackedClientExec));
        trackRaw(() -> runOn(clientExec, trackedClient::close));
        List<LobbySnapshot> observed = new ArrayList<>();
        callOn(clientExec, () -> trackedClient.subscribe(observed::add));

        ServerSocket rawServer = trackRaw(new ServerSocket(0, 1, InetAddress.getLoopbackAddress()));
        CompletableFuture<Socket> acceptedFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return rawServer.accept();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        CompletionStage<Void> connectStage =
                callOn(clientExec, () -> trackedClient.connect("127.0.0.1", rawServer.getLocalPort()));
        Socket rawHost = trackRaw(acceptedFuture.get(WAIT_SECONDS, TimeUnit.SECONDS));
        FrameEncoder encoder = new FrameEncoder();
        FrameDecoder decoder = new FrameDecoder();

        assertEquals(MessageType.HELLO, readFrame(rawHost.getInputStream(), decoder).type());
        rawHost.getOutputStream().write(encoder.encode(ControlPayloads.Hello.message(PeerRole.HOST)));
        rawHost.getOutputStream().flush();
        connectStage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);

        CountDownLatch configAndReady = new CountDownLatch(2);
        inboundLatch.set(configAndReady);
        rawHost.getOutputStream().write(encoder.encode(MatchPayloads.configMessage(CLASSIC, 1)));
        rawHost.getOutputStream().write(encoder.encode(LobbyPayloads.readyMessage(1, true)));
        rawHost.getOutputStream().flush();
        assertTrue(configAndReady.await(WAIT_SECONDS, TimeUnit.SECONDS));

        runOn(clientExec, () -> trackedClient.ready(1, true));
        assertEquals(MessageType.READY, readFrame(rawHost.getInputStream(), decoder).type());

        // 이전/불일치 revision START + 정상 START + 중복 START 연속 전송
        CountDownLatch threeStarts = new CountDownLatch(3);
        inboundLatch.set(threeStarts);
        rawHost.getOutputStream().write(encoder.encode(LobbyPayloads.startMessage("match-wrong-rev", 2)));
        rawHost.getOutputStream().write(encoder.encode(LobbyPayloads.startMessage("match-first", 1)));
        rawHost.getOutputStream().write(encoder.encode(LobbyPayloads.startMessage("match-duplicate", 1)));
        rawHost.getOutputStream().flush();
        assertTrue(threeStarts.await(WAIT_SECONDS, TimeUnit.SECONDS));

        assertEquals(Optional.of("match-first"), callOn(clientExec, trackedClient::matchId));
        long startingCount = callOn(
                clientExec, () -> observed.stream().filter(s -> s.state() == State.STARTING).count());
        assertEquals(1L, startingCount, "STARTING 스냅샷은 한 번만 발행되어야 한다");
    }

    @Test
    void 준비_중_상대가_끊기면_INITIAL로_돌아간다() throws Exception {
        BlockingQueue<LobbySnapshot> hostSnaps = subscribeQueue(hostExec, hostLobby);
        BlockingQueue<LobbySnapshot> clientSnaps = subscribeQueue(clientExec, clientLobby);
        enterLobby(hostSnaps, clientSnaps);

        runOn(hostExec, () -> hostLobby.selectConfig(CLASSIC));
        awaitSnapshot(clientSnaps, s -> s.configRevision() == 1);
        runOn(clientExec, () -> clientLobby.ready(1, true));
        awaitSnapshot(hostSnaps, s -> s.peerReady());

        runOn(clientExec, clientLobby::leave);
        runOn(clientExec, clientLobby::leave);

        LobbySnapshot clientReset = awaitSnapshot(clientSnaps, s -> s.state() == State.INITIAL);
        LobbySnapshot hostReset = awaitSnapshot(hostSnaps, s -> s.state() == State.INITIAL);
        assertEquals(Role.NONE, clientReset.role());
        assertEquals(Role.NONE, hostReset.role());
        assertNull(hostReset.config());
        assertEquals(0L, hostReset.configRevision());
        assertEquals(Optional.empty(), callOn(hostExec, hostLobby::matchId));
    }

    @Test
    void 연결_실패면_INITIAL로_돌아가고_stage가_EstablishException으로_끝난다() throws Exception {
        int closedPort;
        try (ServerSocket ss = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = ss.getLocalPort();
        }
        BlockingQueue<LobbySnapshot> clientSnaps = subscribeQueue(clientExec, clientLobby);
        assertEquals(State.INITIAL, clientSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS).state());

        CompletionStage<Void> stage =
                callOn(clientExec, () -> clientLobby.connect("127.0.0.1", closedPort));
        assertEquals(State.CONNECTING, clientSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS).state());

        ExecutionException ex = assertThrows(
                ExecutionException.class,
                () -> stage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS));
        EstablishException establishEx = assertInstanceOf(EstablishException.class, ex.getCause());
        assertEquals(EstablishFailure.REFUSED, establishEx.reason());

        LobbySnapshot reset = clientSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(State.INITIAL, reset.state());
        assertEquals(Role.NONE, reset.role());
    }

    @Test
    void close를_두_번_불러도_안전하고_이후_명령은_IllegalStateException() throws Exception {
        BlockingQueue<LobbySnapshot> hostSnaps = subscribeQueue(hostExec, hostLobby);
        assertEquals(State.INITIAL, hostSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS).state());

        // cancel 반복 무해 확인
        CompletionStage<Void> pending = callOn(hostExec, () -> hostLobby.host(0));
        assertEquals(State.HOSTING, hostSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS).state());
        runOn(hostExec, hostLobby::cancel);
        runOn(hostExec, hostLobby::cancel);
        assertTrue(pending.toCompletableFuture().isCancelled());
        assertEquals(State.INITIAL, hostSnaps.poll(WAIT_SECONDS, TimeUnit.SECONDS).state());

        // 구독 해제 후 콜백 없음 확인
        BlockingQueue<LobbySnapshot> unsubQueue = new LinkedBlockingQueue<>();
        LobbyClient.Subscription sub = callOn(hostExec, () -> hostLobby.subscribe(unsubQueue::add));
        assertEquals(1, unsubQueue.size());
        runOn(hostExec, sub::close);
        runOn(hostExec, sub::close);

        CompletionStage<Void> pendingBeforeClose = callOn(hostExec, () -> hostLobby.host(0));
        assertEquals(1, unsubQueue.size(), "구독 해제 후에는 새 스냅샷이 전달되지 않아야 한다");

        // close 두 번 호출 안전성 및 진행 중 stage 취소 확인
        runOn(hostExec, hostLobby::close);
        runOn(hostExec, hostLobby::close);
        assertTrue(pendingBeforeClose.toCompletableFuture().isCancelled());

        assertThrows(IllegalStateException.class, () -> callOn(hostExec, () -> hostLobby.host(0)));
        assertThrows(IllegalStateException.class, () -> callOn(hostExec, () -> hostLobby.connect("127.0.0.1", 47123)));
        assertThrows(IllegalStateException.class, () -> runOn(hostExec, hostLobby::cancel));
        assertThrows(IllegalStateException.class, () -> runOn(hostExec, () -> hostLobby.selectConfig(CLASSIC)));
        assertThrows(IllegalStateException.class, () -> runOn(hostExec, () -> hostLobby.ready(1, true)));
        assertThrows(IllegalStateException.class, () -> runOn(hostExec, hostLobby::leave));
        assertThrows(IllegalStateException.class, () -> callOn(hostExec, () -> hostLobby.subscribe(s -> {})));
    }
}
