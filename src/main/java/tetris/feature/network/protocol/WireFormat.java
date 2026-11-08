package tetris.feature.network.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 프레임 형식 상수. 값을 바꾸면 VERSION도 올려야 서로 다른 빌드가 섞인 연결을 거부할 수 있다 */
public final class WireFormat {
    /** 다른 값이면 UNSUPPORTED_VERSION으로 연결을 끊는다 */
    public static final int VERSION = 1;
    /** 길이 접두 뒤 JSON 본문의 최대 바이트 수 (64 KiB). 두 보드 STATE도 이 안에 들어가야 한다 */
    public static final int MAX_FRAME_BYTES = 64 * 1024;
    /** 본문 길이를 담는 big-endian int */
    public static final int LENGTH_PREFIX_BYTES = Integer.BYTES;

    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);

    private WireFormat() {
    }
}
