package tetris.feature;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.feature.crash.CrashDetector;

public class CrashTest {
    private static final int ROWS = 22;
    private static final int COLS = 10;
    private static final int BASELINE = 2;

    private int[][] board;

    @BeforeEach
    void setUp() {
        board = new int[ROWS][COLS];
    }

    private static Block at(Block block, int row, int col) {
        block.setPosition(row, col);
        return block;
    }

    //#region IsInBoard
    @Test
    void IsInBoard_왼쪽위_모서리는_안쪽() {
        assertTrue(CrashDetector.IsInBoard(board, at(new OBlock(), 0, 0)));
    }

    @Test
    void IsInBoard_오른쪽아래_모서리는_안쪽() {
        assertTrue(CrashDetector.IsInBoard(board, at(new OBlock(), ROWS - 2, COLS - 2)));
    }

    @Test
    void IsInBoard_바닥_아래로_나가면_false() {
        assertFalse(CrashDetector.IsInBoard(board, at(new OBlock(), ROWS - 1, 0)));
    }

    @Test
    void IsInBoard_오른쪽_벽_넘으면_false() {
        assertFalse(CrashDetector.IsInBoard(board, at(new OBlock(), 0, COLS - 1)));
    }

    @Test
    void IsInBoard_왼쪽_벽_넘으면_false() {
        assertFalse(CrashDetector.IsInBoard(board, at(new OBlock(), 0, -1)));
    }

    @Test
    void IsInBoard_I블록의_빈_행은_보드_밖이어도_true() {
        //IBlock은 1행만 차 있으므로 row=-1이면 실제 칸은 0행
        assertTrue(CrashDetector.IsInBoard(board, at(new IBlock(), -1, 0)));
        assertTrue(CrashDetector.IsInBoard(board, at(new IBlock(), ROWS - 2, 0)));
        assertFalse(CrashDetector.IsInBoard(board, at(new IBlock(), ROWS - 1, 0)));
    }

    @Test
    void IsInBoard_세로_I블록의_빈_열은_보드_밖이어도_true() {
        int[][] vertical = new IBlock().rotateRight(); //2열만 차 있음
        assertTrue(CrashDetector.IsInBoard(board, vertical, 0, -2));
        assertTrue(CrashDetector.IsInBoard(board, vertical, 0, COLS - 3));
        assertFalse(CrashDetector.IsInBoard(board, vertical, 0, COLS - 2));
    }
    //#endregion

    //#region IsOnBlock
    @Test
    void IsOnBlock_빈_보드면_false() {
        assertFalse(CrashDetector.IsOnBlock(board, at(new OBlock(), 10, 4)));
    }

    @Test
    void IsOnBlock_쌓인_칸과_겹치면_true() {
        board[5][3] = 1;
        assertTrue(CrashDetector.IsOnBlock(board, at(new OBlock(), 4, 3)));
    }

    @Test
    void IsOnBlock_바로_위에_붙어있기만_하면_false() {
        board[5][3] = 1;
        assertFalse(CrashDetector.IsOnBlock(board, at(new OBlock(), 3, 3)));
    }

    @Test
    void IsOnBlock_칸_값이_1이_아니어도_찬_칸으로_판정() {
        board[5][3] = 7; //색상 코드
        assertTrue(CrashDetector.IsOnBlock(board, at(new OBlock(), 4, 3)));
    }

    @Test
    void IsOnBlock_shape의_빈_칸은_겹침으로_안_침() {
        board[0][0] = 1; //TBlock의 (0,0)은 빈 칸
        assertFalse(CrashDetector.IsOnBlock(board, at(new TBlock(), 0, 0)));
    }

    @Test
    void IsOnBlock_오버행_옆으로_파고들면_true() {
        board[16][2] = 1; //아래(17,2)는 비어 있는 돌출부
        assertTrue(CrashDetector.IsOnBlock(board, at(new OBlock(), 16, 1)));
    }

    @Test
    void IsOnBlock_보드_밖_좌표여도_예외_없음() {
        assertDoesNotThrow(() -> CrashDetector.IsOnBlock(board, at(new OBlock(), -1, -1)));
        assertDoesNotThrow(() -> CrashDetector.IsOnBlock(board, at(new OBlock(), ROWS - 1, COLS - 1)));

        board[0][0] = 1;
        assertTrue(CrashDetector.IsOnBlock(board, at(new OBlock(), -1, -1)));
    }
    //#endregion

    //#region IsRowFull
    @Test
    void IsRowFull_꽉_찬_줄이면_true() {
        for (int col = 0; col < COLS; col++) board[ROWS - 1][col] = 1 + col; //색상 코드 섞음
        assertTrue(CrashDetector.IsRowFull(board, ROWS - 1));
    }

    @Test
    void IsRowFull_한_칸이라도_비면_false() {
        for (int col = 0; col < COLS - 1; col++) board[ROWS - 1][col] = 1;
        assertFalse(CrashDetector.IsRowFull(board, ROWS - 1));
    }

    @Test
    void IsRowFull_범위_밖_행이면_false() {
        assertFalse(CrashDetector.IsRowFull(board, -1));
        assertFalse(CrashDetector.IsRowFull(board, ROWS));
    }
    //#endregion

    //#region 게임 흐름 시나리오
    //더 이상 못 내려갈 때까지 떨어뜨리고 최종 row 반환
    private int dropToBottom(Block block) {
        while (CrashDetector.IsInBoard(board, block.getShape(), block.getRow() + 1, block.getCol())
                && !CrashDetector.IsOnBlock(board, block.getShape(), block.getRow() + 1, block.getCol())) {
            block.setPosition(block.getRow() + 1, block.getCol());
        }
        return block.getRow();
    }

    @Test
    void 시나리오_빈_보드에서_O블록은_바닥까지_내려감() {
        assertEquals(ROWS - 2, dropToBottom(at(new OBlock(), 0, 4)));
    }

    @Test
    void 시나리오_쌓인_블록_바로_위에서_멈춤() {
        board[ROWS - 1][4] = 1;
        assertEquals(ROWS - 3, dropToBottom(at(new OBlock(), 0, 4)));
    }

    @Test
    void 시나리오_I블록은_빈_행_때문에_row가_한_칸_더_내려감() {
        //IBlock은 1행이 실제 칸이라 앵커 row = 바닥 - 2
        assertEquals(ROWS - 2, dropToBottom(at(new IBlock(), 0, 3)));
    }
    //#endregion
}
