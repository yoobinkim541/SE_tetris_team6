package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tetris.feature.versus.DueTimeClock;

/** S7-A1: 대전용 clock adapter가 due time만 기록하는지 (D-07) */
class DueTimeClockTest {
    private long now = 1_000;
    private final DueTimeClock clock = new DueTimeClock(() -> now);

    @Test
    void stoppedClockIsNeverDue() {
        assertFalse(clock.isRunning());
        assertEquals(DueTimeClock.STOPPED, clock.dueAt());
        assertFalse(clock.isDue(Long.MAX_VALUE));
    }

    @Test
    void startRecordsDueTimeAndRestartCountsFromNow() {
        clock.start(1000);
        assertEquals(2_000, clock.dueAt());
        assertFalse(clock.isDue(1_999));
        assertTrue(clock.isDue(2_000));

        now = 1_500;
        clock.start(1000);
        assertEquals(2_500, clock.dueAt());
        assertFalse(clock.isDue(2_000));
    }

    @Test
    void tickWithoutRestartRepeatsTheSameInterval() {
        clock.start(150);
        long fired = clock.dueAt();
        clock.onTicked(fired);
        assertEquals(fired + 150, clock.dueAt());
    }

    @Test
    void restartOrStopInsideTickWins() {
        clock.start(150);
        long fired = clock.dueAt();
        now = fired;
        clock.start(800);              // 예: CLEARING이 끝나 Spawn이 낙하 간격으로 다시 시작
        clock.onTicked(fired);
        assertEquals(fired + 800, clock.dueAt());

        long fired2 = clock.dueAt();
        clock.stop();                  // 예: GAME_OVER
        clock.onTicked(fired2);
        assertFalse(clock.isRunning());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> clock.start(0));
        assertThrows(NullPointerException.class, () -> new DueTimeClock(null));
    }
}
