package tetris.feature.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.FrameDecoder;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.ProtocolException;

/**
 * 연결된 TCP 소켓 하나 위의 메시지 송수신 (S7-C2).
 *
 * <ul>
 *   <li>수신 스레드 1개가 바이트를 읽어 {@link FrameDecoder}로 메시지를 꺼내고, 송신 스레드 1개가
 *       {@link FrameEncoder}로 직렬화해 쓴다. 블로킹 I/O와 직렬화는 호출 스레드(EDT)에서 하지 않는다.</li>
 *   <li>{@link #send}는 대기열에 넣기만 한다. 상대가 읽지 않아 대기열이 가득 차면 기다리지 않고
 *       {@link CloseReason#SEND_BACKLOG}로 닫는다.</li>
 *   <li>받은 메시지·종료 통지는 주입한 dispatcher로 넘긴다. dispatcher는 넘긴 순서대로 실행해야 한다(EDT, 단일 스레드).</li>
 *   <li>이 객체 하나가 연결 한 세대다. {@link #close()} 뒤에는 이미 받아 dispatcher 큐에 있던 메시지도 넘기지 않는다.</li>
 * </ul>
 */
public final class PeerConnection {
    /** 송신 대기열 길이. STATE 30Hz 기준 약 8초 분량이며, 넘치면 느린 상대로 보고 닫는다 */
    public static final int SEND_QUEUE_CAPACITY = 256;
    private static final int READ_BUFFER_BYTES = 8 * 1024;
    /** 프로토콜 오류 뒤 ERROR를 보내는 데 쓸 최대 시간. 상대가 읽지 않아도 연결을 붙잡지 않는다 */
    private static final long ERROR_FLUSH_MILLIS = 1_000;
    private static final AtomicInteger IDS = new AtomicInteger();

    /** 송신 스레드에 보내는 마지막 작업: ERROR를 쓰고 닫는다 */
    private record FailAndClose(Message error, CloseReason reason, String detail) {
    }

    private static final Object STOP = new Object();

    private final Socket socket;
    private final Executor dispatcher;
    private final PeerListener listener;
    private final BlockingQueue<Object> outbox = new ArrayBlockingQueue<>(SEND_QUEUE_CAPACITY + 1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean closedLocally;
    private final Thread reader;
    private final Thread writer;

    private PeerConnection(Socket socket, Executor dispatcher, PeerListener listener) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.listener = listener;
        int id = IDS.incrementAndGet();
        this.reader = new Thread(this::readLoop, "peer-" + id + "-reader");
        this.writer = new Thread(this::writeLoop, "peer-" + id + "-writer");
        reader.setDaemon(true);
        writer.setDaemon(true);
    }

    /** 이미 연결된 소켓으로 송수신을 시작한다. 이후 소켓은 이 객체가 소유하고 닫는다 */
    public static PeerConnection start(Socket socket, Executor dispatcher, PeerListener listener) {
        Objects.requireNonNull(socket, "socket");
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(listener, "listener");
        if (!socket.isConnected() || socket.isClosed()) throw new IllegalArgumentException("socket is not connected");
        PeerConnection connection = new PeerConnection(socket, dispatcher, listener);
        try {
            socket.setTcpNoDelay(true); // 입력·STATE 프레임을 모아 보내느라 지연시키지 않는다
        } catch (IOException e) {
            // 지연 최적화만 못 할 뿐 송수신은 가능하다
        }
        connection.reader.start();
        connection.writer.start();
        return connection;
    }

    /** 송신 대기열에 넣는다. 닫힌 연결이거나 대기열이 넘쳐 연결을 닫았으면 false */
    public boolean send(Message message) {
        Objects.requireNonNull(message, "message");
        if (closed.get()) return false;
        if (outbox.size() >= SEND_QUEUE_CAPACITY || !outbox.offer(message)) {
            shutdown(CloseReason.SEND_BACKLOG, "send queue full (" + SEND_QUEUE_CAPACITY + ")");
            return false;
        }
        return true;
    }

    /** 연결을 닫는다. 여러 번 불러도 된다. 처음 한 번만 {@link CloseReason#LOCAL}을 알린다 */
    public void close() {
        closedLocally = true;
        shutdown(CloseReason.LOCAL, "closed locally");
    }

    public boolean isOpen() {
        return !closed.get();
    }

    /** 수신·송신 스레드가 끝날 때까지 기다린다. 제한 시간 안에 끝나면 true */
    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (Thread thread : List.of(reader, writer)) {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(thread, remaining);
        }
        return !reader.isAlive() && !writer.isAlive();
    }

    private void readLoop() {
        FrameDecoder decoder = new FrameDecoder();
        byte[] buffer = new byte[READ_BUFFER_BYTES];
        try {
            InputStream in = socket.getInputStream();
            int read;
            while ((read = in.read(buffer)) != -1) {
                for (Message message : decoder.feed(buffer, 0, read)) deliver(message);
            }
            shutdown(CloseReason.PEER_CLOSED, "end of stream");
        } catch (ProtocolException e) {
            failAndClose(e);
        } catch (IOException e) {
            shutdown(CloseReason.IO_ERROR, String.valueOf(e.getMessage()));
        }
    }

    private void writeLoop() {
        FrameEncoder encoder = new FrameEncoder();
        try {
            OutputStream out = socket.getOutputStream();
            while (true) {
                Object item = outbox.take();
                if (item == STOP) return;
                if (item instanceof FailAndClose fail) {
                    out.write(encoder.encode(fail.error()));
                    out.flush();
                    shutdown(fail.reason(), fail.detail());
                    return;
                }
                out.write(encoder.encode((Message) item));
                out.flush();
            }
        } catch (ProtocolException e) {
            shutdown(CloseReason.PROTOCOL_ERROR, "outbound " + e.getMessage());
        } catch (IOException e) {
            shutdown(CloseReason.IO_ERROR, String.valueOf(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdown(CloseReason.IO_ERROR, "writer interrupted");
        }
    }

    private void deliver(Message message) {
        dispatcher.execute(() -> {
            if (!closedLocally) listener.onMessage(message);
        });
    }

    /** 상대의 위반: 더 읽지 않고, 대기 중인 송신을 버린 뒤 ERROR 하나만 보내고 닫는다 */
    private void failAndClose(ProtocolException e) {
        if (closed.get()) return;
        Message error = new ControlPayloads.Failure(e.code(), String.valueOf(e.getMessage())).message();
        outbox.clear();
        if (!outbox.offer(new FailAndClose(error, CloseReason.PROTOCOL_ERROR, e.getMessage()))) {
            shutdown(CloseReason.PROTOCOL_ERROR, e.getMessage());
            return;
        }
        try {
            writer.join(ERROR_FLUSH_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        shutdown(CloseReason.PROTOCOL_ERROR, e.getMessage()); // 상대가 읽지 않아 ERROR를 못 썼어도 닫는다
    }

    private void shutdown(CloseReason reason, String detail) {
        if (!closed.compareAndSet(false, true)) return;
        try {
            socket.close(); // 블로킹 read·write를 깨운다
        } catch (IOException ignored) {
            // 이미 닫힌 소켓
        }
        outbox.clear();
        outbox.offer(STOP);
        dispatcher.execute(() -> listener.onClosed(reason, detail));
    }
}
