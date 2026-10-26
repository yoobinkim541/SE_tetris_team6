package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Random;
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
import tetris.feature.item.ItemType;

/** Block 공통 동작(위치, 회전, 복사, 아이템 칸)과 7종 블록의 종류 값 */
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

    //#region 아이템 칸
    private static Block[] allBlocks() {
        return new Block[] {new IBlock(), new JBlock(), new LBlock(), new OBlock(), new SBlock(), new TBlock(), new ZBlock()};
    }

    // 아이템이 붙은 칸 위치. 없으면 null
    private static int[] itemPosition(Block block) {
        int[][] shape = block.getShape();
        int[] found = null;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (block.itemCellAt(r, c).isEmpty()) continue;
                assertNull(found, "아이템 칸이 두 개 이상");
                found = new int[] {r, c};
            }
        }
        return found;
    }

    @Test
    void 아이템을_붙이지_않은_블록은_모든_칸이_비어_있다() {
        for (Block block : allBlocks()) assertNull(itemPosition(block), block.getType().name());
    }

    @Test
    void 지정한_칸에만_아이템이_붙는다() {
        Block block = new IBlock();
        block.attachItem(1, 2, ItemType.LINE_CLEAR);

        assertEquals(Optional.of(ItemType.LINE_CLEAR), block.itemCellAt(1, 2));
        assertArrayEquals(new int[] {1, 2}, itemPosition(block));
    }

    @Test
    void 격자_밖_좌표로_조회하면_비어_있다() {
        Block block = new OBlock();
        block.attachItem(0, 1, ItemType.BOMB);

        assertTrue(block.itemCellAt(-1, 1).isEmpty());
        assertTrue(block.itemCellAt(0, 99).isEmpty());
    }

    @Test
    void 아이템을_붙여도_모양은_바뀌지_않는다() {
        Block block = new TBlock();
        block.attachItem(1, 1, ItemType.SLOW);

        assertArrayEquals(new TBlock().getShape(), block.getShape());
    }

    @Test
    void 빈_칸이나_격자_밖에는_붙일_수_없다() {
        Block block = new TBlock(); // (0, 0)은 빈 칸
        assertThrows(IllegalArgumentException.class, () -> block.attachItem(0, 0, ItemType.BOMB));
        assertThrows(IllegalArgumentException.class, () -> block.attachItem(-1, 1, ItemType.BOMB));
        assertThrows(IllegalArgumentException.class, () -> block.attachItem(1, 3, ItemType.BOMB));
        assertThrows(IllegalArgumentException.class, () -> block.attachItem(1, 1, null));
        assertNull(itemPosition(block));
    }

    @Test
    void 아이템은_블록당_하나만_붙는다() {
        Block block = new IBlock();
        block.attachItem(1, 0, ItemType.LINE_CLEAR);

        assertThrows(IllegalStateException.class, () -> block.attachItem(1, 3, ItemType.BOMB));
        assertThrows(IllegalStateException.class, () -> block.attachItemToRandomCell(ItemType.BOMB, new Random(1)));
        assertArrayEquals(new int[] {1, 0}, itemPosition(block));
    }

    @Test
    void 무작위로_붙이면_항상_채워진_칸에_붙는다() {
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            for (Block block : allBlocks()) {
                block.attachItemToRandomCell(ItemType.DOUBLE_SCORE, random);
                int[] position = itemPosition(block);
                assertTrue(block.getShape()[position[0]][position[1]] != 0, block.getType().name());
            }
        }
    }

    @Test
    void I_블록의_4칸이_같은_확률로_선택된다() { // OOOL, OOLO, OLOO, LOOO
        int draws = 10_000;
        Random random = new Random(42);
        int[] counts = new int[4];
        for (int i = 0; i < draws; i++) {
            Block block = new IBlock();
            block.attachItemToRandomCell(ItemType.LINE_CLEAR, random);
            int[] position = itemPosition(block);
            assertEquals(1, position[0]);
            counts[position[1]]++;
        }
        for (int col = 0; col < 4; col++) assertEquals(0.25, counts[col] / (double) draws, 0.05, "col " + col);
    }

    @Test
    void 무작위_위치는_시드가_같으면_같다() {
        for (Block block : allBlocks()) {
            Block other = block.copy();
            block.attachItemToRandomCell(ItemType.BOMB, new Random(7));
            other.attachItemToRandomCell(ItemType.BOMB, new Random(7));
            assertArrayEquals(itemPosition(block), itemPosition(other));
        }
    }

    @Test
    void 회전하면_아이템도_같은_칸을_따라_돈다() {
        Block block = new IBlock(); // 가로 막대는 1행
        block.attachItem(1, 3, ItemType.LINE_CLEAR);

        block.rotateClockwise(); // 세로 막대는 2열, 오른쪽 끝 칸은 맨 아래로
        assertArrayEquals(new int[] {3, 2}, itemPosition(block));
    }

    @Test
    void 몇_번을_회전해도_아이템은_채워진_칸에_있고_4번이면_제자리() {
        for (Block block : allBlocks()) {
            block.attachItemToRandomCell(ItemType.SLOW, new Random(3));
            int[] original = itemPosition(block);
            for (int i = 0; i < 4; i++) {
                block.rotateClockwise();
                int[] position = itemPosition(block);
                assertTrue(block.getShape()[position[0]][position[1]] != 0, block.getType() + " 회전 " + (i + 1));
            }
            assertArrayEquals(original, itemPosition(block), block.getType().name());
        }
    }

    @Test
    void rotateRight로_미리_보기만_하면_아이템은_움직이지_않는다() {
        Block block = new JBlock();
        block.attachItem(0, 0, ItemType.BOMB);
        block.rotateRight();

        assertArrayEquals(new int[] {0, 0}, itemPosition(block));
    }

    @Test
    void copy는_아이템을_유지하고_복사본을_돌려도_원본은_그대로() {
        Block block = new LBlock();
        block.attachItem(1, 0, ItemType.DOUBLE_SCORE);

        Block copy = block.copy();
        assertEquals(Optional.of(ItemType.DOUBLE_SCORE), copy.itemCellAt(1, 0));

        copy.rotateClockwise();
        assertEquals(Optional.of(ItemType.DOUBLE_SCORE), block.itemCellAt(1, 0));
        assertArrayEquals(new int[] {0, 1}, itemPosition(copy));
    }

    @Test
    void Random이_null이면_예외() {
        assertThrows(IllegalArgumentException.class, () -> new OBlock().attachItemToRandomCell(ItemType.BOMB, null));
    }
    //#endregion

    private static int[][] copyOf(int[][] shape) {
        int[][] copy = new int[shape.length][];
        for (int i = 0; i < shape.length; i++) copy[i] = shape[i].clone();
        return copy;
    }
}
