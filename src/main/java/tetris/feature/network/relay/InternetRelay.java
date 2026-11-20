package tetris.feature.network.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import tetris.feature.network.protocol.WireFormat;

/**
 * 인터넷 대전 중계 접속 하나 (방 코드). 다른 네트워크의 두 PC가 중계 서버에서 만난다.
 *
 * <p>게임 연결은 지금과 같은 TCP를 그대로 쓴다. 이 객체가 이 PC의 루프백 소켓과 중계 서버 WebSocket 사이를 이어서,
 * 게임 쪽에서는 같은 PC의 상대와 연결된 것처럼 보인다(HELLO·heartbeat·호스트 권위는 그대로).
 * <ul>
 *   <li>호스트: 게임이 연 대기 포트를 넘기면 방 코드를 받는다. 상대가 참가하면 그 포트로 접속해 잇는다</li>
 *   <li>참가자: 방 코드로 참가하면 이 PC의 루프백 포트를 받는다. 게임은 127.0.0.1:그 포트로 접속한다</li>
 *   <li>짝이 지어진 뒤 어느 쪽이 끊겨도 반대쪽을 닫는다. 게임은 상대가 끊긴 것으로 처리한다</li>
 * </ul>
 * 결과 stage는 주입한 dispatcher(앱은 EDT)에서 완료된다. 한 객체는 한 번만 쓴다. 중계 서버 코드와 배포: {@code relay/README.md}
 */
public final class InternetRelay implements AutoCloseable {
    /**
     * 기본 중계 서버. Cloudflare 터널 뒤에 있어 서버로 들어오는 포트를 열지 않고, 443(HTTPS)만 열린 네트워크에서도 접속된다
     */
    public static final URI DEFAULT_URI = URI.create("wss://tetris.ybkim.pe.kr/relay");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    /** 다른 중계 서버를 쓸 때의 시스템 속성 (예: -Dtetris.relay=ws://127.0.0.1:8765/relay) */
    public static final String URL_PROPERTY = "tetris.relay";
    static final int RELAY_VERSION = 1;
    private static final int ACCEPT_TIMEOUT_MILLIS = 10_000;
    private static final int BUFFER_BYTES = 8 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI uri;
    private final Executor dispatcher;
    private final RelayTransport transport;
    private final Object lock = new Object();
    private final ArrayDeque<byte[]> pendingToGame = new ArrayDeque<>();
    private RelayTransport.Channel channel;
    private Socket gameSocket;
    private ServerSocket loopbackServer;
    private CompletableFuture<String> roomCode;
    private CompletableFuture<Integer> loopbackPort;
    private int hostGamePort = -1;
    private boolean started;
    private boolean paired;
    private boolean closed;

    /** 시스템 속성이 있으면 그 주소, 없으면 기본 중계 서버 */
    public static URI configuredUri() {
        String value = System.getProperty(URL_PROPERTY);
        return value == null || value.isBlank() ? DEFAULT_URI : URI.create(value.trim());
    }

    public InternetRelay(URI uri, Executor dispatcher) {
        this(uri, dispatcher, new JdkRelayTransport());
    }

    public InternetRelay(URI uri, Executor dispatcher, RelayTransport transport) {
        this.uri = Objects.requireNonNull(uri, "uri");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /** 호스트: 게임이 대기 중인 포트를 넘기고 방 코드(4자리)를 받는다 */
    public CompletionStage<String> openRoom(int localGamePort) {
        if (localGamePort <= 0 || localGamePort > 65535) {
            throw new IllegalArgumentException("game port must be 1..65535: " + localGamePort);
        }
        CompletableFuture<String> result = new CompletableFuture<>();
        synchronized (lock) {
            claimStart();
            roomCode = result;
            hostGamePort = localGamePort;
        }
        connect(request("host", null));
        return onDispatcher(result);
    }

    /** 참가자: 방 코드로 참가하고, 게임이 접속할 이 PC의 루프백 포트를 받는다 */
    public CompletionStage<Integer> join(String code) {
        String trimmed = code == null ? "" : code.trim();
        if (!trimmed.matches("\\d{4}")) {
            throw new IllegalArgumentException("room code must be 4 digits");
        }
        CompletableFuture<Integer> result = new CompletableFuture<>();
        synchronized (lock) {
            claimStart();
            loopbackPort = result;
        }
        connect(request("join", trimmed));
        return onDispatcher(result);
    }

    private void claimStart() {
        if (closed) throw new IllegalStateException("relay is closed");
        if (started) throw new IllegalStateException("relay already used");
        started = true;
    }

    private static String request(String op, String code) {
        ObjectNode node = JSON.createObjectNode();
        node.put("op", op);
        node.put("relay", RELAY_VERSION);
        node.put("proto", WireFormat.VERSION); // 게임 버전이 다르면 서버가 참가 단계에서 거부한다
        if (code != null) node.put("code", code);
        return node.toString();
    }

    private void connect(String firstMessage) {
        transport.open(uri, CONNECT_TIMEOUT, new Listener()).whenComplete((opened, error) -> {
            if (error != null) {
                fail(RelayException.Reason.UNREACHABLE, String.valueOf(error.getMessage()));
                return;
            }
            synchronized (lock) {
                if (closed) {
                    opened.close();
                    return;
                }
                channel = opened;
            }
            opened.sendText(firstMessage).whenComplete((ignored, sendError) -> {
                if (sendError != null) fail(RelayException.Reason.UNREACHABLE, String.valueOf(sendError.getMessage()));
            });
        });
    }

    private final class Listener implements RelayTransport.Listener {
        @Override
        public void onText(String text) {
            JsonNode message;
            try {
                message = JSON.readTree(text);
            } catch (IOException e) {
                fail(RelayException.Reason.BAD_REQUEST, "unreadable server message");
                return;
            }
            switch (message.path("op").asText()) {
                case "room" -> {
                    CompletableFuture<String> code;
                    synchronized (lock) {
                        code = roomCode;
                    }
                    if (code != null) code.complete(message.path("code").asText());
                }
                case "paired" -> onPaired();
                case "error" -> fail(RelayException.Reason.fromServer(message.path("reason").asText()), null);
                default -> {
                    // 모르는 제어 메시지는 무시한다 (서버가 나중에 늘려도 끊기지 않게)
                }
            }
        }

        @Override
        public void onBinary(byte[] bytes) {
            synchronized (lock) {
                if (closed) return;
                if (gameSocket == null) {
                    pendingToGame.addLast(bytes); // 게임 연결이 열리기 전에 온 바이트는 순서대로 모아 둔다
                    return;
                }
                if (!writeToGame(bytes)) closeLater();
            }
        }

        @Override
        public void onClosed(String reason) {
            boolean beforePair;
            synchronized (lock) {
                beforePair = !paired;
            }
            if (beforePair) fail(RelayException.Reason.CLOSED, reason);
            else close();
        }
    }

    private void onPaired() {
        boolean host;
        synchronized (lock) {
            if (closed || paired) return;
            paired = true;
            host = hostGamePort > 0;
        }
        if (host) {
            Thread.ofPlatform().daemon().name("relay-connect-game").start(this::connectToGame);
        } else {
            openLoopback();
        }
    }

    /** 호스트: 상대가 참가했으니 이 PC의 게임 대기 포트로 접속한다 */
    private void connectToGame() {
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), hostGamePort), ACCEPT_TIMEOUT_MILLIS);
        } catch (IOException e) {
            closeQuietly(socket);
            close();
            return;
        }
        attachGame(socket);
    }

    /** 참가자: 게임이 접속할 루프백 포트를 열고 알려 준다. 게임 접속 하나만 받는다 */
    private void openLoopback() {
        ServerSocket server;
        try {
            server = new ServerSocket();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
            server.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);
        } catch (IOException e) {
            fail(RelayException.Reason.CLOSED, "loopback port unavailable");
            return;
        }
        CompletableFuture<Integer> port;
        synchronized (lock) {
            if (closed) {
                closeQuietly(server);
                return;
            }
            loopbackServer = server;
            port = loopbackPort;
        }
        Thread.ofPlatform().daemon().name("relay-accept-game").start(() -> {
            try {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true);
                attachGame(socket);
            } catch (IOException e) {
                close(); // 게임이 제한 시간 안에 접속하지 않았거나 닫았다
            } finally {
                closeQuietly(server);
            }
        });
        port.complete(server.getLocalPort());
    }

    private void attachGame(Socket socket) {
        synchronized (lock) {
            if (closed) {
                closeQuietly(socket);
                return;
            }
            gameSocket = socket;
            while (!pendingToGame.isEmpty()) {
                if (!writeToGame(pendingToGame.removeFirst())) {
                    closeLater();
                    return;
                }
            }
        }
        Thread.ofPlatform().daemon().name("relay-uplink").start(() -> pumpGameToRelay(socket));
    }

    /** 게임 → 중계: 받은 만큼 보내고 전송이 끝나야 다음을 읽는다 (WebSocket은 동시에 두 개를 보낼 수 없다) */
    private void pumpGameToRelay(Socket socket) {
        byte[] buffer = new byte[BUFFER_BYTES];
        try {
            InputStream in = socket.getInputStream();
            int read;
            while ((read = in.read(buffer)) != -1) {
                RelayTransport.Channel current;
                synchronized (lock) {
                    if (closed) return;
                    current = channel;
                }
                current.sendBinary(Arrays.copyOf(buffer, read)).join();
            }
        } catch (IOException | RuntimeException ignored) {
            // 게임이 닫았거나 중계가 끊겼다
        } finally {
            close();
        }
    }

    /** lock 안에서 부른다 */
    private boolean writeToGame(byte[] bytes) {
        try {
            OutputStream out = gameSocket.getOutputStream();
            out.write(bytes);
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** lock을 쥔 채로 닫지 않도록 다른 스레드에서 닫는다 */
    private void closeLater() {
        Thread.ofPlatform().daemon().name("relay-close").start(this::close);
    }

    private void fail(RelayException.Reason reason, String detail) {
        RelayException error = new RelayException(reason, detail);
        CompletableFuture<?> pending;
        synchronized (lock) {
            pending = roomCode != null && !roomCode.isDone() ? roomCode : loopbackPort;
        }
        if (pending != null) pending.completeExceptionally(error);
        close();
    }

    private <T> CompletionStage<T> onDispatcher(CompletableFuture<T> source) {
        CompletableFuture<T> result = new CompletableFuture<>();
        source.whenComplete((value, error) -> dispatcher.execute(() -> {
            if (error != null) result.completeExceptionally(error);
            else result.complete(value);
        }));
        return result;
    }

    /** 여러 번 불러도 된다. 짝이 지어지기 전이면 대기 중인 결과는 CLOSED로 끝난다 */
    @Override
    public void close() {
        RelayTransport.Channel toClose;
        Socket socket;
        ServerSocket server;
        CompletableFuture<String> code;
        CompletableFuture<Integer> port;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            toClose = channel;
            socket = gameSocket;
            server = loopbackServer;
            code = roomCode;
            port = loopbackPort;
            pendingToGame.clear();
        }
        RelayException closedError = new RelayException(RelayException.Reason.CLOSED, null);
        if (code != null) code.completeExceptionally(closedError);
        if (port != null) port.completeExceptionally(closedError);
        if (toClose != null) toClose.close();
        closeQuietly(socket);
        closeQuietly(server);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // 이미 닫혔다
        }
    }
}
