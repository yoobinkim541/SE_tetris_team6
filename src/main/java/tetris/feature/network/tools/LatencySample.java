package tetris.feature.network.tools;

import java.util.Objects;

/**
 * 입력 발생 시각과 자기·상대 화면 표시 시각 표본 (S11-C1, R3-LATENCY).
 *
 * <p>{@code remoteShownNanos == -1}이면 상대 화면 표시 시각이 기록되지 않은 표본이며
 * {@link #remoteMillis()}는 {@code -1.0}을 돌려준다.
 */
public record LatencySample(
        String mode,
        String direction,
        long sequence,
        long inputNanos,
        long localShownNanos,
        long remoteShownNanos) {

    private static final double NANOS_PER_MILLI = 1_000_000.0;

    public LatencySample {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(direction, "direction");
        if (mode.isBlank()) {
            throw new IllegalArgumentException("mode must not be blank");
        }
        if (direction.isBlank()) {
            throw new IllegalArgumentException("direction must not be blank");
        }
        if (localShownNanos < inputNanos) {
            throw new IllegalArgumentException(
                    "localShownNanos (" + localShownNanos + ") < inputNanos (" + inputNanos + ")");
        }
        if (remoteShownNanos != -1L && remoteShownNanos < inputNanos) {
            throw new IllegalArgumentException(
                    "remoteShownNanos (" + remoteShownNanos + ") < inputNanos (" + inputNanos + ")");
        }
    }

    public double localMillis() {
        return (localShownNanos - inputNanos) / NANOS_PER_MILLI;
    }

    public double remoteMillis() {
        if (remoteShownNanos == -1L) {
            return -1.0;
        }
        return (remoteShownNanos - inputNanos) / NANOS_PER_MILLI;
    }
}
