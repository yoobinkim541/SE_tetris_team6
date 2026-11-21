package tetris.relay;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * 인터넷 방 이름. 앞뒤 공백을 지우고 제어 문자를 뺀 뒤 1~20자(코드 포인트)만 받는다.
 * 줄 프로토콜에는 base64url(패딩 없음)로 실어 공백·줄바꿈이 필드를 깨지 않게 한다.
 */
public final class RoomName {
    public static final int MAX_CODE_POINTS = 20;

    private RoomName() {
    }

    /** 사람이 입력한 이름. 비었거나 너무 길면 빈 값 */
    public static Optional<String> sanitize(String raw) {
        if (raw == null) return Optional.empty();
        StringBuilder kept = new StringBuilder();
        raw.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(kept::appendCodePoint);
        String name = kept.toString().trim();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_CODE_POINTS) return Optional.empty();
        return Optional.of(name);
    }

    public static String encode(String name) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }

    /** 줄 토큰을 이름으로. 형식이 틀리거나 이름이 규칙에 안 맞으면 빈 값 */
    public static Optional<String> decode(String token) {
        if (token == null || token.isEmpty()) return Optional.empty();
        try {
            byte[] raw = Base64.getUrlDecoder().decode(token);
            return sanitize(new String(raw, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
