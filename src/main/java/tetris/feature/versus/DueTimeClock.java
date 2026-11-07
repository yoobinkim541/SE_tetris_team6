package tetris.feature.versus;

import java.util.Objects;
import tetris.feature.game.GameClock;
import tetris.feature.versus.api.MatchClock;

/**
 * 대전용 {@link GameClock} 어댑터 (D-07). 스윙 타이머를 돌리지 않고 다음 tick의 마감 시각만 기록한다.
 * MatchEngine은 pulse마다 {@link #isDue(long)}인 세션에만 {@code tick()}을 호출한다.
 * {@code GameSession.tick()}은 실제로 한 칸 내리거나 CLEARING을 끝내므로 매 pulse 호출하면 안 된다.
 * 싱글 플레이는 기존 SwingGameClock을 그대로 쓴다.
 */
public final class DueTimeClock implements GameClock {
    /** 멈춘 상태 */
    public static final long STOPPED = Long.MIN_VALUE;

    private final MatchClock time;
    private long dueAt = STOPPED;
    private int interval;

    public DueTimeClock(MatchClock time) {
        this.time = Objects.requireNonNull(time, "time");
    }

    /** 전체 간격으로 (재)시작. 이미 돌고 있어도 지금부터 다시 센다 (GameClock 계약과 같음) */
    @Override
    public void start(int intervalMillis) {
        if (intervalMillis <= 0) throw new IllegalArgumentException("interval must be positive");
        interval = intervalMillis;
        dueAt = time.nowMillis() + intervalMillis;
    }

    @Override
    public void stop() {
        dueAt = STOPPED;
    }

    public boolean isRunning() {
        return dueAt != STOPPED;
    }

    public boolean isDue(long nowMillis) {
        return isRunning() && nowMillis >= dueAt;
    }

    public long dueAt() {
        return dueAt;
    }

    /**
     * tick을 실행한 직후 호출. tick 안에서 start/stop이 다시 불렸다면 그 값을 유지하고,
     * 아니면 같은 간격으로 다음 마감을 잡는다 (Swing Timer의 반복 동작과 같음).
     */
    public void onTicked(long firedDueAt) {
        if (dueAt == firedDueAt) dueAt = firedDueAt + interval;
    }
}
