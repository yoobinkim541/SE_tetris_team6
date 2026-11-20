package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;
import tetris.relay.LobbyCode;
import tetris.relay.RelayLimits;
import tetris.relay.RelayServer;

/** challenge: 중계 서버 경유 연결 수립. 수립 뒤에는 기존 HELLO·PeerLink 경로를 그대로 탄다 */
@Timeout(30)
class RelayEstablishTest {
    private static final RelayLimits RELAXED = new RelayLimits(300, 64, 10_000, Duration.ofSeconds(5), 100,
            Duration.ofMinutes(10), 100, Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10_000);

    private final AtomicLong clock = new AtomicLong();
    private RelayServer relay;
    private InetSocketAddress relayAddress;
    private ExecutorService hostExec;
    private ExecutorService clientExec;
    private PeerEstablisher hosts;
    private PeerEstablisher clients;

    @BeforeEach
    void setUp() throws Exception {
        relay = new RelayServer(0, RELAXED, clock::get, new Random(3));
        relay.start();
        relayAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), relay.localPort());
        hostExec = Executors.newSingleThreadExecutor();
        clientExec = Executors.newSingleThreadExecutor();
        hosts = new PeerEstablisher(hostExec, Duration.ofSeconds(5));
        clients = new PeerEstablisher(clientExec, Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        relay.close();
        hostExec.shutdownNow();
        clientExec.shutdownNow();
    }

    private static EstablishFailure failureOf(PeerEstablisher.Attempt attempt) throws Exception {
        ExecutionException e = assertThrows(ExecutionException.class, () -> attempt.result().get(10, TimeUnit.SECONDS));
        assertInstanceOf(EstablishException.class, e.getCause());
        return ((EstablishException) e.getCause()).reason();
    }

    private static void eventually(java.util.function.BooleanSupplier c, String what) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!c.getAsBoolean()) {
            if (System.nanoTime() > end) fail("timed out waiting for " + what);
            Thread.sleep(5);
        }
    }

    @Test
    void 호스트는_코드를_받고_친구가_그_코드로_들어오면_HELLO까지_수립된다() throws Exception {
        BlockingQueue<String> codes = new LinkedBlockingQueue<>();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        Thread hostDispatcher = hostExec.submit(Thread::currentThread).get();
        PeerEstablisher.Attempt host = hosts.hostRelay(relayAddress, code -> {
            callbackThread.set(Thread.currentThread());
            codes.add(code);
        });
        String code = codes.poll(5, TimeUnit.SECONDS);
        assertNotNull(code, "코드를 받지 못했다");
        assertSame(hostDispatcher, callbackThread.get(), "코드 알림은 dispatcher 스레드");

        PeerEstablisher.Attempt client = clients.joinRelay(relayAddress, LobbyCode.display(code).toLowerCase());
        PeerLink hostLink = host.result().get(10, TimeUnit.SECONDS);
        PeerLink clientLink = client.result().get(10, TimeUnit.SECONDS);

        assertEquals(PeerRole.HOST, hostLink.role());
        assertEquals(PeerRole.CLIENT, clientLink.role());
        String shown = "인터넷 · " + LobbyCode.display(code);
        assertEquals(shown, hostLink.peerAddress());
        assertEquals(shown, clientLink.peerAddress());
    }

    @Test
    void 수립된_뒤_메시지가_서버를_거쳐_오간다() throws Exception {
        BlockingQueue<String> codes = new LinkedBlockingQueue<>();
        PeerEstablisher.Attempt host = hosts.hostRelay(relayAddress, codes::add);
        PeerEstablisher.Attempt client = clients.joinRelay(relayAddress, codes.poll(5, TimeUnit.SECONDS));
        PeerLink hostLink = host.result().get(10, TimeUnit.SECONDS);
        PeerLink clientLink = client.result().get(10, TimeUnit.SECONDS);
        BlockingQueue<Message> received = new LinkedBlockingQueue<>();
        clientExec.submit(() -> clientLink.attach(new PeerListener() {
            @Override public void onMessage(Message message) { received.add(message); }
            @Override public void onClosed(CloseReason reason, String detail) { }
        })).get();

        hostLink.send(new ControlPayloads.Ping(7, 8).message());

        Message m = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(m);
        assertEquals(MessageType.PING, m.type());
        hostLink.close();
        clientLink.close();
    }

    @Test
    void 없는_코드는_NO_ROOM() throws Exception {
        assertEquals(EstablishFailure.NO_ROOM, failureOf(clients.joinRelay(relayAddress, "AAAAAA")));
    }

    @Test
    void 형식이_틀린_코드는_즉시_INVALID_ADDRESS() throws Exception {
        PeerEstablisher.Attempt attempt = clients.joinRelay(relayAddress, "0O1");
        assertTrue(attempt.result().isDone());
        assertEquals(EstablishFailure.INVALID_ADDRESS, failureOf(attempt));
    }

    @Test
    void 이미_짝지어진_방은_ROOM_FULL() throws Exception {
        BlockingQueue<String> codes = new LinkedBlockingQueue<>();
        PeerEstablisher.Attempt host = hosts.hostRelay(relayAddress, codes::add);
        String code = codes.poll(5, TimeUnit.SECONDS);
        PeerEstablisher.Attempt first = clients.joinRelay(relayAddress, code);
        first.result().get(10, TimeUnit.SECONDS);
        assertEquals(EstablishFailure.ROOM_FULL, failureOf(clients.joinRelay(relayAddress, code)));
        host.cancel();
    }

    @Test
    void 서버가_없으면_RELAY_UNAVAILABLE() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        InetSocketAddress nowhere = new InetSocketAddress(InetAddress.getLoopbackAddress(), closedPort);
        assertEquals(EstablishFailure.RELAY_UNAVAILABLE, failureOf(clients.joinRelay(nowhere, "AAAAAA")));
        assertEquals(EstablishFailure.RELAY_UNAVAILABLE, failureOf(hosts.hostRelay(nowhere, c -> { })));
    }

    @Test
    void 코드를_받은_뒤_취소하면_서버의_방도_사라진다() throws Exception {
        BlockingQueue<String> codes = new LinkedBlockingQueue<>();
        PeerEstablisher.Attempt host = hosts.hostRelay(relayAddress, codes::add);
        String code = codes.poll(5, TimeUnit.SECONDS);
        assertNotNull(code);

        hostExec.submit(host::cancel).get();

        assertThrows(CancellationException.class, () -> host.result().get(5, TimeUnit.SECONDS));
        eventually(() -> relay.waitingRooms() == 0, "room removed");
        assertEquals(EstablishFailure.NO_ROOM, failureOf(clients.joinRelay(relayAddress, code)));
    }

    @Test
    void 대기_방이_만료되면_ROOM_EXPIRED() throws Exception {
        BlockingQueue<String> codes = new LinkedBlockingQueue<>();
        PeerEstablisher.Attempt host = hosts.hostRelay(relayAddress, codes::add);
        assertNotNull(codes.poll(5, TimeUnit.SECONDS));
        clock.addAndGet(Duration.ofMinutes(11).toNanos());
        java.lang.reflect.Method sweep = RelayServer.class.getDeclaredMethod("sweep");
        sweep.setAccessible(true);
        sweep.invoke(relay);
        assertEquals(EstablishFailure.ROOM_EXPIRED, failureOf(host));
    }
}
