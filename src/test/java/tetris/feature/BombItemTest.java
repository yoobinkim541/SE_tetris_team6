package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.feature.data.Board;
import tetris.feature.item.BombEffect;
import tetris.feature.item.Cell;
import tetris.feature.item.DoubleScoreEffect;
import tetris.feature.item.GameModifiers;
import tetris.feature.item.ItemResult;
import tetris.feature.item.ItemType;

/**
 * 폭탄 아이템(B)이 아이템 칸 중심 3×3의 채워진 칸만 지울 칸으로 돌려주는지 검증한다.
 * 보드 밖은 잘리고 숨은 줄은 포함, 꽉 찬 행의 칸은 제외, 지울 칸 하나당 10점.
 */
public class BombItemTest {
    private final BombEffect bomb = new BombEffect();

    private static Block row(ItemType item) {
        int[][] shape = new int[10][10];
        java.util.Arrays.fill(shape[0], 1);
        Block block = new Block(shape) {
            @Override public tetris.component.block.BlockType getType() {
                return tetris.component.block.BlockType.I;
            }
        };
        block.attachItem(0, 4, item);
        return block;
    }

    @Test
    void 실제_B_Lock의_칸_점수는_D_중에도_한번만_더한다() {
        Block bombCell = GameTestSupport.cell();
        bombCell.attachItem(0, 0, ItemType.BOMB);
        var session = GameTestSupport.startedSession(row(ItemType.DOUBLE_SCORE), bombCell,
                new tetris.component.block.OBlock());
        session.hardDrop();
        session.tick();
        assertEquals(ItemType.DOUBLE_SCORE, session.snapshot().effects().getFirst().type());
        session.hardDrop();
        long beforeClear = session.snapshot().score();
        assertEquals(1, session.snapshot().clearingCells().size());
        session.tick();
        assertEquals(10, session.snapshot().score() - beforeClear);
        assertEquals(1, session.snapshot().lines());
        for (int[] cells : session.snapshot().cells())
            for (int cell : cells) assertEquals(0, cell);
    }

    @Test
    void 실제_B가_완성한_행은_행_점수만_받는다() {
        var session = GameTestSupport.startedSession(row(ItemType.BOMB), new tetris.component.block.OBlock());
        session.hardDrop();
        long beforeClear = session.snapshot().score();
        assertEquals(10, session.snapshot().clearingCells().size());
        session.tick();
        assertEquals(100, session.snapshot().score() - beforeClear);
        assertEquals(1, session.snapshot().lines());
    }

    private static void fill(Board board, int row, int col) {
        Block cell = GameTestSupport.cell();
        cell.setPosition(row, col);
        assertTrue(board.tryPlaceBlock(cell), "(" + row + ", " + col + ") 채우기 실패");
    }

    private static void fillSquare(Board board, int centerRow, int centerCol) {
        for (int row = centerRow - 1; row <= centerRow + 1; row++)
            for (int col = centerCol - 1; col <= centerCol + 1; col++)
                if (row >= -Board.MARGIN && row < Board.HEIGHT && col >= 0 && col < Board.WIDTH) fill(board, row, col);
    }

    private static Set<Cell> square(int centerRow, int centerCol) {
        Set<Cell> cells = new HashSet<>();
        for (int row = centerRow - 1; row <= centerRow + 1; row++)
            for (int col = centerCol - 1; col <= centerCol + 1; col++) cells.add(new Cell(row, col));
        return cells;
    }

    @Test
    void 가운데에서_터지면_채워진_3x3_9칸을_지우고_90점() {
        Board board = new Board();
        fillSquare(board, 10, 4);
        GameModifiers modifiers = new GameModifiers();

        ItemResult result = bomb.onLock(board, 10, 4, modifiers);

        assertEquals(square(10, 4), result.cells());
        assertTrue(result.rows().isEmpty());
        assertEquals(90, board.clearCells(result.cells()) * 10L);
    }

    @Test
    void 빈_칸은_지울_칸과_점수에서_빠진다() {
        Board board = new Board();
        fill(board, 10, 4); // 폭탄 칸 자신
        fill(board, 11, 3);
        fill(board, 9, 5);
        GameModifiers modifiers = new GameModifiers();

        ItemResult result = bomb.onLock(board, 10, 4, modifiers);

        assertEquals(Set.of(new Cell(10, 4), new Cell(11, 3), new Cell(9, 5)), result.cells());
        assertEquals(30, board.clearCells(result.cells()) * 10L);
    }

    @Test
    void 왼쪽_아래_모서리에서는_보드_밖이_잘려_4칸() {
        Board board = new Board();
        fillSquare(board, Board.HEIGHT - 1, 0);
        GameModifiers modifiers = new GameModifiers();

        ItemResult result = bomb.onLock(board, Board.HEIGHT - 1, 0, modifiers);

        assertEquals(Set.of(new Cell(18, 0), new Cell(18, 1), new Cell(19, 0), new Cell(19, 1)), result.cells());
        assertEquals(40, board.clearCells(result.cells()) * 10L);
    }

    @Test
    void 오른쪽_벽에서는_보드_밖_열이_잘린다() {
        Board board = new Board();
        fillSquare(board, 5, Board.WIDTH - 1);

        ItemResult result = bomb.onLock(board, 5, Board.WIDTH - 1, new GameModifiers());

        assertEquals(6, result.cells().size());
        assertTrue(result.cells().stream().allMatch(cell -> cell.col() < Board.WIDTH));
    }

    @Test
    void 숨은_줄의_칸도_지운다() {
        Board board = new Board();
        fillSquare(board, -1, 4); // 숨은 줄 -2, -1과 보이는 0행

        ItemResult result = bomb.onLock(board, -1, 4, new GameModifiers());

        assertEquals(square(-1, 4), result.cells());
    }

    @Test
    void 맨_위_숨은_줄_위로는_잘린다() {
        Board board = new Board();
        fillSquare(board, -2, 4);

        ItemResult result = bomb.onLock(board, -2, 4, new GameModifiers());

        assertEquals(6, result.cells().size());
        assertTrue(result.cells().stream().allMatch(cell -> cell.row() >= -Board.MARGIN));
    }

    @Test
    void 꽉_찬_행의_칸은_행_삭제로_지워지므로_빼고_점수도_세지_않는다() {
        Board board = new Board();
        fillSquare(board, 15, 4);
        for (int col = 0; col < Board.WIDTH; col++) {
            if (col < 3 || col > 5) fill(board, 16, col); // 16행을 꽉 채운다 (3~5열은 fillSquare가 채움)
        }
        assertTrue(board.isRowFull(16));
        GameModifiers modifiers = new GameModifiers();

        ItemResult result = bomb.onLock(board, 15, 4, modifiers);

        assertTrue(result.cells().stream().noneMatch(cell -> cell.row() == 16));
        assertEquals(6, result.cells().size());
        assertEquals(60, board.clearCells(result.cells()) * 10L);
    }

    @Test
    void 보드를_바꾸지_않는다() {
        Board board = new Board();
        fillSquare(board, 10, 4);
        int[][] before = board.copyCells();

        bomb.onLock(board, 10, 4, new GameModifiers());

        assertArrayEquals(before, board.copyCells());
    }

    @Test
    void 칸_점수는_점수_2배의_영향을_받지_않고_배율도_바꾸지_않는다() {
        Board board = new Board();
        fillSquare(board, 10, 4);
        GameModifiers modifiers = new GameModifiers();
        new DoubleScoreEffect().onLock(new Board(), 0, 0, modifiers);

        ItemResult result = bomb.onLock(board, 10, 4, modifiers);

        assertEquals(90, board.clearCells(result.cells()) * 10L);
        assertEquals(2, modifiers.scoreMultiplier());
    }

    @Test
    void 폭탄_블록이_행을_완성하면_그_행은_빼고_위아래_행만_지운다() {
        Board board = new Board();
        fillSquare(board, 15, 4); // (15, 4)가 폭탄 칸
        for (int col = 0; col < Board.WIDTH; col++) {
            if (col < 3 || col > 5) fill(board, 15, col);
        }
        assertTrue(board.isRowFull(15));
        GameModifiers modifiers = new GameModifiers();

        ItemResult result = bomb.onLock(board, 15, 4, modifiers);

        Set<Cell> expected = square(15, 4);
        expected.removeIf(cell -> cell.row() == 15);
        assertEquals(expected, result.cells());
        assertEquals(60, board.clearCells(result.cells()) * 10L);
    }

    @Test
    void 반복_호출은_같은_대상만_반환하고_보드와_지속효과를_바꾸지_않는다() {
        Board board = new Board();
        fill(board, 10, 4);
        fill(board, 3, 3);
        GameModifiers modifiers = new GameModifiers();

        ItemResult first = bomb.onLock(board, 10, 4, modifiers);
        assertEquals(first, bomb.onLock(board, 10, 4, modifiers));
        assertEquals(1, bomb.onLock(board, 3, 3, modifiers).cells().size());
        assertTrue(modifiers.effects().isEmpty());
        assertTrue(!board.isEmpty(10, 4) && !board.isEmpty(3, 3));
    }

    @Test
    void ItemType_B는_BombEffect를_효과로_갖는다() {
        assertEquals('B', ItemType.BOMB.symbol());
        assertInstanceOf(BombEffect.class, ItemType.BOMB.effect());
    }
}
