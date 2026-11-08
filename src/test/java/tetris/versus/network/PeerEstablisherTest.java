package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.CloseReason;
import tetris.feature.network.EstablishException;
import tetris.feature.network.EstablishFailure;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.PeerLink;
import tetris.feature.network.PeerListener;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;

/** S8-C1: 호스트 대기·클라이언트 접속·HELLO 핸드셰이크·타임아웃·취소 검증 (R3-HOST, R3-NETTEST) */
@Timeout(30)
class PeerEstablisherTest {
    private static final long WAIT_SECONDS = 5;

    private ExecutorService hostDispatcher;
    private ExecutorService clientDispatcher;
    private final List<PeerLink> links = new ArrayList<>();
    private final List<PeerEstablisher.Attempt> attempts = new ArrayList<>();
    private final List<AutoCloseable> resources = new ArrayList<>();

    @BeforeEach
    void setUp() {
        hostDispatcher = Executors.newSingleThreadExecutor();
        clientDispatcher = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (PeerEstablisher.Attempt attempt : attempts) {
            attempt.cancel();
        }
        for (PeerLink link : links) {
            link.close();
        }
        for (AutoCloseable resource : resources) {
            resource.close();
        }
        hostDispatcher.shutdownNow();
        clientDispatcher.shutdownNow();
    }

    private PeerEstablisher.Attempt track(PeerEstablisher.Attempt attempt) {
        attempts.add(attempt);
        return attempt;
    }

    private PeerLink track(PeerLink link) {
        links.add(link);
        return link;
    }

    private <T extends AutoCloseable> T trackResource(T resource) {
        resources.add(resource);
        return resource;
    }

    private static Message state(int tick) {
        return new Message(MessageType.STATE, "m-1", 0, JsonNodeFactory.instance.objectNode().put("tick", tick));
    }

    private static EstablishException expectEstablishFailure(CompletableFuture<?> future) throws Exception {
        ExecutionException ex = assertThrows(
                ExecutionException.class, () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));
        return assertInstanceOf(EstablishException.class, ex.getCause());
    }

    private static int drainUntilEof(InputStream in) throws IOException {
        byte[] buf = new byte[256];
        int r;
        while ((r = in.read(buf)) != -1) {
            // 프로토콜 오류 시 상대가 보낸 ERROR 프레임 바이트를 비우고 EOF를 확인한다
        }
        return r;
    }

    @Test
    void 호스트와_클라이언트가_HELLO를_교환하고_역할과_주소를_얻는다() throws Exception {
        PeerEstablisher hostEst = new PeerEstablisher(hostDispatcher);
        PeerEstablisher clientEst = new PeerEstablisher(clientDispatcher);

        PeerEstablisher.Attempt hostAttempt = track(hostEst.host(0));
        assertTrue(hostAttempt.localPort() > 0);
        PeerEstablisher.Attempt clientAttempt = track(clientEst.connect("127.0.0.1", hostAttempt.localPort()));
        assertEquals(-1, clientAttempt.localPort());

        PeerLink hostLink = track(hostAttempt.result().get(WAIT_SECONDS, TimeUnit.SECONDS));
        PeerLink clientLink = track(clientAttempt.result().get(WAIT_SECONDS, TimeUnit.SECONDS));

        assertEquals(PeerRole.HOST, hostLink.role());
        assertEquals(PeerRole.CLIENT, clientLink.role());
        assertEquals("127.0.0.1", hostLink.peerAddress());
        assertEquals("127.0.0.1", clientLink.peerAddress());
        assertTrue(hostLink.isOpen());
        assertTrue(clientLink.isOpen());
    }

    @Test
    void 수립_직후_attach_전에_온_메시지도_순서대로_받는다() throws Exception {
        AtomicBoolean handshakeCompleted = new AtomicBoolean(false);
        CountDownLatch threeBuffered = new CountDownLatch(3);
        Executor countingClientDispatcher = task -> clientDispatcher.execute(() -> {
            boolean afterHandshake = handshakeCompleted.get();
            task.run();
            if (afterHandshake) {
                threeBuffered.countDown();
            }
        });

        PeerEstablisher hostEst = new PeerEstablisher(hostDispatcher);
        PeerEstablisher clientEst = new PeerEstablisher(countingClientDispatcher);

        PeerEstablisher.Attempt hostAttempt = track(hostEst.host(0));
        PeerEstablisher.Attempt clientAttempt = track(clientEst.connect("127.0.0.1", hostAttempt.localPort()));
        clientAttempt.result().thenRun(() -> handshakeCompleted.set(true));

        PeerLink hostLink = track(hostAttempt.result().get(WAIT_SECONDS, TimeUnit.SECONDS));
        PeerLink clientLink = track(clientAttempt.result().get(WAIT_SECONDS, TimeUnit.SECONDS));

        assertTrue(hostLink.send(state(1)));
        assertTrue(hostLink.send(state(2)));
        assertTrue(hostLink.send(state(3)));

        assertTrue(threeBuffered.await(WAIT_SECONDS, TimeUnit.SECONDS), "attach 전에 3개 메시지가 버퍼에 쌓여야 한다");

        BlockingQueue<Message> received = new LinkedBlockingQueue<>();
        clientDispatcher.submit(() -> clientLink.attach(new PeerListener() {
            @Override
            public void onMessage(Message message) {
                received.add(message);
            }

            @Override
            public void onClosed(CloseReason reason, String detail) {
            }
        })).get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(1, received.poll(WAIT_SECONDS, TimeUnit.SECONDS).payload().get("tick").intValue());
        assertEquals(2, received.poll(WAIT_SECONDS, TimeUnit.SECONDS).payload().get("tick").intValue());
        assertEquals(3, received.poll(WAIT_SECONDS, TimeUnit.SECONDS).payload().get("tick").intValue());
    }

    @Test
    void 결과는_dispatcher_스레드에서_완료된다() throws Exception {
        Thread expectedHostThread = hostDispatcher.submit(Thread::currentThread).get(WAIT_SECONDS, TimeUnit.SECONDS);
        Thread expectedClientThread = clientDispatcher.submit(Thread::currentThread).get(WAIT_SECONDS, TimeUnit.SECONDS);

        PeerEstablisher hostEst = new PeerEstablisher(hostDispatcher);
        PeerEstablisher clientEst = new PeerEstablisher(clientDispatcher);

        AtomicReference<Thread> hostCompletionThread = new AtomicReference<>();
        AtomicReference<Thread> clientCompletionThread = new AtomicReference<>();
        CountDownLatch bothCallbacks = new CountDownLatch(2);

        // Register callbacks before either dispatcher can complete its future.
        // A late thenRun (or a get helping completion) can run on the test worker.
        CountDownLatch allowCompletion = new CountDownLatch(1);
        hostDispatcher.submit(() -> { allowCompletion.await(); return null; });
        clientDispatcher.submit(() -> { allowCompletion.await(); return null; });
        PeerEstablisher.Attempt hostAttempt;
        PeerEstablisher.Attempt clientAttempt;
        try {
            hostAttempt = track(hostEst.host(0));
            hostAttempt.result().thenRun(() -> {
                hostCompletionThread.set(Thread.currentThread());
                bothCallbacks.countDown();
            });

            clientAttempt = track(clientEst.connect("127.0.0.1", hostAttempt.localPort()));
            clientAttempt.result().thenRun(() -> {
                clientCompletionThread.set(Thread.currentThread());
                bothCallbacks.countDown();
            });
        } finally {
            allowCompletion.countDown();
        }
        boolean callbacksReceived = bothCallbacks.await(WAIT_SECONDS, TimeUnit.SECONDS);
        track(hostAttempt.result().get(WAIT_SECONDS, TimeUnit.SECONDS));
        track(clientAttempt.result().get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(callbacksReceived);

        assertEquals(expectedHostThread, hostCompletionThread.get());
        assertEquals(expectedClientThread, clientCompletionThread.get());
    }

    @Test
    void 닫힌_포트에_접속하면_REFUSED() throws Exception {
        int closedPort;
        try (ServerSocket ss = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = ss.getLocalPort();
        }
        PeerEstablisher clientEst = new PeerEstablisher(clientDispatcher);
        PeerEstablisher.Attempt attempt = track(clientEst.connect("127.0.0.1", closedPort));

        EstablishException ex = expectEstablishFailure(attempt.result());
        assertEquals(EstablishFailure.REFUSED, ex.reason());
    }

    @Test
    void 잘못된_주소는_즉시_INVALID_ADDRESS() throws Exception {
        PeerEstablisher clientEst = new PeerEstablisher(clientDispatcher);
        PeerEstablisher.Attempt attempt = track(clientEst.connect("999.1.1.1", 47123));

        assertTrue(attempt.result().isDone(), "잘못된 주소는 스레드를 시작하지 않고 즉시 완료되어야 한다");
        EstablishException ex = expectEstablishFailure(attempt.result());
        assertEquals(EstablishFailure.INVALID_ADDRESS, ex.reason());
    }

    @Test
    void 사용_중인_포트로_호스트하면_PORT_IN_USE() throws Exception {
        PeerEstablisher hostEst = new PeerEstablisher(hostDispatcher);
        PeerEstablisher.Attempt first = track(hostEst.host(0));
        int port = first.localPort();

        PeerEstablisher.Attempt second = track(hostEst.host(port));
        assertTrue(second.result().isDone(), "바인드 충돌은 즉시 실패해야 한다");
        EstablishException ex = expectEstablishFailure(second.result());
        assertEquals(EstablishFailure.PORT_IN_USE, ex.reason());
    }

    @Test
    void HELLO가_오지_않으면_timeout_후_TIMEOUT이고_소켓을_닫는다() throws Exception {
        ServerSocket silentServer = trackResource(new ServerSocket(0, 1, InetAddress.getLoopbackAddress()));
        CompletableFuture<Socket> acceptedFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return silentServer.accept();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        PeerEstablisher clientEst = new PeerEstablisher(clientDispatcher, Duration.ofMillis(300));
        PeerEstablisher.Attempt attempt = track(clientEst.connect("127.0.0.1", silentServer.getLocalPort()));

        Socket serverSideSocket = trackResource(acceptedFuture.get(WAIT_SECONDS, TimeUnit.SECONDS));
        EstablishException ex = expectEstablishFailure(attempt.result());
        assertEquals(EstablishFailure.TIMEOUT, ex.reason());
        assertEquals(-1, drainUntilEof(serverSideSocket.getInputStream()));
    }

    @Test
    void 같은_역할의_HELLO는_PROTOCOL_ERROR() throws Exception {
        PeerEstablisher hostEst = new PeerEstablisher(hostDispatcher);
        PeerEstablisher.Attempt hostAttempt = track(hostEst.host(0));

        Socket rawClient = trackResource(new Socket(InetAddress.getLoopbackAddress(), hostAttempt.localPort()));
        byte[] sameRoleHello = new FrameEncoder().encode(ControlPayloads.Hello.message(PeerRole.HOST));
        rawClient.getOutputStream().write(sameRoleHello);
        rawClient.getOutputStream().flush();

        EstablishException ex = expectEstablishFailure(hostAttempt.result());
        assertEquals(EstablishFailure.PROTOCOL_ERROR, ex.reason());
        assertEquals(-1, drainUntilEof(rawClient.getInputStream()));
    }

    @Test
    void HELLO_전에_상대가_바로_끊으면_IO_ERROR이고_다른_메시지를_보내면_PROTOCOL_ERROR() throws Exception {
        PeerEstablisher hostEst = new PeerEstablisher(hostDispatcher);

        PeerEstablisher.Attempt eofAttempt = track(hostEst.host(0));
        try (Socket raw = new Socket(InetAddress.getLoopbackAddress(), eofAttempt.localPort())) {
            // 아무것도 보내지 않고 즉시 닫음
        }
        assertEquals(EstablishFailure.IO_ERROR, expectEstablishFailure(eofAttempt.result()).reason());

        PeerEstablisher.Attempt wrongMsgAttempt = track(hostEst.host(0));
        Socket wrongPeer = trackResource(new Socket(InetAddress.getLoopbackAddress(), wrongMsgAttempt.localPort()));
        byte[] pingBeforeHello = new FrameEncoder().encode(new ControlPayloads.Ping(1L, 100L).message());
        wrongPeer.getOutputStream().write(pingBeforeHello);
        wrongPeer.getOutputStream().flush();

        assertEquals(EstablishFailure.PROTOCOL_ERROR, expectEstablishFailure(wrongMsgAttempt.result()).reason());
        assertEquals(-1, drainUntilEof(wrongPeer.getInputStream()));
    }

    @Test
    void 대기_중인_호스트를_취소하면_future가_취소되고_포트를_다시_쓸_수_있다() throws Exception {
        PeerEstablisher hostEst = new PeerEstablisher(hostDispatcher);
        PeerEstablisher.Attempt first = track(hostEst.host(0));
        int port = first.localPort();

        hostDispatcher.submit(first::cancel).get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertTrue(first.result().isCancelled());
        assertThrows(CancellationException.class, () -> first.result().get(WAIT_SECONDS, TimeUnit.SECONDS));

        PeerEstablisher.Attempt rebound = track(hostEst.host(port));
        assertEquals(port, rebound.localPort());
        assertFalse(rebound.result().isDone());
        hostDispatcher.submit(rebound::cancel).get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 취소한_이전_시도의_타이머가_새_연결에_영향을_주지_않는다() throws Exception {
        ServerSocket silentServer = trackResource(new ServerSocket(0, 1, InetAddress.getLoopbackAddress()));
        CompletableFuture<Socket> silentAccepted = CompletableFuture.supplyAsync(() -> {
            try {
                return silentServer.accept();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        PeerEstablisher clientEst = new PeerEstablisher(clientDispatcher, Duration.ofMillis(300));
        PeerEstablisher hostEst = new PeerEstablisher(hostDispatcher, Duration.ofMillis(300));

        PeerEstablisher.Attempt attempt1 = track(clientEst.connect("127.0.0.1", silentServer.getLocalPort()));
        Socket silentPeer = trackResource(silentAccepted.get(WAIT_SECONDS, TimeUnit.SECONDS));
        clientDispatcher.submit(attempt1::cancel).get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertTrue(attempt1.result().isCancelled());
        assertEquals(-1, drainUntilEof(silentPeer.getInputStream()));

        PeerEstablisher.Attempt attempt2Host = track(hostEst.host(0));
        PeerEstablisher.Attempt attempt2Client = track(clientEst.connect("127.0.0.1", attempt2Host.localPort()));
        PeerLink hostLink = track(attempt2Host.result().get(WAIT_SECONDS, TimeUnit.SECONDS));
        PeerLink clientLink = track(attempt2Client.result().get(WAIT_SECONDS, TimeUnit.SECONDS));

        CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(600, TimeUnit.MILLISECONDS))
                .get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertTrue(hostLink.isOpen(), "이전 시도의 타이머가 새 호스트 연결을 닫으면 안 된다");
        assertTrue(clientLink.isOpen(), "이전 시도의 타이머가 새 클라이언트 연결을 닫으면 안 된다");
    }

    @Test
    void 수립_후_close하면_두_스레드가_끝난다() throws Exception {
        PeerEstablisher hostEst = new PeerEstablisher(hostDispatcher);
        PeerEstablisher clientEst = new PeerEstablisher(clientDispatcher);

        PeerEstablisher.Attempt hostAttempt = track(hostEst.host(0));
        PeerEstablisher.Attempt clientAttempt = track(clientEst.connect("127.0.0.1", hostAttempt.localPort()));

        PeerLink hostLink = track(hostAttempt.result().get(WAIT_SECONDS, TimeUnit.SECONDS));
        PeerLink clientLink = track(clientAttempt.result().get(WAIT_SECONDS, TimeUnit.SECONDS));

        hostLink.close();
        clientLink.close();

        assertTrue(hostLink.awaitTermination(Duration.ofSeconds(WAIT_SECONDS)));
        assertTrue(clientLink.awaitTermination(Duration.ofSeconds(WAIT_SECONDS)));
        assertFalse(hostLink.isOpen());
        assertFalse(clientLink.isOpen());
    }
}
