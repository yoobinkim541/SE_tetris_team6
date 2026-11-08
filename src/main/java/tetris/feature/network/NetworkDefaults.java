package tetris.feature.network;

import java.time.Duration;

/** P2P 연결 기본 상수. 포트와 연결 제한 시간 기본값은 이 클래스 한 곳에서만 정의한다 (decisions.md) */
public final class NetworkDefaults {
    /** 호스트 대기·클라이언트 접속 기본 포트 */
    public static final int DEFAULT_PORT = 47123;
    /** 소켓 접속 및 HELLO 핸드셰이크 기본 제한 시간 (5초) */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private NetworkDefaults() {
    }
}
