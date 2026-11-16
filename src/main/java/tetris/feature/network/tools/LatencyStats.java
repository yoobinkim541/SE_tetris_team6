package tetris.feature.network.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 지연 표본의 p50·p95·최대·임계값 초과 건수 통계 (S11-C1, R3-LATENCY).
 *
 * <p>백분위수는 nearest-rank({@code ceil(p/100 * n)}번째, 1부터 시작) 방식을 따르며,
 * 초과 건수는 {@code > thresholdMillis}인 표본 수다.
 */
public record LatencyStats(
        int count,
        double p50,
        double p95,
        double max,
        int overThreshold,
        double thresholdMillis) {

    public LatencyStats {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        if (Double.isNaN(thresholdMillis) || thresholdMillis < 0.0) {
            throw new IllegalArgumentException("invalid thresholdMillis: " + thresholdMillis);
        }
    }

    public static LatencyStats of(List<Double> millis, double thresholdMillis) {
        Objects.requireNonNull(millis, "millis");
        if (millis.isEmpty()) {
            throw new IllegalArgumentException("millis must not be empty");
        }
        if (Double.isNaN(thresholdMillis) || thresholdMillis < 0.0) {
            throw new IllegalArgumentException("invalid thresholdMillis: " + thresholdMillis);
        }
        List<Double> sorted = new ArrayList<>(millis.size());
        int over = 0;
        for (Double value : millis) {
            if (value == null || Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
                throw new IllegalArgumentException("invalid latency sample: " + value);
            }
            sorted.add(value);
            if (value > thresholdMillis) {
                over++;
            }
        }
        Collections.sort(sorted);
        int n = sorted.size();
        double p50 = nearestRank(sorted, 50);
        double p95 = nearestRank(sorted, 95);
        double max = sorted.get(n - 1);
        return new LatencyStats(n, p50, p95, max, over, thresholdMillis);
    }

    private static double nearestRank(List<Double> sorted, int percentile) {
        int n = sorted.size();
        int rank = (int) Math.ceil((percentile / 100.0) * n);
        int index = Math.max(0, Math.min(n - 1, rank - 1));
        return sorted.get(index);
    }
}
