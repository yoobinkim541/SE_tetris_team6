package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tetris.feature.rule.LevelPolicy;

/** LVL-2, LVL-3 (interval 1..4 = 1000/800/600/400, Level 5 이상 250, 두 카운터 독립 누적) */
public class LevelPolicyTest {
    @Test
    void 낙하_간격은_Level_1부터_4까지_200ms씩_줄고_5부터_250() { // LVL-2
        assertEquals(1000, LevelPolicy.fallIntervalMillis(1));
        assertEquals(800, LevelPolicy.fallIntervalMillis(2));
        assertEquals(600, LevelPolicy.fallIntervalMillis(3));
        assertEquals(400, LevelPolicy.fallIntervalMillis(4));
        assertEquals(250, LevelPolicy.fallIntervalMillis(5));
        assertEquals(250, LevelPolicy.fallIntervalMillis(50));
    }

    @Test
    void 처음은_Level_1_카운터_0() { // LVL-1
        LevelPolicy policy = new LevelPolicy();
        assertEquals(1, policy.getLevel());
        assertEquals(0, policy.getLineCounter());
        assertEquals(0, policy.getBlockCounter());
        assertEquals(1000, policy.fallIntervalMillis());
    }

    @Test
    void 블록_10개마다_Level_상승() {
        LevelPolicy policy = new LevelPolicy();
        for (int i = 0; i < 9; i++) policy.onBlockSpawned();
        assertEquals(1, policy.getLevel());
        policy.onBlockSpawned();
        assertEquals(2, policy.getLevel());
        assertEquals(0, policy.getBlockCounter());
    }

    @Test
    void 삭제_5줄마다_Level_상승하고_나머지는_유지() { // LVL-3
        LevelPolicy policy = new LevelPolicy();
        policy.onLinesCleared(4);
        policy.onLinesCleared(3);
        assertEquals(2, policy.getLevel());
        assertEquals(2, policy.getLineCounter());
    }

    @Test
    void 두_카운터는_서로_독립() { // LVL-3, §4.1 첫 번째 시나리오
        LevelPolicy policy = new LevelPolicy();
        for (int i = 0; i < 9; i++) policy.onBlockSpawned();
        policy.onLinesCleared(3);

        policy.onLinesCleared(2);   // L5
        assertEquals(2, policy.getLevel());
        assertEquals(0, policy.getLineCounter());
        assertEquals(9, policy.getBlockCounter());

        policy.onBlockSpawned();    // S3
        assertEquals(3, policy.getLevel());
        assertEquals(0, policy.getBlockCounter());
        assertEquals(600, policy.fallIntervalMillis());
    }

    @Test
    void reset은_처음_상태로() { // LVL-4
        LevelPolicy policy = new LevelPolicy();
        for (int i = 0; i < 12; i++) policy.onBlockSpawned();
        policy.onLinesCleared(4);
        policy.reset();
        assertEquals(1, policy.getLevel());
        assertEquals(0, policy.getLineCounter());
        assertEquals(0, policy.getBlockCounter());
    }

    @Test
    void 한_번에_삭제한_줄_수가_0에서_4를_벗어나면_예외() { // CLR-3
        LevelPolicy policy = new LevelPolicy();
        assertThrows(IllegalArgumentException.class, () -> policy.onLinesCleared(-1));
        assertThrows(IllegalArgumentException.class, () -> policy.onLinesCleared(5));
    }
}
