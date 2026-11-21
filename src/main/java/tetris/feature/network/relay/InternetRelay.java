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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import tetris.feature.network.protocol.WireFormat;
import tetris.relay.LobbyCode;
import tetris.relay.RelayServer;

/**
 * 인터넷 대전 중계 접속 하나 (방 코드). 다른 네트워크의 두 PC가 중계 서버에서 만난다.
 *
 * <p>게임 연결은 지금과 같은 TCP를 그대로 쓴다. 이 객체가 이 PC의 루프백 소켓과 중계 서버 사이를 이어서,
 * 게임 쪽에서는 같은 PC의 상대와 연결된 것처럼 보인다(HELLO·heartbeat·호스트 권위는 그대로).
 * <ul>
 *   <li>중계 서버는 두 곳이다. 메인({@link #MAIN_URI}, 공인 IP로 바로 붙는 TCP 중계, 6글자 코드)과
 *       예비({@link #FALLBACK_URI}, Cloudflare를 거치는 WebSocket 중계, 숫자 4자리 코드)</li>
 *   <li>호스트: 메인부터 방을 만들어 보고, 방 코드를 받기 전에 실패하면 예비 서버로 넘어간다</li>
 *   <li>참가자: 코드 모양으로 서버를 고른다(6글자 = 메인, 숫자 4자리 = 예비). 그래서 두 사람이 늘 같은 서버에서 만난다</li>
 *   <li>짝이 지어진 뒤 어느 쪽이 끊겨도 반대쪽을 닫는다. 게임은 상대가 끊긴 것으로 처리한다</li>
 * </ul>
 * 결과 stage는 주입한 dispatcher(앱은 EDT)에서 완료된다. 한 객체는 한 번만 쓴다.
 * 예비 서버 코드와 배포: {@code relay/README.md}, 메인 서버: {@code tetris.relay.RelayServer}
 */
public final class InternetRelay implements AutoCloseable {
    /** 메인 서버 주소를 바꿀 때의 시스템 속성 (예: -Dtetris.relay=127.0.0.1:47123) */
    public static final String MAIN_PROPERTY = "tetris.relay";
    /** 예비 서버 주소를 바꿀 때의 시스템 속성 (예: -Dtetris.relay.fallback=ws://127.0.0.1:8765/relay) */
    public static final String FALLBACK_PROPERTY = "tetris.relay.fallback";
    /** 메인: 오라클 클라우드 VM의 TCP 중계. 왕복 수십 ms */
    public static final URI MAIN_URI = URI.create("tcp://158.179.174.70:" + RelayServer.DEFAULT_PORT);
    /** 예비: hermes 서버의 WebSocket 중계(Cloudflare 터널). 메인이 안 될 때만 쓴다 */
    public static final URI FALLBACK_URI = URI.create("wss://tetris.ybkim.pe.kr/relay");
    static final int RELAY_VERSION = 1;
    private static final int ACCEPT_TIMEOUT_MILLIS = 10_000;
    private static final int BUFFER_BYTES = 8 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 중계 서버 하나.
     *
     * @param normalizeCode 사람이 입력한 코드를 이 서버 형식으로 바꾼다. 이 서버 코드가 아니면 빈 값
     * @param displayCode   서버가 준 코드를 화면에 보일 모양으로
     */
    public record Route(String name, URI uri, Duration connectTimeout, RelayTransport transport,
                        Function<String, Optional<String>> normalizeCode, Function<String, String> displayCode) {
        public Route {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(uri, "uri");
            Objects.requireNonNull(connectTimeout, "connectTimeout");
            Objects.requireNonNull(transport, "transport");
            Objects.requireNonNull(normalizeCode, "normalizeCode");
            Objects.requireNonNull(displayCode, "displayCode");
        }

        /** 메인 서버 방식: 6글자 로비 코드, TCP 한 줄 핸드셰이크 */
        public static Route main(URI uri) {
            return new Route("메인", uri, Duration.ofSeconds(3), new LineRelayTransport(),
                    LobbyCode::normalize, LobbyCode::display);
        }

        /** 예비 서버 방식: 숫자 4자리 코드, WebSocket */
        public static Route fallback(URI uri, RelayTransport transport) {
            return new Route("예비", uri, Duration.ofSeconds(8), transport, InternetRelay::digitCode, code -> code);
        }
    }

    private final List<Route> routes;
    private final Executor dispatcher;
    private final Object lock = new Object();
    private final ArrayDeque<byte[]> pendingToGame = new ArrayDeque<>();
    private RelayTransport.Channel channel;
    private Socket gameSocket;
    private ServerSocket loopbackServer;
    private CompletableFuture<String> roomCode;
    private CompletableFuture<Integer> loopbackPort;
    /** 지금 시도 중인 서버(routes 순번). 지난 시도의 콜백은 무시한다 */
    private int attempt = -1;
    private int hostGamePort = -1;
    private boolean started;
    private boolean paired;
    private boolean closed;

    /** 메인 → 예비 순서. 시스템 속성으로 각각 바꿀 수 있다 */
    public static List<Route> configuredRoutes() {
        String main = System.getProperty(MAIN_PROPERTY);
        String fallback = System.getProperty(FALLBACK_PROPERTY);
        URI mainUri = main == null || main.isBlank() ? MAIN_URI : URI.create("tcp://" + main.trim());
        URI fallbackUri = fallback == null || fallback.isBlank() ? FALLBACK_URI : URI.create(fallback.trim());
        return List.of(Route.main(mainUri), Route.fallback(fallbackUri, new JdkRelayTransport()));
    }

    /** 숫자 4자리면 그대로, 아니면 빈 값 (예비 서버 코드) */
    static Optional<String> digitCode(String input) {
        String code = input == null ? "" : input.trim();
        return code.matches("\\d{4}") ? Optional.of(code) : Optional.empty();
    }

    public InternetRelay(List<Route> routes, Executor dispatcher) {
        if (routes == null || routes.isEmpty()) throw new IllegalArgumentException("no relay route");
        this.routes = List.copyOf(routes);
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /** 서버 하나(예비 서버 방식, 숫자 4자리 코드) */
    public InternetRelay(URI uri, Executor dispatcher, RelayTransport transport) {
        this(List.of(Route.fallback(uri, transport)), dispatcher);
    }

    /** 입력한 코드가 어느 서버의 코드든 맞는 모양인지 */
    public boolean accepts(String code) {
        return routes.stream().anyMatch(route -> route.normalizeCode().apply(code).isPresent());
    }

    /** 호스트: 게임이 대기 중인 포트를 넘기고 화면에 보일 방 코드를 받는다 (메인 실패 시 예비 서버) */
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
        connect(0, request("host", null));
        return onDispatcher(result);
    }

    /** 참가자: 방 코드로 참가하고, 게임이 접속할 이 PC의 루프백 포트를 받는다. 코드 모양으로 서버를 고른다 */
    public CompletionStage<Integer> join(String code) {
        int index = -1;
        String normalized = null;
        for (int i = 0; i < routes.size() && normalized == null; i++) {
            Optional<String> match = routes.get(i).normalizeCode().apply(code);
            if (match.isPresent()) {
                index = i;
                normalized = match.get();
            }
        }
        if (normalized == null) throw new IllegalArgumentException("unknown room code format");
        CompletableFuture<Integer> result = new CompletableFuture<>();
        synchronized (lock) {
            claimStart();
            loopbackPort = result;
        }
        connect(index, request("join", normalized));
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

    private void connect(int index, String firstMessage) {
        Route route = routes.get(index);
        synchronized (lock) {
            if (closed) return;
            attempt = index;
        }
        route.transport().open(route.uri(), route.connectTimeout(), new Listener(index, firstMessage))
                .whenComplete((opened, error) -> {
                    if (error != null) {
                        failOrNext(index, firstMessage, RelayException.Reason.UNREACHABLE, String.valueOf(error.getMessage()));
                        return;
                    }
                    synchronized (lock) {
                        if (closed || attempt != index) {
                            opened.close();
                            return;
                        }
                        channel = opened;
                    }
                    opened.sendText(firstMessage).whenComplete((ignored, sendError) -> {
                        if (sendError != null) {
                            failOrNext(index, firstMessage, RelayException.Reason.UNREACHABLE,
                                    String.valueOf(sendError.getMessage()));
                        }
                    });
                });
    }

    /**
     * 서버 하나가 실패했다. 호스트가 아직 방 코드를 못 받았고 다음 서버가 있으면 그쪽으로, 아니면 실패로 끝낸다.
     * 참가자는 코드가 정한 서버만 쓰므로 바로 실패한다
     */
    private void failOrNext(int index, String firstMessage, RelayException.Reason reason, String detail) {
        RelayTransport.Channel stale;
        boolean next;
        synchronized (lock) {
            if (closed || attempt != index) return;
            next = roomCode != null && !roomCode.isDone() && !paired && index + 1 < routes.size();
            stale = channel;
            if (next) {
                channel = null;
                attempt = index + 1; // 닫는 중인 이전 연결의 콜백이 다시 여기로 오지 않게 먼저 넘긴다
            }
        }
        if (!next) {
            fail(reason, detail);
            return;
        }
        if (stale != null) stale.close();
        connect(index + 1, firstMessage);
    }

    private final class Listener implements RelayTransport.Listener {
        private final int index;
        private final String firstMessage;

        Listener(int index, String firstMessage) {
            this.index = index;
            this.firstMessage = firstMessage;
        }

        private boolean current() {
            synchronized (lock) {
                return !closed && attempt == index;
            }
        }

        @Override
        public void onText(String text) {
            if (!current()) return;
            JsonNode message;
            try {
                message = JSON.readTree(text);
            } catch (IOException e) {
                failOrNext(index, firstMessage, RelayException.Reason.BAD_REQUEST, "unreadable server message");
                return;
            }
            switch (message.path("op").asText()) {
                case "room" -> {
                    CompletableFuture<String> code;
                    synchronized (lock) {
                        code = roomCode;
                    }
                    if (code != null) code.complete(routes.get(index).displayCode().apply(message.path("code").asText()));
                }
                case "paired" -> onPaired();
                case "error" -> failOrNext(index, firstMessage,
                        RelayException.Reason.fromServer(message.path("reason").asText()), null);
                default -> {
                    // 모르는 제어 메시지는 무시한다 (서버가 나중에 늘려도 끊기지 않게)
                }
            }
        }

        @Override
        public void onBinary(byte[] bytes) {
            synchronized (lock) {
                if (closed || attempt != index) return;
                if (gameSocket == null) {
                    pendingToGame.addLast(bytes); // 게임 연결이 열리기 전에 온 바이트는 순서대로 모아 둔다
                    return;
                }
                if (!writeToGame(bytes)) closeLater();
            }
        }

        @Override
        public void onClosed(String reason) {
            if (!current()) return;
            boolean beforePair;
            synchronized (lock) {
                beforePair = !paired;
            }
            if (beforePair) failOrNext(index, firstMessage, RelayException.Reason.CLOSED, reason);
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
