package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.JBlock;
import tetris.component.block.OBlock;
import tetris.feature.crash.CrashDetector;
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
        assertFalse(CrashDetector.CanPlace(board.copyCells(), cell, 18, 1)); // J의 아랫줄이 한 칸 내려와 차지
        assertFalse(CrashDetector.CanPlace(board.copyCells(), cell, 19, 8)); // O의 윗줄이 맨 아래로 내려옴
        assertTrue(CrashDetector.CanPlace(board.copyCells(), cell, 19, 2));  // 삭제된 줄의 나머지 칸은 비어 있음
        assertTrue(CrashDetector.CanPlace(board.copyCells(), cell, 16, 1));  // J의 윗줄이 내려가 원래 자리는 비어 있음
    }

    @Test
    void positionsAboveOrOutsideTheBoardAreInvalid() {
        Board board = new Board();
        Block cell = new Block(new int[][] {{1}}) {};
        assertFalse(CrashDetector.CanPlace(board.copyCells(), cell, -1, 0));
        assertFalse(CrashDetector.CanPlace(board.copyCells(), cell, 0, -1));
        assertFalse(CrashDetector.CanPlace(board.copyCells(), cell, 0, 10));
        assertFalse(CrashDetector.CanPlace(board.copyCells(), cell, 20, 0));
        assertTrue(CrashDetector.CanPlace(board.copyCells(), cell, 0, 0));
        assertTrue(CrashDetector.CanPlace(board.copyCells(), cell, 19, 9));
    }

    private static void place(Board board, Block block, int row, int col) {
        block.setPosition(row, col);
        assertTrue(board.TryPlaceBlock(block));
    }
}
