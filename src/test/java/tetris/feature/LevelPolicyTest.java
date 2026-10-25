package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tetris.feature.game.Difficulty;
import tetris.feature.rule.LevelPolicy;

/** Level 상승(블록 10개·줄 5개, 두 카운터는 따로 셈)과 난이도별 낙하 간격 계산을 검증한다 */
public class LevelPolicyTest {
    @Test
    void 낙하_간격은_Level_1부터_4까지_200ms씩_줄고_5부터_250() {
        assertEquals(1000, LevelPolicy.fallIntervalMillis(1));
        assertEquals(800, LevelPolicy.fallIntervalMillis(2));
        assertEquals(600, LevelPolicy.fallIntervalMillis(3));
        assertEquals(400, LevelPolicy.fallIntervalMillis(4));
        assertEquals(250, LevelPolicy.fallIntervalMillis(5));
        assertEquals(250, LevelPolicy.fallIntervalMillis(50));
    }

    @Test
    void 처음은_Level_1_카운터_0() {
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
    void 삭제_5줄마다_Level_상승하고_나머지는_유지() {
        LevelPolicy policy = new LevelPolicy();
        policy.onLinesCleared(4);
        policy.onLinesCleared(3);
        assertEquals(2, policy.getLevel());
        assertEquals(2, policy.getLineCounter());
    }

    @Test
    void 두_카운터는_서로_독립() { // 블록 9개 + 3줄 상태에서 2줄을 더 지운 뒤 블록 하나가 나오는 경우
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
    void reset은_처음_상태로() {
        LevelPolicy policy = new LevelPolicy();
        for (int i = 0; i < 12; i++) policy.onBlockSpawned();
        policy.onLinesCleared(4);
        policy.reset();
        assertEquals(1, policy.getLevel());
        assertEquals(0, policy.getLineCounter());
        assertEquals(0, policy.getBlockCounter());
    }

    @Test
    void 한_번에_삭제한_줄_수가_0에서_4를_벗어나면_예외() { // 블록 하나는 최대 4줄을 채운다
        LevelPolicy policy = new LevelPolicy();
        assertThrows(IllegalArgumentException.class, () -> policy.onLinesCleared(-1));
        assertThrows(IllegalArgumentException.class, () -> policy.onLinesCleared(5));
    }

    //#region 난이도별 낙하 간격
    // 난이도별 기대 간격(ms). 배열 index 0 = Level 1, Level 6부터는 모두 250
    private static final int[] EASY_INTERVALS = {1000, 840, 680, 520, 360, 250};
    private static final int[] NORMAL_INTERVALS = {1000, 800, 600, 400, 250, 250};
    private static final int[] HARD_INTERVALS = {1000, 760, 520, 280, 250, 250};

    private static void assertIntervals(Difficulty difficulty, int[] expected) {
        for (int level = 1; level <= expected.length; level++) {
            assertEquals(expected[level - 1], LevelPolicy.fallIntervalMillis(level, difficulty), difficulty + " Level " + level);
        }
        assertEquals(250, LevelPolicy.fallIntervalMillis(100, difficulty));
    }

    @Test
    void EASY_낙하_간격_표() {
        assertIntervals(Difficulty.EASY, EASY_INTERVALS);
    }

    @Test
    void NORMAL_낙하_간격_표() {
        assertIntervals(Difficulty.NORMAL, NORMAL_INTERVALS);
    }

    @Test
    void HARD_낙하_간격_표() {
        assertIntervals(Difficulty.HARD, HARD_INTERVALS);
    }

    @Test
    void NORMAL은_v1_2_식과_모든_Level에서_같다() {
        for (int level = 1; level <= 100; level++) {
            assertEquals(Math.max(250, 1200 - 200 * level), LevelPolicy.fallIntervalMillis(level, Difficulty.NORMAL), "Level " + level);
            assertEquals(LevelPolicy.fallIntervalMillis(level, Difficulty.NORMAL), LevelPolicy.fallIntervalMillis(level));
        }
    }

    @Test
    void 레벨당_감소폭은_EASY_20퍼센트_덜_HARD_20퍼센트_더() { // 요구사항: easy는 20% 덜, hard는 20% 더 빨라진다
        int normalStep = 1000 - LevelPolicy.fallIntervalMillis(2, Difficulty.NORMAL);
        assertEquals(normalStep * 0.8, 1000 - LevelPolicy.fallIntervalMillis(2, Difficulty.EASY), 0.001);
        assertEquals(normalStep * 1.2, 1000 - LevelPolicy.fallIntervalMillis(2, Difficulty.HARD), 0.001);
    }

    @Test
    void 인스턴스는_생성자로_받은_난이도로_간격을_계산() {
        LevelPolicy easy = new LevelPolicy(Difficulty.EASY);
        LevelPolicy hard = new LevelPolicy(Difficulty.HARD);
        for (int i = 0; i < 10; i++) {
            easy.onBlockSpawned();
            hard.onBlockSpawned();
        }
        assertEquals(2, easy.getLevel());
        assertEquals(840, easy.fallIntervalMillis());
        assertEquals(760, hard.fallIntervalMillis());
    }

    @Test
    void Level_상승_조건은_난이도와_무관() {
        for (Difficulty difficulty : Difficulty.values()) {
            LevelPolicy policy = new LevelPolicy(difficulty);
            policy.onLinesCleared(4);
            policy.onLinesCleared(1);
            for (int i = 0; i < 10; i++) policy.onBlockSpawned();
            assertEquals(3, policy.getLevel(), difficulty.name());
        }
    }

    @Test
    void reset해도_난이도는_유지() { // Restart해도 같은 난이도로 다시 시작한다
        LevelPolicy policy = new LevelPolicy(Difficulty.HARD);
        for (int i = 0; i < 10; i++) policy.onBlockSpawned();
        policy.reset();
        assertEquals(1000, policy.fallIntervalMillis());
        for (int i = 0; i < 10; i++) policy.onBlockSpawned();
        assertEquals(760, policy.fallIntervalMillis());
    }

    @Test
    void 기본_생성자는_NORMAL() {
        LevelPolicy policy = new LevelPolicy();
        for (int i = 0; i < 10; i++) policy.onBlockSpawned();
        assertEquals(800, policy.fallIntervalMillis());
    }

    @Test
    void 잘못된_입력은_예외() {
        assertThrows(IllegalArgumentException.class, () -> new LevelPolicy(null));
        assertThrows(IllegalArgumentException.class, () -> LevelPolicy.fallIntervalMillis(1, null));
        assertThrows(IllegalArgumentException.class, () -> LevelPolicy.fallIntervalMillis(0, Difficulty.NORMAL));
    }
    //#endregion
}
