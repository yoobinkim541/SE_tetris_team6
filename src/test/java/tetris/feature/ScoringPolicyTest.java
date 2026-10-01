package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tetris.feature.rule.ScoringPolicy;

/** SCR-1~3 (하강 +Level, Hard Drop 칸수 x Level, 보너스 100/300/500/800, 명세 6장 339점 예시) */
public class ScoringPolicyTest {
    private final ScoringPolicy scoring = new ScoringPolicy();

    @Test
    void 하강_한_칸은_Level점() { // SCR-1
        assertEquals(1L, scoring.softDropScore(1));
        assertEquals(3L, scoring.softDropScore(3));
    }

    @Test
    void 줄_삭제_보너스는_Level과_무관하게_100_300_500_800() { // SCR-3
        assertEquals(0L, scoring.lineClearBonus(0));
        assertEquals(100L, scoring.lineClearBonus(1));
        assertEquals(300L, scoring.lineClearBonus(2));
        assertEquals(500L, scoring.lineClearBonus(3));
        assertEquals(800L, scoring.lineClearBonus(4));
    }

    @Test
    void 한_번에_지운_줄_수가_0에서_4를_벗어나면_예외() { // CLR-3
        assertThrows(IllegalArgumentException.class, () -> scoring.lineClearBonus(5));
        assertThrows(IllegalArgumentException.class, () -> scoring.lineClearBonus(-1));
    }

    @Test
    void Level이_1보다_작으면_예외() {
        assertThrows(IllegalArgumentException.class, () -> scoring.softDropScore(0));
    }
}
