package tetris.feature.network.relay;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** JDK 내장 WebSocket 클라이언트(java.net.http)로 중계 서버에 접속한다. 추가 라이브러리가 필요 없다 */
public final class JdkRelayTransport implements RelayTransport {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Override
    public CompletableFuture<Channel> open(URI uri, Duration connectTimeout, Listener listener) {
        AtomicBoolean closedOnce = new AtomicBoolean();
        WebSocket.Listener adapter = new WebSocket.Listener() {
            private final StringBuilder text = new StringBuilder();

            @Override
            public void onOpen(WebSocket socket) {
                socket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                text.append(data);
                if (last) {
                    String message = text.toString();
                    text.setLength(0);
                    listener.onText(message);
                }
                socket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
                // 게임 바이트는 스트림이라 조각 단위로 그대로 넘겨도 순서만 지키면 된다
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                listener.onBinary(bytes);
                socket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
                if (closedOnce.compareAndSet(false, true)) listener.onClosed(reason);
                return null;
            }

            @Override
            public void onError(WebSocket socket, Throwable error) {
                if (closedOnce.compareAndSet(false, true)) listener.onClosed(String.valueOf(error.getMessage()));
            }
        };
        return CLIENT.newWebSocketBuilder()
                .connectTimeout(connectTimeout)
                .buildAsync(uri, adapter)
                .thenApply(JdkChannel::new);
    }

    private record JdkChannel(WebSocket socket) implements Channel {
        @Override
        public CompletableFuture<Void> sendText(String text) {
            return socket.sendText(text, true).thenApply(ignored -> null);
        }

        @Override
        public CompletableFuture<Void> sendBinary(byte[] bytes) {
            return socket.sendBinary(ByteBuffer.wrap(bytes), true).thenApply(ignored -> null);
        }

        @Override
        public void close() {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").whenComplete((ignored, error) -> socket.abort());
            } catch (RuntimeException sendInProgress) {
                socket.abort(); // 다른 전송이 진행 중이면 정상 종료 대신 끊는다
            }
        }
    }
}
