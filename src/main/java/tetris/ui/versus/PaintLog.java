package tetris.ui.versus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tetris.feature.versus.api.MatchSnapshot;

/**
 * 경기 화면이 그린 기록 (S11-B2, R3-LATENCY). 최근 {@link #CAPACITY}번의 그리기마다 그린 시각, 그린 스냅샷,
 * 그 스냅샷을 받은 뒤 그리기까지 걸린 시간을 남긴다. 입력→두 화면 표시 지연을 재는 인수 테스트와 시연 측정이 읽는다.
 * EDT 전용. 기록은 화면 하나당 수십 항목뿐이라 경기 성능에 영향이 없다.
 */
public final class PaintLog {
    public static final int CAPACITY = 256;

    /** receivedNanos: 이 스냅샷을 화면이 받은 시각, paintedNanos: 그리기를 마친 시각 (System.nanoTime) */
    public record Entry(long receivedNanos, long paintedNanos, MatchSnapshot snapshot) {
        public Entry {
            Objects.requireNonNull(snapshot);
        }

        /** 상태 수신 → 그리기 지연 */
        public long delayNanos() {
            return paintedNanos - receivedNanos;
        }
    }

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long receivedNanos = -1;
    private long lastPaintedRevision = -1;
    private String lastPaintedMatch;

    /** 새 스냅샷을 받았다. 아직 그리지 않은 앞선 스냅샷이 있으면 그 수신 시각을 유지한다(가장 오래 기다린 기준) */
    void received(long nanos) {
        if (receivedNanos < 0) receivedNanos = nanos;
    }

    /** 그리기를 마쳤다. 같은 스냅샷을 다시 그린 것(창 가림 등)은 기록하지 않는다 */
    void painted(MatchSnapshot snapshot, long nanos) {
        if (snapshot.revision() == lastPaintedRevision && snapshot.matchId().equals(lastPaintedMatch)) return;
        lastPaintedRevision = snapshot.revision();
        lastPaintedMatch = snapshot.matchId();
        long received = receivedNanos < 0 ? nanos : receivedNanos;
        receivedNanos = -1;
        if (entries.size() == CAPACITY) entries.removeFirst();
        entries.addLast(new Entry(received, nanos, snapshot));
    }

    public List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    public void clear() {
        entries.clear();
    }
}
