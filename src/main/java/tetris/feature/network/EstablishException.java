package tetris.feature.network;

import java.util.Objects;

/** 연결 수립 실패를 나타내는 예외. {@link EstablishFailure}로 원인을 구분한다 */
public final class EstablishException extends Exception {
    private final EstablishFailure reason;

    public EstablishException(EstablishFailure reason, String detail) {
        super(Objects.requireNonNull(reason, "reason") + ": " + detail);
        this.reason = reason;
    }

    public EstablishException(EstablishFailure reason, String detail, Throwable cause) {
        super(Objects.requireNonNull(reason, "reason") + ": " + detail, cause);
        this.reason = reason;
    }

    public EstablishFailure reason() {
        return reason;
    }
}
