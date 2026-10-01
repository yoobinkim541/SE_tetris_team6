package tetris.feature.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tetris.component.block.Block;
import tetris.feature.crash.CrashDetector;

/**
 * 20x10 논리 보드 (명세 G-1~G-3). 보드 배열의 유일한 변경 지점이다.
 *
 * 공개 API는 항상 0-기반 논리 좌표를 쓴다: 보이는 영역은 row 0..19(위->아래), col 0..9(왼->오)이고,
 * 그 위의 숨은 줄은 row -MARGIN..-1이다.
 *
 * 내부 배열은 상하좌우로 MARGIN칸씩 늘린 ARRAY_ROWS x ARRAY_COLS(24x14)다.
 *   - 위 MARGIN행: 블록이 지나다니는 빈 숨은 줄 (I 블록 Spawn·회전용)
 *   - 아래 MARGIN행, 좌우 MARGIN열: WALL. 벽이 충돌을 막으므로 배열 범위를 따로 검사하지 않아도 된다
 * I 블록 회전은 보드 밖으로 최대 2칸 나가므로 MARGIN = 2다.
 * 논리 좌표 -> 배열 인덱스 변환(+MARGIN)은 canPlace와 setCell, 그리고 행 단위 처리(isRowFull·clearRows·copyCells)에서만 한다.
 */
public class Board {
    public static final int WIDTH = 10;   // 보이는 영역 열 수
    public static final int HEIGHT = 20;  // 보이는 영역 행 수
    public static final int MARGIN = 2;   // 상하좌우 여유 칸 수
    public static final int ARRAY_ROWS = HEIGHT + 2 * MARGIN; // 내부 배열 행 수 (24)
    public static final int ARRAY_COLS = WIDTH + 2 * MARGIN;  // 내부 배열 열 수 (14)
    public static final int EMPTY = 0;
    public static final int WALL = -1;

    private final int[][] board;

    public Board() {
        board = createDefaultBoard();
    }
    
    // 기본 보드 생성: 위 MARGIN행은 EMPTY(숨은 줄), 아래 MARGIN행과 좌우 MARGIN열은 WALL
    private int[][] createDefaultBoard() {
        int[][] cells = new int[ARRAY_ROWS][ARRAY_COLS];
        for (int row = 0; row < ARRAY_ROWS; row++) {
            for (int col = 0; col < ARRAY_COLS; col++) {
                boolean isFloor = row >= MARGIN + HEIGHT;
                boolean isSide = col < MARGIN || col >= MARGIN + WIDTH;
                cells[row][col] = (isFloor || isSide) ? WALL : EMPTY;
            }
        }
        return cells;
    }

    /** 새 게임/Restart: 숨은 줄을 포함한 모든 칸을 비운다 (벽은 유지) */
    public void reset() {
        for (int row = 0; row < MARGIN + HEIGHT; row++) {
            Arrays.fill(board[row], MARGIN, MARGIN + WIDTH, EMPTY);
        }
    }

    /** 앵커를 논리 좌표 (row, col)에 뒀을 때 유효한지. 상태를 바꾸지 않는다 */
    public boolean canPlace(Block block, int row, int col) {
        return canPlace(block.getShape(), row, col);
    }

    /** 회전 전 확인용: 주어진 모양을 논리 좌표 (row, col)에 뒀을 때 유효한지. 상태를 바꾸지 않는다 */
    public boolean canPlace(int[][] shape, int row, int col) {
        return CrashDetector.CanPlace(board, shape, row + MARGIN, col + MARGIN);
    }

    // 논리 좌표 (row, col)의 칸에 값을 쓴다
    private void setCell(int row, int col, int value) {
        board[row + MARGIN][col + MARGIN] = value;
    }

    /** Lock: 블록 칸을 종류 값(1..7)으로 기록한다 (LCK-4). 놓을 수 없는 위치면 아무것도 바꾸지 않고 false */
    public boolean tryPlaceBlock(Block block) {
        if (block == null) throw new IllegalArgumentException("block cannot be null");
        if (!canPlace(block, block.getRow(), block.getCol())) return false;

        int value = block.getType().cellValue();
        int[][] shape = block.getShape();
        
        for (int _row = 0; _row < shape.length; _row++) {
            for (int _col = 0; _col < shape[_row].length; _col++) {
                if(shape[_row][_col] == 0) continue;

                setCell(block.getRow() + _row, block.getCol() + _col, value);
            }
        }

        return true;
    }

    /** 한 행의 10칸이 모두 채워졌는지 (row: 0..19). 범위 밖이면 false */
    public boolean isRowFull(int row) {
        if (row < 0 || row >= HEIGHT) return false;           // 숨은 줄·바닥 벽은 검사하지 않음
        return CrashDetector.IsRowFull(board, row + MARGIN);  // 좌우 벽은 WALL(≠0)이라 결과에 영향 없음
    }

    /** 완성된 행 번호 목록 (0-기반, 위->아래 순) */
    public List<Integer> findFullRows() {
        List<Integer> result = new ArrayList<>();
        for (int row = 0; row < HEIGHT; row++) {
            if (isRowFull(row)) result.add(row);
        }
        return result;
    }

    /** 주어진 완성 행을 모두 삭제하고, 위쪽 행을 아래로 내리며, 맨 위는 빈 행으로 채운다 (CLR-1, CLR-2) */
    public void clearRows(List<Integer> targets) {
        Set<Integer> completed = new HashSet<>();
        for (int row : targets) {
            if (isRowFull(row)) completed.add(row + MARGIN);
        }

        // 배열 인덱스 기준. 숨은 줄까지 함께 내리고, 좌우 벽 열은 건드리지 않는다
        int writeRow = MARGIN + HEIGHT - 1;
        for (int readRow = MARGIN + HEIGHT - 1; readRow >= 0; readRow--) {
            if (completed.contains(readRow)) continue;
            System.arraycopy(board[readRow], MARGIN, board[writeRow], MARGIN, WIDTH);
            writeRow--;
        }
        while (writeRow >= 0) {
            Arrays.fill(board[writeRow], MARGIN, MARGIN + WIDTH, EMPTY);
            writeRow--;
        }
    }

    /** Hard Drop 거리:블록을 아래로 몇 칸 내릴 수 있는지 (0 이상). 상태를 바꾸지 않는다 */
    public int getDropDistance(Block block) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 한 칸의 값 (0..7). 범위 밖이면 IllegalArgumentException */
    public int getCell(int row, int col) {
        throw new UnsupportedOperationException("TODO");
    }

    /** UI/스냅샷용 보이는 영역 20x10 복사본 (숨은 줄·벽 제외). 수정해도 게임에 영향이 없다 */
    public int[][] copyCells() {
        int[][] copy = new int[HEIGHT][];

        for (int row = 0; row < copy.length; row++) {
            copy[row] = Arrays.copyOfRange(board[row + MARGIN], MARGIN, MARGIN + WIDTH);
        }
        
        return copy;
    }
}
