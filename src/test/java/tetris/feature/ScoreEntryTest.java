package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tetris.feature.score.ScoreEntry;

/** SBD-4, NAM-2, NAM-3: 잘못된 기록은 만들어질 수 없다 */
public class ScoreEntryTest {
    private static ScoreEntry entry(String name, long score, int level, int lines, Instant recordedAt) {
        return new ScoreEntry(name, score, level, lines, recordedAt);
    }

    @Test
    void 유효한_값으로_기록을_만든다() {
        ScoreEntry entry = entry("홍길동", 100, 3, 12, Instant.EPOCH);

        assertEquals("홍길동", entry.name());
        assertEquals(100, entry.score());
        assertEquals(3, entry.level());
        assertEquals(12, entry.lines());
        assertEquals(Instant.EPOCH, entry.recordedAt());
    }

    @Test
    void 이름_앞뒤_공백은_제거해_보관한다() {
        assertEquals("A B", entry("  A B  ", 0, 1, 0, Instant.EPOCH).name());
    }

    @Test
    void 점수_0과_Level_1과_줄_0은_허용한다() {
        entry("a", 0, 1, 0, Instant.EPOCH);
    }

    @Test
    void 잘못된_이름이면_만들_수_없다() {
        for (String name : new String[] {null, "", "   ", "A!", "ABCDEFGHIJK"}) {
            assertThrows(IllegalArgumentException.class, () -> entry(name, 0, 1, 0, Instant.EPOCH), String.valueOf(name));
        }
    }

    @Test
    void 음수_점수_Level_0_음수_줄이면_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> entry("a", -1, 1, 0, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> entry("a", 0, 0, 0, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> entry("a", 0, 1, -1, Instant.EPOCH));
    }

    @Test
    void 기록_시각이_없으면_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> entry("a", 0, 1, 0, null));
    }
}
