package tetris.feature.network.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * 두 PC 사이에 끼워 지연을 주입하는 TCP 중계기 (S11-C1, R3-LATENCY).
 *
 * <ul>
 *   <li>첫 접속 하나만 상대한다. 접속이 오면 대상 주소로 연결하고 방향마다 중계 스레드 1개를 띄운다.</li>
 *   <li>각 청크는 <b>받은 시각 + {@code delayMillis}</b> 이후에 전달한다. 보낸 시각부터 세지 않으므로
 *       왕복 지연은 양방향 지연의 합이 된다.</li>
 *   <li>{@code stallAfterMillis ≥ 0}이면 중계를 시작한 시각부터 그 시간이 지난 뒤 받은 데이터는 전달하지 않는다.
 *       소켓은 그대로 두어 "연결은 살아 있지만 화면이 멈춘" 상태를 재현한다.</li>
 *   <li>한쪽이 EOF·오류로 끝나면 양쪽 소켓을 닫아 반대쪽 스레드도 끝낸다.</li>
 * </ul>
 */
public final class LagProxy implements AutoCloseable {
    private static final int BUFFER_BYTES = 8 * 1024;

    private final int listenPort;
    private final InetSocketAddress target;
    private final long delayMillis;
    private final long stallAfterMillis;

    private final Object lock = new Object();
    private final List<Thread> threads = new ArrayList<>();
    private ServerSocket server;
    private Socket inbound;
    private Socket outbound;
    private Thread acceptThread;
    private volatile boolean closed;
    private volatile long startedAtNanos;

    /**
     * @param listenPort 중계기가 접속을 받을 포트. 0이면 임의 빈 포트
     * @param target 실제로 연결할 대상 주소
     * @param delayMillis 방향별 전달 지연(ms). 0 이상
     * @param stallAfterMillis 중계 시작 뒤 이 시간(ms)이 지나면 전달을 멈춘다. 음수면 멈추지 않는다
     */
    public LagProxy(int listenPort, InetSocketAddress target, long delayMillis, long stallAfterMillis) {
        if (listenPort < 0 || listenPort > 65535) {
            throw new IllegalArgumentException("listenPort out of range: " + listenPort);
        }
        this.target = Objects.requireNonNull(target, "target");
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must not be negative: " + delayMillis);
        }
        if (stallAfterMillis < -1) {
            throw new IllegalArgumentException("stallAfterMillis must be -1 or more: " + stallAfterMillis);
        }
        this.listenPort = listenPort;
        this.delayMillis = delayMillis;
        this.stallAfterMillis = stallAfterMillis;
    }

    /** 중계기가 실제로 열어 둔 포트. {@link #start()} 전에는 -1 */
    public int localPort() {
        synchronized (lock) {
            return server == null ? -1 : server.getLocalPort();
        }
    }

    /** 접속 대기를 시작한다. 호출 스레드는 곧바로 돌아온다 */
    public void start() throws IOException {
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(listenPort), 1);
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
        synchronized (lock) {
            if (closed) {
                closeQuietly(socket);
                throw new IOException("LagProxy is already closed");
            }
            this.server = socket;
            this.acceptThread = newDaemonThread("lag-proxy-accept", this::acceptOnce);
        }
        startedAtNanos = System.nanoTime();
        acceptThread.start();
    }

    private void acceptOnce() {
        Socket client;
        Socket remote;
        try {
            client = server.accept();
        } catch (IOException e) {
            return; // close()가 서버 소켓을 닫은 정상 종료
        }
        try {
            remote = new Socket();
            remote.connect(target, 5_000);
        } catch (IOException e) {
            closeQuietly(client);
            return;
        }
        try {
            client.setTcpNoDelay(true);
            remote.setTcpNoDelay(true);
        } catch (IOException ignored) {
            // 중계 자체는 계속한다
        }
        synchronized (lock) {
            if (closed) {
                closeQuietly(client);
                closeQuietly(remote);
                return;
            }
            this.inbound = client;
            this.outbound = remote;
            Thread upstream = newDaemonThread("lag-proxy-upstream", () -> pump(client, remote));
            Thread downstream = newDaemonThread("lag-proxy-downstream", () -> pump(remote, client));
            threads.add(upstream);
            threads.add(downstream);
            upstream.start();
            downstream.start();
        }
        closeQuietly(server);
    }

    private void pump(Socket source, Socket sink) {
        byte[] buffer = new byte[BUFFER_BYTES];
        try (InputStream in = source.getInputStream(); OutputStream out = sink.getOutputStream()) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (closed) return;
                long stallAt = stallAfterNanos();
                if (stallAt >= 0) {
                    long now = System.nanoTime();
                    if (now - startedAtNanos >= stallAt) {
                        // 전달을 멈춘다: 읽은 바이트를 버리고 연결은 유지한다
                        continue;
                    }
                    // 정지 시각 전에 보낸 청크가 정지 뒤에 도착하지 않도록 정지 시각까지만 기다린다
                    awaitUntil(Math.min(now + delayNanos(), startedAtNanos + stallAt));
                } else {
                    awaitUntil(System.nanoTime() + delayNanos());
                }
                if (closed) return;
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException ignored) {
            // 상대가 닫히면 아래에서 양쪽을 닫는다
        } finally {
            closeBoth();
        }
    }

    private long delayNanos() {
        return delayMillis * 1_000_000L;
    }

    private long stallAfterNanos() {
        return stallAfterMillis < 0 ? -1 : stallAfterMillis * 1_000_000L;
    }

    private static void awaitUntil(long deadlineNanos) {
        long remaining;
        while ((remaining = deadlineNanos - System.nanoTime()) > 0) {
            if (Thread.currentThread().isInterrupted()) return;
            LockSupport.parkNanos(Math.min(remaining, 1_000_000L));
        }
    }

    private void closeBoth() {
        synchronized (lock) {
            closeQuietly(inbound);
            closeQuietly(outbound);
        }
    }

    /** 중계 스레드를 멈추고 소켓을 모두 닫는다 (반복 호출 무해) */
    @Override
    public void close() {
        List<Thread> snapshot;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            closeQuietly(server);
            closeQuietly(inbound);
            closeQuietly(outbound);
            snapshot = new ArrayList<>(threads);
            if (acceptThread != null) snapshot.add(acceptThread);
        }
        for (Thread thread : snapshot) {
            if (thread != Thread.currentThread()) {
                thread.interrupt();
            }
        }
        for (Thread thread : snapshot) {
            if (thread == Thread.currentThread()) continue;
            try {
                thread.join(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static Thread newDaemonThread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // 이미 닫힌 자원
        }
    }
}
