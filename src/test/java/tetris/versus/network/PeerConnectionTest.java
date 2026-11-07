package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.CloseReason;
import tetris.feature.network.PeerConnection;
import tetris.feature.network.PeerListener;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.ErrorCode;
import tetris.feature.network.protocol.FrameDecoder;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.network.protocol.WireFormat;

/** S7-C2: 실제 loopback TCP 소켓 위의 PeerConnection (R3-NETTEST) */
@Timeout(30)
public class PeerConnectionTest {
    private static final long WAIT_SECONDS = 5;

    /** 콜백 기록. dispatcher가 한 스레드라 순서가 보존된다 */
    private static final class Recorder implements PeerListener {
        final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<CloseReason> closes = new LinkedBlockingQueue<>();
        final BlockingQueue<Boolean> onEdt = new LinkedBlockingQueue<>();

        @Override
        public void onMessage(Message message) {
            onEdt.add(SwingUtilities.isEventDispatchThread());
            messages.add(message);
        }

        @Override
        public void onClosed(CloseReason reason, String detail) {
            onEdt.add(SwingUtilities.isEventDispatchThread());
            closes.add(reason);
        }

        Message nextMessage() throws InterruptedException {
            return messages.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        CloseReason nextClose() throws InterruptedException {
            return closes.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 테스트가 실행 시점을 정하는 dispatcher. execute는 큐에 넣기만 하고 {@link #runAll()}이 테스트 스레드에서 실행한다.
     * holdFirst이면 첫 execute를 부른 스레드(첫 메시지를 넘기는 수신 스레드)를 release까지 붙잡는다.
     */
    private static final class ManualDispatcher implements Executor {
        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        final CountDownLatch firstArrived = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean first = new AtomicBoolean(true);
        private final boolean holdFirst;

        ManualDispatcher(boolean holdFirst) {
            this.holdFirst = holdFirst;
        }

        @Override
        public void execute(Runnable task) {
            if (first.compareAndSet(true, false)) {
                firstArrived.countDown();
                if (holdFirst) {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            tasks.add(task);
        }

        void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) task.run();
        }
    }

    private ServerSocket server;
    private final List<Socket> sockets = new ArrayList<>();
    private final List<PeerConnection> connections = new ArrayList<>();
    private ExecutorService dispatcher;

    @BeforeEach
    void open() throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        dispatcher = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void closeAll() throws IOException {
        connections.forEach(PeerConnection::close);
        for (Socket socket : sockets) socket.close();
        server.close();
        dispatcher.shutdownNow();
    }

    /** [0] = 접속한 쪽, [1] = 받은 쪽 */
    private Socket[] socketPair() throws IOException {
        Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
        Socket accepted = server.accept();
        sockets.add(client);
        sockets.add(accepted);
        return new Socket[] {client, accepted};
    }

    private PeerConnection start(Socket socket, PeerListener listener) {
        PeerConnection connection = PeerConnection.start(socket, dispatcher, listener);
        connections.add(connection);
        return connection;
    }

    private static Message state(int tick) {
        return new Message(MessageType.STATE, "m", 0, JsonNodeFactory.instance.objectNode().put("tick", tick));
    }

    private static byte[] frames(Message... messages) throws ProtocolException {
        FrameEncoder encoder = new FrameEncoder();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Message message : messages) out.writeBytes(encoder.encode(message));
        return out.toByteArray();
    }

    /** 원시 소켓에서 프레임을 디코딩해 하나 읽는다. EOF면 null */
    private static Message readOne(InputStream in, FrameDecoder decoder) throws IOException, ProtocolException {
        byte[] buffer = new byte[512];
        int n;
        while ((n = in.read(buffer)) != -1) {
            List<Message> decoded = decoder.feed(buffer, 0, n);
            if (!decoded.isEmpty()) return decoded.get(0);
        }
        return null;
    }

    //#region 송수신
    @Test
    void 연결된_두_쪽은_보낸_순서대로_메시지를_받는다() throws Exception {
        Socket[] pair = socketPair();
        Recorder clientSide = new Recorder();
        Recorder hostSide = new Recorder();
        PeerConnection client = start(pair[0], clientSide);
        PeerConnection host = start(pair[1], hostSide);

        assertTrue(client.send(ControlPayloads.Hello.message(PeerRole.CLIENT)));
        for (int tick = 1; tick <= 50; tick++) assertTrue(host.send(state(tick)));

        assertEquals(PeerRole.CLIENT, ControlPayloads.Hello.from(hostSide.nextMessage().payload()).role());
        for (int tick = 1; tick <= 50; tick++) {
            assertEquals(tick, clientSide.nextMessage().payload().get("tick").intValue());
        }
    }

    @Test
    void 상대가_프레임을_한_바이트씩_보내도_완성된_메시지만_받는다() throws Exception {
        Socket[] pair = socketPair();
        Recorder recorder = new Recorder();
        start(pair[1], recorder);
        byte[] bytes = frames(state(1), state(2));
        OutputStream raw = pair[0].getOutputStream();

        for (byte b : bytes) {
            raw.write(b);
            raw.flush();
        }

        assertEquals(1, recorder.nextMessage().payload().get("tick").intValue());
        assertEquals(2, recorder.nextMessage().payload().get("tick").intValue());
        assertNull(recorder.messages.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void 여러_프레임을_한_번에_써도_모두_받는다() throws Exception {
        Socket[] pair = socketPair();
        Recorder recorder = new Recorder();
        start(pair[1], recorder);
        Message[] sent = new Message[20];
        for (int i = 0; i < sent.length; i++) sent[i] = state(i);

        pair[0].getOutputStream().write(frames(sent));

        for (Message message : sent) assertEquals(message, recorder.nextMessage());
    }
    //#endregion

    //#region 종료와 자원 해제
    @Test
    void 잘못된_프레임을_받으면_ERROR를_보내고_닫으며_메시지는_넘기지_않는다() throws Exception {
        Socket[] pair = socketPair();
        Recorder recorder = new Recorder();
        PeerConnection connection = start(pair[1], recorder);
        byte[] body = "not json".getBytes(StandardCharsets.UTF_8);

        pair[0].getOutputStream().write(ByteBuffer.allocate(4 + body.length).putInt(body.length).put(body).array());

        assertEquals(CloseReason.PROTOCOL_ERROR, recorder.nextClose());
        assertTrue(recorder.messages.isEmpty());
        FrameDecoder decoder = new FrameDecoder();
        Message error = readOne(pair[0].getInputStream(), decoder);
        assertEquals(MessageType.ERROR, error.type());
        assertEquals(ErrorCode.MALFORMED_FRAME, ControlPayloads.Failure.from(error.payload()).code());
        assertNull(readOne(pair[0].getInputStream(), decoder), "ERROR 뒤에는 연결이 닫혀야 한다");
        assertTrue(connection.awaitTermination(Duration.ofSeconds(WAIT_SECONDS)));
    }

    @Test
    void 상대가_연결을_닫으면_받은_메시지를_모두_넘긴_뒤_PEER_CLOSED를_한_번_알린다() throws Exception {
        Socket[] pair = socketPair();
        Recorder recorder = new Recorder();
        PeerConnection connection = start(pair[1], recorder);

        pair[0].getOutputStream().write(frames(state(1), state(2)));
        pair[0].close();

        assertEquals(state(1), recorder.nextMessage());
        assertEquals(state(2), recorder.nextMessage());
        assertEquals(CloseReason.PEER_CLOSED, recorder.nextClose());
        assertTrue(connection.awaitTermination(Duration.ofSeconds(WAIT_SECONDS)));
        assertNull(recorder.closes.poll(200, TimeUnit.MILLISECONDS), "종료 알림은 한 번만");
        assertFalse(connection.isOpen());
    }

    @Test
    void 읽기_대기_중에_close하면_스레드와_소켓을_정리하고_LOCAL을_한_번_알린다() throws Exception {
        Socket[] pair = socketPair();
        Recorder recorder = new Recorder();
        PeerConnection connection = start(pair[1], recorder);

        connection.close();
        connection.close();

        assertEquals(CloseReason.LOCAL, recorder.nextClose());
        assertTrue(connection.awaitTermination(Duration.ofSeconds(WAIT_SECONDS)));
        assertTrue(pair[1].isClosed());
        assertEquals(-1, pair[0].getInputStream().read(), "상대는 EOF를 받는다");
        assertNull(recorder.closes.poll(200, TimeUnit.MILLISECONDS), "종료 알림은 한 번만");
    }

    @Test
    void 닫힌_연결의_send는_거부한다() throws Exception {
        Socket[] pair = socketPair();
        PeerConnection connection = start(pair[1], new Recorder());

        connection.close();

        assertFalse(connection.send(state(1)));
    }

    @Test
    void 로컬에서_닫은_뒤에는_이미_받은_메시지도_넘기지_않는다() throws Exception {
        Socket[] pair = socketPair();
        Recorder recorder = new Recorder();
        ManualDispatcher manual = new ManualDispatcher(false);
        PeerConnection connection = PeerConnection.start(pair[1], manual, recorder);
        connections.add(connection);
        pair[0].getOutputStream().write(frames(state(1)));
        assertTrue(manual.firstArrived.await(WAIT_SECONDS, TimeUnit.SECONDS), "수신 스레드가 메시지를 넘기지 않았다");

        connection.close();
        manual.runAll();

        assertEquals(List.of(CloseReason.LOCAL), List.copyOf(recorder.closes));
        assertTrue(recorder.messages.isEmpty(), "이전 연결 세대의 메시지가 새 화면에 섞이지 않게 한다");
    }

    @Test
    void 다른_원인으로_닫힌_뒤_넘어온_메시지는_onClosed_다음에_전달하지_않는다() throws Exception {
        Socket[] pair = socketPair();
        Recorder recorder = new Recorder();
        ManualDispatcher manual = new ManualDispatcher(true);
        PeerConnection connection = PeerConnection.start(pair[1], manual, recorder);
        connections.add(connection);
        pair[0].getOutputStream().write(frames(state(1)));
        assertTrue(manual.firstArrived.await(WAIT_SECONDS, TimeUnit.SECONDS), "수신 스레드가 메시지를 넘기지 않았다");
        Message big = new Message(MessageType.STATE, "m", 0,
                JsonNodeFactory.instance.objectNode().put("pad", "x".repeat(60_000)));

        // 수신 스레드가 메시지를 넘기는 도중, 상대가 읽지 않아 송신 대기열이 넘쳐 닫힌다
        while (connection.isOpen()) connection.send(big);
        manual.release.countDown();
        assertTrue(connection.awaitTermination(Duration.ofSeconds(WAIT_SECONDS)));
        manual.runAll();

        assertEquals(List.of(CloseReason.SEND_BACKLOG), List.copyOf(recorder.closes));
        assertTrue(recorder.messages.isEmpty(), "onClosed 뒤에 onMessage가 왔다");
    }

    @Test
    void dispatcher가_작업을_거부해도_소켓과_스레드를_정리한다() throws Exception {
        Socket[] pair = socketPair();
        Executor rejecting = task -> {
            throw new RejectedExecutionException("dispatcher shut down");
        };
        PeerConnection connection = PeerConnection.start(pair[1], rejecting, new Recorder());
        connections.add(connection);

        pair[0].getOutputStream().write(frames(state(1)));

        assertTrue(connection.awaitTermination(Duration.ofSeconds(WAIT_SECONDS)), "수신·송신 스레드가 남아 있다");
        assertFalse(connection.isOpen());
        assertEquals(-1, pair[0].getInputStream().read(), "상대는 EOF를 받는다");
    }

    @Test
    void 상대가_보낸_값이_길어도_ERROR를_보낸_뒤_닫는다() throws Exception {
        Socket[] pair = socketPair();
        Recorder recorder = new Recorder();
        start(pair[1], recorder);
        String prefix = "{\"version\":1,\"sequence\":1,\"configRevision\":0,\"payload\":{},\"type\":\"";
        String suffix = "\"}";
        String type = "Z".repeat(WireFormat.MAX_FRAME_BYTES - prefix.length() - suffix.length());
        byte[] body = (prefix + type + suffix).getBytes(StandardCharsets.UTF_8);

        pair[0].getOutputStream().write(ByteBuffer.allocate(4 + body.length).putInt(body.length).put(body).array());

        assertEquals(CloseReason.PROTOCOL_ERROR, recorder.nextClose());
        Message error = readOne(pair[0].getInputStream(), new FrameDecoder());
        assertTrue(error != null && error.type() == MessageType.ERROR, "ERROR 프레임을 받지 못했다");
        assertEquals(ErrorCode.UNKNOWN_TYPE, ControlPayloads.Failure.from(error.payload()).code());
    }
    //#endregion

    //#region EDT·역압력
    @Test
    void 콜백은_주입한_dispatcher인_EDT에서만_실행된다() throws Exception {
        Socket[] pair = socketPair();
        Recorder recorder = new Recorder();
        PeerConnection connection = PeerConnection.start(pair[1], SwingUtilities::invokeLater, recorder);
        connections.add(connection);

        pair[0].getOutputStream().write(frames(state(1)));
        assertEquals(state(1), recorder.nextMessage());
        pair[0].close();
        assertEquals(CloseReason.PEER_CLOSED, recorder.nextClose());

        assertEquals(List.of(true, true), List.copyOf(recorder.onEdt));
    }

    @Test
    void 상대가_읽지_않아도_EDT의_send는_막히지_않고_큐가_넘치면_SEND_BACKLOG로_닫는다() throws Exception {
        Socket[] pair = socketPair();
        Recorder recorder = new Recorder();
        PeerConnection connection = start(pair[1], recorder);
        Message big = new Message(MessageType.STATE, "m", 0,
                JsonNodeFactory.instance.objectNode().put("pad", "x".repeat(60_000)));
        AtomicLong slowestNanos = new AtomicLong();
        List<Boolean> accepted = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < PeerConnection.SEND_QUEUE_CAPACITY * 4 && connection.isOpen(); i++) {
                long start = System.nanoTime();
                accepted.add(connection.send(big));
                slowestNanos.accumulateAndGet(System.nanoTime() - start, Math::max);
            }
        });

        assertEquals(CloseReason.SEND_BACKLOG, recorder.nextClose());
        assertFalse(accepted.get(accepted.size() - 1));
        assertTrue(slowestNanos.get() < TimeUnit.MILLISECONDS.toNanos(100),
                "send 한 번이 " + slowestNanos.get() / 1_000_000 + "ms 동안 EDT를 막았다");
        assertTrue(connection.awaitTermination(Duration.ofSeconds(WAIT_SECONDS)));
    }
    //#endregion
}
