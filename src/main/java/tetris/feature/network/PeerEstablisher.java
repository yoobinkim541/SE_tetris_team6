package tetris.feature.network;

import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.protocol.ProtocolException;

/**
 * 호스트 소켓 대기, 클라이언트 소켓 접속, HELLO 역할 핸드셰이크, 타임아웃·취소를 담당한다 (S8-C1).
 *
 * <ul>
 *   <li>소켓 accept·connect와 읽기·쓰기는 데몬 스레드에서 수행하고, 결과 완료는 주입한 dispatcher에서 수행한다.</li>
 *   <li>각 {@link Attempt}는 고유한 {@code settled} 상태를 가져 취소·타임아웃 뒤 늦게 끝난 작업이
 *       이후 시도의 연결에 영향을 주지 않는다.</li>
 * </ul>
 */
public final class PeerEstablisher {
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();
    private static final ScheduledExecutorService TIMER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "peer-establish-timer");
                thread.setDaemon(true);
                return thread;
            });

    private final Executor dispatcher;
    private final Duration timeout;

    public PeerEstablisher(Executor dispatcher) {
        this(dispatcher, NetworkDefaults.CONNECT_TIMEOUT);
    }

    public PeerEstablisher(Executor dispatcher, Duration timeout) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
    }

    /** 지정 포트(0이면 임의 빈 포트)로 1회 접속을 기다린다. 포트 충돌은 즉시 실패한다 */
    public Attempt host(int port) {
        if (port < 0 || port > 65535) {
            return Attempt.failedImmediately(
                    new EstablishException(EstablishFailure.INVALID_ADDRESS, "port " + port));
        }
        ServerSocket server = new ServerSocketUnbound().create();
        if (server == null) {
            return Attempt.failedImmediately(
                    new EstablishException(EstablishFailure.IO_ERROR, "failed to open ServerSocket"));
        }
        try {
            server.setReuseAddress(false);
            InetAddress wildcardIpv4 = InetAddress.getByAddress(new byte[] {0, 0, 0, 0});
            server.bind(new InetSocketAddress(wildcardIpv4, port), 1);
        } catch (BindException e) {
            closeQuietly(server);
            return Attempt.failedImmediately(
                    new EstablishException(EstablishFailure.PORT_IN_USE, String.valueOf(e.getMessage()), e));
        } catch (IOException e) {
            closeQuietly(server);
            return Attempt.failedImmediately(
                    new EstablishException(EstablishFailure.IO_ERROR, String.valueOf(e.getMessage()), e));
        }

        int boundPort = server.getLocalPort();
        AttemptState state = new AttemptState(dispatcher, timeout, PeerRole.HOST, boundPort, server, null);
        startDaemon("peer-accept-" + WORKER_IDS.incrementAndGet(), () -> acceptOnce(state));
        return new Attempt(state);
    }

    /** 검증된 IPv4 주소와 포트로 접속을 시작한다. 주소 형식 오류는 스레드 없이 즉시 실패한다 */
    public Attempt connect(String address, int port) {
        PeerAddress parsed;
        InetAddress target;
        try {
            parsed = PeerAddress.parse(address, port);
            target = parsed.toInetAddress();
        } catch (EstablishException e) {
            return Attempt.failedImmediately(e);
        }

        Socket socket = new Socket();
        AttemptState state = new AttemptState(dispatcher, timeout, PeerRole.CLIENT, -1, null, socket);
        InetSocketAddress endpoint = new InetSocketAddress(target, parsed.port());
        startDaemon("peer-connect-" + WORKER_IDS.incrementAndGet(), () -> connectOnce(state, endpoint));
        return new Attempt(state);
    }

    private void acceptOnce(AttemptState state) {
        Socket accepted;
        try {
            accepted = state.serverSocket.accept();
        } catch (IOException e) {
            closeQuietly(state.serverSocket);
            state.failFromWorker(EstablishFailure.IO_ERROR, String.valueOf(e.getMessage()), e);
            return;
        }
        // 첫 접속 직후 서버 소켓을 닫아 두 번째 접속을 막는다
        closeQuietly(state.serverSocket);
        state.beginHandshake(accepted);
    }

    private void connectOnce(AttemptState state, InetSocketAddress endpoint) {
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeout.toMillis()));
        try {
            state.rawSocket.connect(endpoint, timeoutMillis);
        } catch (SocketTimeoutException e) {
            closeQuietly(state.rawSocket);
            state.failFromWorker(EstablishFailure.TIMEOUT, "connect timed out", e);
            return;
        } catch (ConnectException e) {
            closeQuietly(state.rawSocket);
            state.failFromWorker(EstablishFailure.REFUSED, String.valueOf(e.getMessage()), e);
            return;
        } catch (IOException e) {
            closeQuietly(state.rawSocket);
            state.failFromWorker(EstablishFailure.IO_ERROR, String.valueOf(e.getMessage()), e);
            return;
        }
        state.beginHandshake(state.rawSocket);
    }

    private static void startDaemon(String name, Runnable task) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // 이미 닫힌 소켓
        }
    }

    private static final class ServerSocketUnbound {
        ServerSocket create() {
            try {
                return new ServerSocket();
            } catch (IOException e) {
                return null;
            }
        }
    }

    /** 진행 중인 연결 수립 시도 핸들 */
    public static final class Attempt {
        private final CompletableFuture<PeerLink> future;
        private final int localPort;
        private final AttemptState state;

        private Attempt(AttemptState state) {
            this.future = state.future;
            this.localPort = state.localPort;
            this.state = state;
        }

        private Attempt(CompletableFuture<PeerLink> failedFuture) {
            this.future = failedFuture;
            this.localPort = -1;
            this.state = null;
        }

        static Attempt failedImmediately(EstablishException error) {
            CompletableFuture<PeerLink> failed = new CompletableFuture<>();
            failed.completeExceptionally(error);
            return new Attempt(failed);
        }

        public CompletableFuture<PeerLink> result() {
            return future;
        }

        /** 호스트 시도가 바인드한 로컬 포트. 클라이언트 시도나 즉시 실패는 -1 */
        public int localPort() {
            return localPort;
        }

        /** 진행 중인 시도를 취소하고 소켓·연결 자원을 닫는다 (반복 호출 무해) */
        public void cancel() {
            if (state != null) {
                state.cancel();
            }
        }
    }

    private static final class AttemptState implements PeerListener {
        private final Object lock = new Object();
        private final Executor dispatcher;
        private final Duration timeout;
        private final PeerRole role;
        private final int localPort;
        private final ServerSocket serverSocket;
        private final CompletableFuture<PeerLink> future = new CompletableFuture<>();
        private final AtomicBoolean settled = new AtomicBoolean(false);

        private Socket rawSocket;
        private PeerConnection connection;
        private ScheduledFuture<?> timeoutFuture;
        /** dispatcher 스레드에서만 접근한다 */
        private PeerLink establishedLink;

        AttemptState(
                Executor dispatcher,
                Duration timeout,
                PeerRole role,
                int localPort,
                ServerSocket serverSocket,
                Socket rawSocket) {
            this.dispatcher = dispatcher;
            this.timeout = timeout;
            this.role = role;
            this.localPort = localPort;
            this.serverSocket = serverSocket;
            this.rawSocket = rawSocket;
        }

        void beginHandshake(Socket connectedSocket) {
            PeerConnection started;
            synchronized (lock) {
                if (settled.get()) {
                    closeQuietly(connectedSocket);
                    return;
                }
                this.rawSocket = connectedSocket;
                try {
                    started = PeerConnection.start(connectedSocket, dispatcher, this);
                } catch (RuntimeException e) {
                    closeQuietly(connectedSocket);
                    failFromWorker(EstablishFailure.IO_ERROR, String.valueOf(e.getMessage()), e);
                    return;
                }
                this.connection = started;
                this.timeoutFuture = TIMER.schedule(
                        this::onHandshakeTimeout,
                        Math.max(1L, timeout.toNanos()),
                        TimeUnit.NANOSECONDS);
            }
            started.send(ControlPayloads.Hello.message(role));
        }

        private void onHandshakeTimeout() {
            failFromWorker(EstablishFailure.TIMEOUT, "HELLO handshake timed out", null);
        }

        void failFromWorker(EstablishFailure reason, String detail, Throwable cause) {
            if (settled.get()) return;
            try {
                dispatcher.execute(() -> failOnDispatcher(reason, detail, cause));
            } catch (RuntimeException rejected) {
                synchronized (lock) {
                    if (settled.compareAndSet(false, true)) {
                        closeResourcesLocked();
                    }
                }
            }
        }

        private void failOnDispatcher(EstablishFailure reason, String detail, Throwable cause) {
            boolean won;
            synchronized (lock) {
                won = settled.compareAndSet(false, true);
                if (won) {
                    closeResourcesLocked();
                }
            }
            if (won) {
                EstablishException ex = (cause == null)
                        ? new EstablishException(reason, detail)
                        : new EstablishException(reason, detail, cause);
                future.completeExceptionally(ex);
            }
        }

        void cancel() {
            boolean won;
            synchronized (lock) {
                won = settled.compareAndSet(false, true);
                if (won) {
                    closeResourcesLocked();
                }
            }
            if (won) {
                future.cancel(false);
            }
        }

        @Override
        public void onMessage(Message message) {
            if (establishedLink != null) {
                establishedLink.deliverMessage(message);
                return;
            }
            if (message.type() != MessageType.HELLO) {
                failOnDispatcher(EstablishFailure.PROTOCOL_ERROR, "expected HELLO but got " + message.type(), null);
                return;
            }
            ControlPayloads.Hello hello;
            try {
                hello = ControlPayloads.Hello.from(message.payload());
            } catch (ProtocolException e) {
                failOnDispatcher(EstablishFailure.PROTOCOL_ERROR, e.getMessage(), e);
                return;
            }
            if (hello.role() == role) {
                failOnDispatcher(EstablishFailure.PROTOCOL_ERROR, "peer sent same role " + hello.role(), null);
                return;
            }

            PeerConnection conn;
            String peerAddress;
            synchronized (lock) {
                if (!settled.compareAndSet(false, true)) {
                    if (connection != null) {
                        connection.close();
                    }
                    return;
                }
                cancelTimeoutLocked();
                conn = this.connection;
                peerAddress = this.rawSocket.getInetAddress().getHostAddress();
            }
            PeerLink link = new PeerLink(role, peerAddress, conn);
            this.establishedLink = link;
            future.complete(link);
        }

        @Override
        public void onClosed(CloseReason reason, String detail) {
            if (establishedLink != null) {
                establishedLink.deliverClosed(reason, detail);
                return;
            }
            EstablishFailure mapped = (reason == CloseReason.PROTOCOL_ERROR)
                    ? EstablishFailure.PROTOCOL_ERROR
                    : EstablishFailure.IO_ERROR;
            failOnDispatcher(mapped, detail, null);
        }

        private void cancelTimeoutLocked() {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
                timeoutFuture = null;
            }
        }

        private void closeResourcesLocked() {
            cancelTimeoutLocked();
            closeQuietly(serverSocket);
            if (connection != null) {
                connection.close();
            } else {
                closeQuietly(rawSocket);
            }
        }
    }
}
