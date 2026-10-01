package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.JBlock;
import tetris.component.block.OBlock;
import tetris.feature.data.Board;

/** 좌표는 명세 G-1의 0-기반 논리 좌표 (row 0..19, col 0..9) */
class BoardTest {
    @Test
    void clearingACompletedRowMovesTheRowsAboveItDown() {
        Board board = new Board();
        // I는 행렬 1행에 값이 있으므로 앵커 row 18 -> 실제 칸은 row 19(맨 아래)
        place(board, new IBlock(), 18, 0);
        place(board, new IBlock(), 18, 4);
        place(board, new OBlock(), 18, 8);
        place(board, new JBlock(), 16, 1);

        board.clearRows(board.findFullRows());

        Block cell = new Block(new int[][] {{1}}) {};
        assertTrue(board.findFullRows().isEmpty());
        assertFalse(board.canPlace(cell, 18, 1)); // J의 아랫줄이 한 칸 내려와 차지
        assertFalse(board.canPlace(cell, 19, 8)); // O의 윗줄이 맨 아래로 내려옴
        assertTrue(board.canPlace(cell, 19, 2));  // 삭제된 줄의 나머지 칸은 비어 있음
        assertTrue(board.canPlace(cell, 16, 1));  // J의 윗줄이 내려가 원래 자리는 비어 있음
    }

    @Test
    void positionsOutsideTheBoardAreInvalid() {
        Board board = new Board();
        Block cell = new Block(new int[][] {{1}}) {};
        assertFalse(board.canPlace(cell, 0, -1));                    // 왼쪽 벽
        assertFalse(board.canPlace(cell, 0, Board.WIDTH));           // 오른쪽 벽
        assertFalse(board.canPlace(cell, Board.HEIGHT, 0));          // 바닥 벽
        assertFalse(board.canPlace(cell, -Board.MARGIN - 1, 0));     // 숨은 줄보다 위
        assertTrue(board.canPlace(cell, 0, 0));
        assertTrue(board.canPlace(cell, Board.HEIGHT - 1, Board.WIDTH - 1));
    }

    @Test
    void hiddenRowsAboveTheVisibleAreaAreValid() {
        Board board = new Board();
        Block cell = new Block(new int[][] {{1}}) {};
        assertTrue(board.canPlace(cell, -1, 0));
        assertTrue(board.canPlace(cell, -Board.MARGIN, Board.WIDTH - 1));
        assertTrue(board.canPlace(new IBlock().rotateRight(), -Board.MARGIN, 3)); // Spawn 직후 세로 I (숨은 줄에 걸침)
    }

    @Test
    void copyCellsReturnsOnlyTheVisibleArea() {
        Board board = new Board();
        int[][] cells = board.copyCells();
        assertTrue(cells.length == Board.HEIGHT && cells[0].length == Board.WIDTH);
        for (int[] row : cells) for (int value : row) assertTrue(value == Board.EMPTY); // 벽(-1)이 섞여 있지 않음
    }

    private static void place(Board board, Block block, int row, int col) {
        block.setPosition(row, col);
        assertTrue(board.TryPlaceBlock(block));
    }
}
