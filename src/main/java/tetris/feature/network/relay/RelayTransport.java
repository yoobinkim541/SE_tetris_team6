package tetris.feature.network.relay;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** 중계 서버와 주고받는 WebSocket 한 개. 앱은 {@link JdkRelayTransport}, 테스트는 메모리 안의 가짜 서버를 쓴다 */
public interface RelayTransport {
    /** connectTimeout 안에 연결을 연다. listener 콜백은 전송 계층 스레드에서 순서대로 온다 */
    CompletableFuture<Channel> open(URI uri, Duration connectTimeout, Listener listener);

    interface Channel {
        /** 제어 메시지(JSON). 이전 전송이 끝난 뒤에만 부른다 */
        CompletableFuture<Void> sendText(String text);

        /** 게임 바이트. 이전 전송이 끝난 뒤에만 부른다 */
        CompletableFuture<Void> sendBinary(byte[] bytes);

        /** 여러 번 불러도 된다 */
        void close();
    }

    interface Listener {
        void onText(String text);

        void onBinary(byte[] bytes);

        /** 상대·서버가 닫았거나 오류로 끊겼다. 한 번만 온다 */
        void onClosed(String reason);
    }
}
