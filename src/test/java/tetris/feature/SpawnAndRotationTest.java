package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.JBlock;
import tetris.component.block.LBlock;
import tetris.component.block.OBlock;
import tetris.component.block.SBlock;
import tetris.component.block.TBlock;
import tetris.component.block.ZBlock;
import tetris.feature.data.Board;
import tetris.feature.rule.SpawnPolicy;

/** SPN-1, SPN-4, ROT-2~4, ROT-6 (Spawn 앵커, 회전 4회 원복, O 불변, 벽 근처 회전 실패, Spawn 충돌) */
public class SpawnAndRotationTest {
    private final SpawnPolicy spawn = new SpawnPolicy();

    private static Block[] allBlocks() {
        return new Block[] {new IBlock(), new JBlock(), new LBlock(), new OBlock(), new SBlock(), new TBlock(), new ZBlock()};
    }

    //#region Spawn
    @Test
    void Spawn_열은_I_J_L_S_T_Z가_3_O가_4() { // SPN-1
        for (Block block : allBlocks()) {
            int expected = block instanceof OBlock ? 4 : 3;
            assertEquals(expected, spawn.column(block));
        }
    }

    @Test
    void Spawn_행은_I만_마이너스1_나머지는_0() { // SPN-1 (가장 높은 칸 기준)
        for (Block block : allBlocks()) {
            int expected = block instanceof IBlock ? -1 : 0;
            assertEquals(expected, spawn.row(block));
        }
    }

    @Test
    void 빈_보드에서는_7종_모두_Spawn_위치가_유효() {
        Board board = new Board();
        for (Block block : allBlocks()) {
            assertTrue(board.canPlace(block, spawn.row(block), spawn.column(block)));
        }
    }

    @Test
    void Spawn한_I는_맨_윗줄에_보이고_바로_회전할_수_있다() {
        Board board = new Board();
        Block i = new IBlock();
        i.setPosition(spawn.row(i), spawn.column(i));
        assertTrue(board.canPlace(i.rotateRight(), i.getRow(), i.getCol()));

        board.TryPlaceBlock(i);
        int[][] cells = board.copyCells();
        for (int col = 3; col <= 6; col++) assertTrue(cells[0][col] != Board.EMPTY);
    }

    @Test
    void Spawn_칸이_막혀_있으면_유효하지_않다() { // SPN-4
        Board board = new Board();
        Block cell = new Block(new int[][] {{1}}) {};
        cell.setPosition(0, 4);
        board.TryPlaceBlock(cell);

        Block t = new TBlock();
        assertFalse(board.canPlace(t, spawn.row(t), spawn.column(t)));
    }
    //#endregion
}
