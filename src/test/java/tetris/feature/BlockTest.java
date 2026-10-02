package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.IBlock;
import tetris.component.block.JBlock;
import tetris.component.block.LBlock;
import tetris.component.block.OBlock;
import tetris.component.block.SBlock;
import tetris.component.block.TBlock;
import tetris.component.block.ZBlock;

/** Block 공통 동작(위치, 회전, 복사)과 7종 블록의 종류 값 */
public class BlockTest {
    //#region 종류 값
    @Test
    void 일곱_종류_블록은_각자_자기_종류를_돌려준다() {
        assertEquals(BlockType.I, new IBlock().getType());
        assertEquals(BlockType.J, new JBlock().getType());
        assertEquals(BlockType.L, new LBlock().getType());
        assertEquals(BlockType.O, new OBlock().getType());
        assertEquals(BlockType.S, new SBlock().getType());
        assertEquals(BlockType.T, new TBlock().getType());
        assertEquals(BlockType.Z, new ZBlock().getType());
    }

    @Test
    void 종류를_override하지_않은_블록의_getType은_예외() {
        Block anonymous = new Block(new int[][] {{1}}) { };

        assertThrows(UnsupportedOperationException.class, anonymous::getType);
    }
    //#endregion

    //#region 위치
    @Test
    void 위치를_함께_받는_생성자는_위치를_설정한다() {
        Block block = new Block(new int[][] {{1}}, 3, 4) { };

        assertEquals(3, block.getRow());
        assertEquals(4, block.getCol());
    }

    @Test
    void setPosition은_행과_열을_바꾼다() {
        Block block = new TBlock();
        block.setPosition(5, 6);

        assertEquals(5, block.getRow());
        assertEquals(6, block.getCol());
    }
    //#endregion

    //#region 회전
    @Test
    void rotateRight는_원본을_바꾸지_않고_회전한_새_격자를_돌려준다() {
        Block block = new TBlock();
        int[][] original = copyOf(block.getShape());

        int[][] rotated = block.rotateRight();

        assertArrayEquals(original, block.getShape());
        assertNotSame(block.getShape(), rotated);
        assertEquals(0, block.getRotation());
    }

    @Test
    void rotateClockwise는_모양과_회전_상태를_함께_바꾼다() {
        Block block = new IBlock();
        int[][] expected = block.rotateRight();

        block.rotateClockwise();

        assertArrayEquals(expected, block.getShape());
        assertEquals(1, block.getRotation());
    }

    @Test
    void 네_번_회전하면_처음_모양과_회전_상태로_돌아온다() {
        Block block = new LBlock();
        int[][] original = copyOf(block.getShape());

        for (int i = 0; i < 4; i++) block.rotateClockwise();

        assertArrayEquals(original, block.getShape());
        assertEquals(0, block.getRotation());
    }

    @Test
    void 회전_상태는_0에서_3까지_순환한다() {
        Block block = new ZBlock();
        for (int expected = 1; expected <= 3; expected++) {
            block.rotateClockwise();
            assertEquals(expected, block.getRotation());
        }
        block.rotateClockwise();
        assertEquals(0, block.getRotation());
    }
    //#endregion

    //#region 복사
    @Test
    void copy는_종류와_위치와_모양을_유지한다() {
        Block block = new SBlock();
        block.setPosition(2, 3);
        block.rotateClockwise();

        Block copy = block.copy();

        assertEquals(SBlock.class, copy.getClass());
        assertEquals(2, copy.getRow());
        assertEquals(3, copy.getCol());
        assertEquals(1, copy.getRotation());
        assertArrayEquals(block.getShape(), copy.getShape());
    }

    @Test
    void copy를_바꿔도_원본에_영향이_없다() {
        Block block = new OBlock();
        Block copy = block.copy();

        copy.setPosition(9, 9);
        copy.rotateClockwise();
        copy.getShape()[0][0] = 7;

        assertEquals(0, block.getRow());
        assertEquals(0, block.getRotation());
        assertTrue(block.getShape()[0][0] != 7);
    }
    //#endregion

    private static int[][] copyOf(int[][] shape) {
        int[][] copy = new int[shape.length][];
        for (int i = 0; i < shape.length; i++) copy[i] = shape[i].clone();
        return copy;
    }
}
