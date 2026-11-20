package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tetris.feature.GameTestSupport.cell;

import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.feature.data.Board;
import tetris.feature.rule.BasicRotationPolicy;
import tetris.feature.rule.SrsRotationPolicy;

/** ROT-8: SRS 시계방향 Wall Kick. 제자리가 막히면 표의 대체 위치를 순서대로 시도한다 */
public class SrsRotationTest {
    private final SrsRotationPolicy srs = new SrsRotationPolicy();
    private final Board board = new Board();

    private void fill(int row, int col) {
        Block c = cell();
        c.setPosition(row, col);
        assertTrue(board.tryPlaceBlock(c));
    }

    @Test
    void 막히지_않으면_제자리_회전_킥_0번() {
        TBlock t = new TBlock();
        t.setPosition(5, 3);
        assertEquals(0, srs.tryRotate(board, t));
        assertEquals(1, t.getRotation());
        assertEquals(5, t.getRow());
        assertEquals(3, t.getCol());
    }

    @Test
    void 바닥에_붙은_T는_위로_밀려나며_회전한다() {
        TBlock t = new TBlock();
        t.setPosition(18, 3);                       // 18~19행, 꼭지 위
        assertEquals(2, srs.tryRotate(board, t));   // 0→R 킥 (-1, +1): 왼쪽 1칸, 위 1칸
        assertEquals(1, t.getRotation());
        assertEquals(17, t.getRow());
        assertEquals(2, t.getCol());
        assertTrue(board.canPlace(t, t.getRow(), t.getCol()));
    }

    @Test
    void 같은_위치에서_1차_회전은_실패한다() { // 1차 규칙(ROT-3)은 BasicRotationPolicy로 남아 있다
        TBlock t = new TBlock();
        t.setPosition(18, 3);
        assertFalse(new BasicRotationPolicy().rotate(board, t));
        assertEquals(0, t.getRotation());
    }

    @Test
    void 모든_킥이_막히면_아무것도_바꾸지_않는다() {
        // 세로 I(col 5)를 사방이 막힌 1칸 폭 우물에 넣는다
        IBlock i = new IBlock();
        i.rotateClockwise();                         // R: 행렬 2열이 세로
        i.setPosition(16, 3);                        // 16~19행, col 5
        for (int row = 12; row < 20; row++) {
            for (int col = 0; col < Board.WIDTH; col++) {
                if (col != 5) fill(row, col);
            }
        }
        assertEquals(-1, srs.tryRotate(board, i));
        assertEquals(1, i.getRotation());
        assertEquals(16, i.getRow());
        assertEquals(3, i.getCol());
    }

    @Test
    void 오른쪽_벽의_세로_I는_왼쪽으로_밀려난다() {
        IBlock i = new IBlock();
        i.rotateClockwise();
        i.setPosition(5, 7);                         // 세로 막대 col 9
        assertEquals(1, srs.tryRotate(board, i));    // R→2 킥 (-1, 0)
        assertEquals(2, i.getRotation());
        assertEquals(6, i.getCol());
    }

    @Test
    void O는_움직이지_않는다() {
        OBlock o = new OBlock();
        o.setPosition(18, 0);
        assertEquals(0, srs.tryRotate(board, o));
        assertEquals(18, o.getRow());
        assertEquals(0, o.getCol());
    }

    @Test
    void 네_번_돌리면_모양이_돌아온다() {
        TBlock t = new TBlock();
        t.setPosition(5, 3);
        int[][] original = t.getShape();
        for (int k = 0; k < 4; k++) assertEquals(0, srs.tryRotate(board, t));
        assertEquals(0, t.getRotation());
        assertEquals(java.util.Arrays.deepToString(original), java.util.Arrays.deepToString(t.getShape()));
    }
}
