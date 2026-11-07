package tetris.feature.network.protocol;

/** 프로토콜 위반. 받는 쪽은 이 예외를 받으면 그 연결을 더 읽지 않는다 */
public final class ProtocolException extends Exception {
    private final ErrorCode code;

    public ProtocolException(ErrorCode code, String detail) {
        super(code + ": " + detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
