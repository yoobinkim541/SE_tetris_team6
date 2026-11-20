package tetris.feature.network.relay;

import java.util.Objects;

/** 인터넷 대전 중계 서버에서 방을 만들거나 참가하지 못한 이유 */
public final class RelayException extends Exception {
    public enum Reason {
        /** 중계 서버에 접속하지 못했다 (인터넷 연결·주소 문제) */
        UNREACHABLE,
        /** 그런 방 코드가 없거나 이미 다른 사람이 참가했다 */
        NO_ROOM,
        /** 두 PC의 게임 버전이 다르다 */
        PROTO_MISMATCH,
        /** 서버에 방이 너무 많다 */
        SERVER_FULL,
        /** 틀린 코드를 너무 많이 입력했다 (1분 뒤 다시) */
        TOO_MANY_ATTEMPTS,
        /** 방 코드 유효 시간(10분)이 지났다 */
        EXPIRED,
        /** 서버가 요청을 이해하지 못했다 */
        BAD_REQUEST,
        /** 짝이 지어지기 전에 연결이 끊겼다 */
        CLOSED;

        static Reason fromServer(String text) {
            for (Reason reason : values()) {
                if (reason.name().equals(text)) return reason;
            }
            return BAD_REQUEST;
        }
    }

    private final Reason reason;

    public RelayException(Reason reason, String detail) {
        super(reason + (detail == null || detail.isBlank() ? "" : ": " + detail));
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
