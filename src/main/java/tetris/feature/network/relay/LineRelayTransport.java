package tetris.feature.network.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import tetris.relay.RelayLine;
import tetris.relay.RoomName;

/**
 * 메인 중계 서버({@code tetris.relay.RelayServer})와 TCP로 주고받는다. 서버는 게임 바이트 앞에 한 줄 핸드셰이크만 쓴다
 * ({@code HOST v1} → {@code ROOM 코드} … {@code PAIRED}, {@code JOIN v1 코드} → {@code OK} 또는 {@code ERR 이유}).
 *
 * <p>{@link InternetRelay}는 예비 서버와 같은 JSON 제어 메시지로 다루므로, 이 클래스가 두 형식을 서로 바꿔 준다.
 * 핸드셰이크가 끝난 뒤의 바이트는 그대로 넘긴다. 주소는 {@code tcp://호스트:포트}.
 */
public final class LineRelayTransport implements RelayTransport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int BUFFER_BYTES = 8 * 1024;

    @Override
    public CompletableFuture<Channel> open(URI uri, Duration connectTimeout, Listener listener) {
        CompletableFuture<Channel> result = new CompletableFuture<>();
        Thread.ofPlatform().daemon().name("relay-line-connect").start(() -> {
            Socket socket = new Socket();
            try {
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), (int) connectTimeout.toMillis());
            } catch (IOException | IllegalArgumentException e) {
                closeQuietly(socket);
                result.completeExceptionally(e);
                return;
            }
            LineChannel channel = new LineChannel(socket, listener);
            result.complete(channel);
            Thread.ofPlatform().daemon().name("relay-line-reader").start(channel::readLoop);
        });
        return result;
    }

    private static final class LineChannel implements Channel {
        private final Socket socket;
        private final Listener listener;
        private final AtomicBoolean closedOnce = new AtomicBoolean();
        private volatile boolean listing;
        private final List<ObjectNode> listed = new ArrayList<>();

        LineChannel(Socket socket, Listener listener) {
            this.socket = socket;
            this.listener = listener;
        }

        /** 짝이 지어질 때까지는 제어 줄, 그 뒤는 바이트 */
        void readLoop() {
            try {
                InputStream in = socket.getInputStream();
                while (true) {
                    String line = RelayLine.read(in);
                    String[] parts = line.trim().split(" ");
                    switch (parts[0]) {
                        case "R" -> {
                            if (listing) listed.add(roomNode(parts));
                        }
                        case "END" -> {
                            listener.onText(roomsJson());
                            return;
                        }
                        case "ROOM" -> listener.onText(roomOpened(parts));
                        case "ERR" -> {
                            listener.onText("{\"op\":\"error\",\"reason\":\"" + reason(parts.length > 1 ? parts[1] : "") + "\"}");
                            return;
                        }
                        case "OK", "PAIRED" -> {
                            listener.onText("{\"op\":\"paired\"}");
                            pumpBytes(in);
                            return;
                        }
                        default -> {
                            // 모르는 제어 줄은 무시한다
                        }
                    }
                }
            } catch (IOException e) {
                // 서버·상대가 닫았다
            } finally {
                notifyClosed();
            }
        }

        private void pumpBytes(InputStream in) throws IOException {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = in.read(buffer)) != -1) listener.onBinary(Arrays.copyOf(buffer, read));
        }

        /** 메인 서버의 오류 이름을 예비 서버와 같은 이름으로 */
        private static String reason(String server) {
            return switch (server) {
                case "NO_ROOM", "ROOM_FULL" -> "NO_ROOM";
                case "SLOW_DOWN" -> "TOO_MANY_ATTEMPTS";
                case "FULL" -> "SERVER_FULL";
                case "PRIVATE" -> "PRIVATE";
                case "ROOM_LIMIT" -> "ROOM_LIMIT";
                default -> "BAD_REQUEST";
            };
        }

        @Override
        public CompletableFuture<Void> sendText(String text) {
            try {
                JsonNode request = JSON.readTree(text);
                String line = switch (request.path("op").asText()) {
                    case "host" -> hostLine(request);
                    case "join" -> joinLine(request);
                    case "list" -> {
                        listing = true;
                        yield "LIST v2";
                    }
                    default -> throw new IOException("unknown relay request");
                };
                RelayLine.write(socket.getOutputStream(), line);
                return CompletableFuture.completedFuture(null);
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        private static String hostLine(JsonNode request) {
            if (!request.hasNonNull("name") || request.path("name").asText().isEmpty()) return "HOST v1";
            String visibility = request.path("visibility").asText("public");
            String mode = request.path("mode").asText("classic");
            return "HOST v2 " + visibility + " " + mode + " " + RoomName.encode(request.path("name").asText());
        }

        private static String joinLine(JsonNode request) {
            String id = request.path("id").asText("");
            String code = request.path("code").asText("");
            if (!id.isEmpty() && code.isEmpty()) return "JOINID v2 " + id;
            return "JOIN v1 " + code;
        }

        private static String roomOpened(String[] parts) {
            String code = parts.length > 1 ? parts[1] : "";
            if (parts.length > 2 && !parts[2].isEmpty()) {
                return "{\"op\":\"room\",\"code\":\"" + code + "\",\"id\":\"" + parts[2] + "\"}";
            }
            return "{\"op\":\"room\",\"code\":\"" + code + "\"}";
        }

        private static ObjectNode roomNode(String[] parts) {
            ObjectNode room = JSON.createObjectNode();
            room.put("id", parts.length > 1 ? parts[1] : "");
            room.put("mode", parts.length > 2 ? parts[2] : "");
            room.put("private", parts.length > 3 && "1".equals(parts[3]));
            room.put("players", parts.length > 4 ? parsePlayers(parts[4]) : 1);
            Optional<String> name = parts.length > 5 ? RoomName.decode(parts[5]) : Optional.empty();
            room.put("name", name.orElse(""));
            return room;
        }

        private static int parsePlayers(String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return 1;
            }
        }

        private String roomsJson() {
            ObjectNode message = JSON.createObjectNode();
            message.put("op", "rooms");
            ArrayNode rooms = message.putArray("rooms");
            for (ObjectNode room : listed) {
                if (!room.path("name").asText().isEmpty()) rooms.add(room);
            }
            return message.toString();
        }

        @Override
        public CompletableFuture<Void> sendBinary(byte[] bytes) {
            try {
                OutputStream out = socket.getOutputStream();
                out.write(bytes);
                out.flush();
                return CompletableFuture.completedFuture(null);
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public void close() {
            closeQuietly(socket);
            notifyClosed();
        }

        private void notifyClosed() {
            if (closedOnce.compareAndSet(false, true)) listener.onClosed("closed");
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 이미 닫혔다
        }
    }
}
