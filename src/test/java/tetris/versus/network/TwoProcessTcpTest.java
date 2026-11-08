package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.CloseReason;
import tetris.feature.network.PeerConnection;
import tetris.feature.network.PeerListener;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.protocol.ProtocolException;

/**
 * S7-C2: 엔진 없는 두 JVM 프로세스가 실제 loopback TCP로 HELLO·가짜 STATE·PING/PONG·LEAVE를 주고받는다 (R3-NETTEST).
 * 단일 PC loopback 결과이며 두 PC LAN 200ms 합격(R3-LATENCY, S11-C1)을 대신하지 않는다.
 */
@Timeout(60)
public class TwoProcessTcpTest {
    private static final int STATES = 30;
    private static final int PINGS = 200;

    /** 호스트 역할. 콜백은 dispatcher 한 스레드에서 실행된다 */
    private static final class ProbeHost implements PeerListener {
        final BlockingQueue<MessageType> received = new LinkedBlockingQueue<>();
        final BlockingQueue<CloseReason> closes = new LinkedBlockingQueue<>();
        PeerConnection connection;
        int pongs;

        @Override
        public void onMessage(Message message) {
            received.add(message.type());
            try {
                switch (message.type()) {
                    case HELLO -> {
                        connection.send(ControlPayloads.Hello.message(PeerRole.HOST));
                        for (int tick = 1; tick <= STATES; tick++) connection.send(TcpProbe.fakeState(tick));
                    }
                    case PING -> {
                        connection.send(ControlPayloads.Ping.from(message.payload()).pong());
                        pongs++;
                    }
                    case LEAVE -> connection.close();
                    default -> throw new IllegalStateException("unexpected " + message.type());
                }
            } catch (ProtocolException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onClosed(CloseReason reason, String detail) {
            closes.add(reason);
        }
    }

    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor();
    private Process child;

    @AfterEach
    void cleanUp() {
        if (child != null) child.destroyForcibly();
        dispatcher.shutdownNow();
    }

    private Process launch(String... probeArgs) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(
                java, "-cp", System.getProperty("java.class.path"), TcpProbe.class.getName()));
        command.addAll(List.of(probeArgs));
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }

    /** 첫 콜백보다 host.connection이 먼저 보이도록 연결을 붙이는 동안 dispatcher를 잠시 막는다 */
    private PeerConnection accept(ServerSocket server, ProbeHost host) throws IOException {
        server.setSoTimeout((int) TimeUnit.SECONDS.toMillis(20));
        Socket socket = server.accept();
        CountDownLatch attached = new CountDownLatch(1);
        dispatcher.execute(() -> {
            try {
                attached.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        host.connection = PeerConnection.start(socket, dispatcher, host);
        attached.countDown();
        return host.connection;
    }

    @Test
    void 두_프로세스가_HELLO_가짜_STATE_PING_LEAVE를_주고받고_정상_종료한다() throws Exception {
        ProbeHost host = new ProbeHost();
        PeerConnection connection;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            child = launch("client", String.valueOf(server.getLocalPort()), String.valueOf(STATES), String.valueOf(PINGS));
            connection = accept(server, host);
        }

        assertTrue(child.waitFor(40, TimeUnit.SECONDS), "클라이언트 프로세스가 끝나지 않았다");
        String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, child.exitValue(), output);
        JsonNode result = parseResult(output);

        assertEquals(STATES, result.get("states").intValue());
        assertEquals(PINGS, result.get("pings").intValue());
        assertEquals(CloseReason.LOCAL, host.closes.poll(10, TimeUnit.SECONDS));
        assertTrue(connection.awaitTermination(Duration.ofSeconds(10)));
        assertEquals(MessageType.HELLO, host.received.poll());
        assertEquals(PINGS, host.pongs);
        System.out.println("S7-C2 loopback 두 프로세스 RTT(µs, PING→PONG " + PINGS + "회): " + result);
    }

    @Test
    void 상대_프로세스가_강제_종료되면_연결을_닫고_스레드와_포트를_해제한다() throws Exception {
        ProbeHost host = new ProbeHost();
        int port;
        PeerConnection connection;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
            child = launch("hang", String.valueOf(port));
            connection = accept(server, host);
            assertEquals(MessageType.HELLO, host.received.poll(20, TimeUnit.SECONDS));

            child.destroyForcibly().waitFor(10, TimeUnit.SECONDS);

            CloseReason reason = host.closes.poll(10, TimeUnit.SECONDS);
            assertNotNull(reason, "강제 종료를 감지하지 못했다");
            assertTrue(Set.of(CloseReason.PEER_CLOSED, CloseReason.IO_ERROR).contains(reason), reason.toString());
            assertTrue(connection.awaitTermination(Duration.ofSeconds(10)), "수신·송신 스레드가 남아 있다");
        }

        try (ServerSocket rebound = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            assertEquals(port, rebound.getLocalPort());
        }
    }

    private static JsonNode parseResult(String output) throws IOException {
        for (String line : output.split("\\R")) {
            if (line.startsWith(TcpProbe.RESULT_PREFIX)) {
                return new ObjectMapper().readTree(line.substring(TcpProbe.RESULT_PREFIX.length()));
            }
        }
        throw new AssertionError("PROBE_RESULT 줄이 없다:\n" + output);
    }
}
