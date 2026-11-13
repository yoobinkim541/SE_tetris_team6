package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.EstablishException;
import tetris.feature.network.EstablishFailure;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.tools.LagProxy;

/** S11-C1·S12-C1: 지연 주입 중계 LagProxy의 지연·정지·양방향 종료 검증 (R3-LATENCY, R3-DISCONNECT) */
@Timeout(30)
class LagProxyTest {
    private static final int DELAY_MILLIS = 120;
    private static final long WAIT_SECONDS = 10;

    private final ExecutorService hostDispatcher = Executors.newSingleThreadExecutor();
    private final ExecutorService clientDispatcher = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        hostDispatcher.shutdownNow();
        clientDispatcher.shutdownNow();
    }

    private static InetAddress loopback() throws IOException {
        return InetAddress.getLoopbackAddress();
    }

    /** 받은 바이트를 그대로 돌려주는 원시 에코 서버. 첫 접속 하나만 상대한다 */
    private static Thread startEchoServer(ServerSocket server, AtomicInteger lastRead) {
        Thread thread = new Thread(
                () -> {
                    try (Socket socket = server.accept()) {
                        socket.setTcpNoDelay(true);
                        InputStream in = socket.getInputStream();
                        OutputStream out = socket.getOutputStream();
                        byte[] buffer = new byte[256];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            out.flush();
                        }
                        lastRead.set(read);
                    } catch (IOException ignored) {
                        // 테스트 종료 시 소켓이 닫히면 여기로 온다
                    }
                },
                "lag-proxy-test-echo");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void awaitNanos(long nanos) {
        long deadline = System.nanoTime() + nanos;
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0) {
            LockSupport.parkNanos(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(20)));
        }
    }

    private static void awaitRelayThreadsStopped() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean alive = true;
        while (System.nanoTime() < deadline) {
            alive = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.isAlive()
                            && thread.getName().startsWith("lag-proxy-")
                            && !thread.getName().contains("test-echo"));
            if (!alive) return;
            awaitNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        throw new AssertionError("close() 뒤에도 중계 스레드가 살아 있다");
    }

    @Test
    void 지연_주입이_왕복을_두_방향_모두_늦춘다() throws Exception {
        try (ServerSocket echoServer = new ServerSocket(0, 1, loopback())) {
            startEchoServer(echoServer, new AtomicInteger(Integer.MIN_VALUE));
            try (LagProxy proxy = new LagProxy(
                    0, new InetSocketAddress(loopback(), echoServer.getLocalPort()), DELAY_MILLIS, -1)) {
                proxy.start();
                try (Socket client = new Socket(loopback(), proxy.localPort())) {
                    client.setTcpNoDelay(true);
                    client.setSoTimeout((int) TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

                    long startNanos = System.nanoTime();
                    client.getOutputStream().write(new byte[] {42});
                    client.getOutputStream().flush();

                    assertEquals(42, client.getInputStream().read());
                    long roundTripMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                    assertTrue(
                            roundTripMillis >= 2L * DELAY_MILLIS,
                            "양방향 지연 " + (2L * DELAY_MILLIS) + "ms 이상이어야 한다: " + roundTripMillis + "ms");
                }
            }
        }
    }

    @Test
    void 정지_시각_뒤_바이트는_전달되지_않고_연결은_남는다() throws Exception {
        try (ServerSocket echoServer = new ServerSocket(0, 1, loopback())) {
            startEchoServer(echoServer, new AtomicInteger(Integer.MIN_VALUE));
            try (LagProxy proxy = new LagProxy(
                    0, new InetSocketAddress(loopback(), echoServer.getLocalPort()), 0, 100)) {
                proxy.start();
                try (Socket client = new Socket(loopback(), proxy.localPort())) {
                    client.setTcpNoDelay(true);
                    client.setSoTimeout((int) TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

                    client.getOutputStream().write(new byte[] {1});
                    client.getOutputStream().flush();
                    assertEquals(1, client.getInputStream().read(), "정지 전 바이트는 그대로 통과해야 한다");

                    awaitNanos(TimeUnit.MILLISECONDS.toNanos(250));
                    assertFalse(client.isClosed(), "정지 뒤에도 연결은 유지된다");

                    client.setSoTimeout(1_000);
                    client.getOutputStream().write(new byte[] {2});
                    client.getOutputStream().flush();
                    assertThrows(
                            SocketTimeoutException.class,
                            () -> client.getInputStream().read(),
                            "정지 뒤 바이트는 1초 안에 전달되지 않아야 한다");
                }
            }
        }
    }

    @Test
    void 한쪽이_닫히면_다른_쪽도_EOF를_받고_중계_스레드가_끝난다() throws Exception {
        AtomicInteger echoLastRead = new AtomicInteger(Integer.MIN_VALUE);
        try (ServerSocket echoServer = new ServerSocket(0, 1, loopback())) {
            Thread echo = startEchoServer(echoServer, echoLastRead);
            try (LagProxy proxy = new LagProxy(
                    0, new InetSocketAddress(loopback(), echoServer.getLocalPort()), 0, -1)) {
                proxy.start();
                try (Socket client = new Socket(loopback(), proxy.localPort())) {
                    client.setTcpNoDelay(true);
                    client.getOutputStream().write(new byte[] {7});
                    client.getOutputStream().flush();
                    assertEquals(7, client.getInputStream().read(), "중계가 살아 있어야 한다");
                }
                echo.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
                assertFalse(echo.isAlive(), "클라이언트가 닫히면 에코 서버 쪽 read도 끝나야 한다");
                assertEquals(-1, echoLastRead.get(), "에코 서버는 EOF를 받아야 한다");
            }
        }
        awaitRelayThreadsStopped();
    }

    @Test
    void 정지된_중계_뒤에서는_HELLO_핸드셰이크가_시간_초과로_실패한다() throws Exception {
        PeerEstablisher host = new PeerEstablisher(hostDispatcher);
        PeerEstablisher.Attempt hostAttempt = host.host(0);
        try {
            assertTrue(hostAttempt.localPort() > 0);
            try (LagProxy proxy = new LagProxy(
                    0, new InetSocketAddress(loopback(), hostAttempt.localPort()), 0, 0)) {
                proxy.start();
                PeerEstablisher client = new PeerEstablisher(clientDispatcher, Duration.ofMillis(300));
                PeerEstablisher.Attempt clientAttempt = client.connect("127.0.0.1", proxy.localPort());
                try {
                    ExecutionException failure = assertThrows(
                            ExecutionException.class,
                            () -> clientAttempt.result().get(WAIT_SECONDS, TimeUnit.SECONDS));
                    EstablishException cause = assertInstanceOf(EstablishException.class, failure.getCause());
                    assertEquals(EstablishFailure.TIMEOUT, cause.reason());
                } finally {
                    clientAttempt.cancel();
                }
            }
        } finally {
            hostAttempt.cancel();
        }
    }

    @Test
    void 잘못된_지연_설정은_거부한다() throws Exception {
        InetSocketAddress target = new InetSocketAddress(loopback(), 47123);
        assertThrows(IllegalArgumentException.class, () -> new LagProxy(0, target, -1, -1));
        assertThrows(IllegalArgumentException.class, () -> new LagProxy(0, target, 0, -2));
        assertThrows(IllegalArgumentException.class, () -> new LagProxy(-1, target, 0, -1));

        try (LagProxy proxy = new LagProxy(0, target, 0, -1)) {
            proxy.start();
            assertTrue(proxy.localPort() > 0);
        }
    }
}
