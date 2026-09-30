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
    void Level이_1보다_작으면_예외() {
        assertThrows(IllegalArgumentException.class, () -> scoring.softDropScore(0));
    }
}
