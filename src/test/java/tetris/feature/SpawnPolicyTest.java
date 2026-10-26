package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.feature.rule.SpawnPolicy;

/** SPN-1: 가장 높은 칸이 맨 윗줄에 오고 가로는 가운데 (나누어떨어지지 않으면 왼쪽) */
public class SpawnPolicyTest {
    private final SpawnPolicy policy = new SpawnPolicy();

    @Test
    void 첫_행에_칸이_있는_블록은_row_0에서_시작한다() {
        assertEquals(0, policy.getSpawnRow(new OBlock()));
        assertEquals(0, policy.getSpawnRow(new TBlock()));
    }

    @Test
    void I_블록은_첫_행이_비어_있어_숨은_줄에서_시작한다() {
        assertEquals(-1, policy.getSpawnRow(new IBlock()));
    }

    @Test
    void 열은_행렬_크기_기준으로_가운데이고_나누어떨어지지_않으면_왼쪽() {
        assertEquals(3, policy.getSpawnColumn(new TBlock())); // (10 - 3) / 2
        assertEquals(3, policy.getSpawnColumn(new IBlock())); // (10 - 4) / 2
        assertEquals(4, policy.getSpawnColumn(new OBlock())); // (10 - 2) / 2
    }

    @Test
    void 칸이_하나도_없는_블록은_예외() {
        Block empty = new Block(new int[][] {{0, 0}, {0, 0}}) { };

        assertThrows(IllegalArgumentException.class, () -> policy.getSpawnRow(empty));
    }
}
