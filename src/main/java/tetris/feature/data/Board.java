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
 * 공개 API는 항상 0-기반 논리 좌표를 쓴다: row 0..19(위->아래), col 0..9(왼->오).
 * 내부 배열은 판정을 단순하게 하려고 벽 테두리를 둔 22x12이며(위 0행, 아래 21행, 좌 0열, 우 11열),
 * 바깥에서는 이 내부 좌표를 볼 수 없다.
 */
public class Board {
    public static final int WIDTH = 10;
    public static final int HEIGHT = 20;
    public static final int WALL = -1;
    public static final int EMPTY = 0;

    private static final int WALL_MARGIN = 2;
    private static final int OFFSET = 1; // 논리 좌표 -> 내부 배열 좌표

    private final int[][] board;

    public Board() {
        board = createDefaultBoard();
    }
    
    // 기본 테트리스 보드 생성: 22 x 12, 테두리 WALL(-1), 내부 EMPTY(0)
    private int[][] createDefaultBoard() {
        int[][] cells = new int[HEIGHT + WALL_MARGIN][WIDTH + WALL_MARGIN];
        for (int i = 0; i <= WIDTH + 1; i++) {
            cells[0][i] = WALL;
            cells[HEIGHT + 1][i] = WALL;
        }
        for (int i = 1; i <= HEIGHT; i++) {
            cells[i][0] = WALL;
            cells[i][WIDTH + 1] = WALL;
        }
        return cells;
    }

    /** 새 게임/Restart: 모든 칸을 비운다 */
    public void reset() {
        for (int row = 1; row <= HEIGHT; row++) {
            Arrays.fill(board[row], 1, WIDTH + 1, EMPTY);
        }
    }

    public boolean TryPlaceBlock(Block block) {
        if (block == null) throw new IllegalArgumentException("block cannot be null");
        if (!CrashDetector.CanPlace(board, block)) return false;

        int[][] shape = block.getShape();
        
        for (int _row = 0; _row < shape.length; _row++) {
            for (int _col = 0; _col < shape[_row].length; _col++) {
                if(shape[_row][_col] == 0) continue;

                int boardRow = block.getRow() + _row;
                int boardCol = block.getCol() + _col;

                board[boardRow][boardCol] = shape[_row][_col];
            }
        }

        return true;
    }

    public void RemoveBlock(Block block) {
        if (block == null) throw new IllegalArgumentException("block cannot be null");

        int[][] shape = block.getShape();
        
        for (int _row = 0; _row < shape.length; _row++) {
            for (int _col = 0; _col < shape[_row].length; _col++) {
                if(shape[_row][_col] == 0) continue;

                int boardRow = block.getRow() + _row;
                int boardCol = block.getCol() + _col;

                board[boardRow][boardCol] = 0;
            }
        }
    }

    /** 한 행의 10칸이 모두 채워졌는지 (row: 0..19). 범위 밖이면 false */
    public boolean isRowFull(int row) {
        if (row < 0 || row >= HEIGHT) {
            return false;
        }
        for (int col = 1; col <= WIDTH; col++) {
            if (board[row + OFFSET][col] == EMPTY) return false;
        }
        return true;
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
            if (isRowFull(row)) completed.add(row + OFFSET);
        }

        int writeRow = HEIGHT;
        for (int readRow = HEIGHT; readRow >= 1; readRow--) {
            if (completed.contains(readRow)) continue;
            System.arraycopy(board[readRow], 1, board[writeRow], 1, WIDTH);
            writeRow--;
        }
        while (writeRow >= 1) {
            Arrays.fill(board[writeRow], 1, WIDTH + 1, EMPTY);
            writeRow--;
        }
    }

    /** Spawn 앵커 열 = floor((WIDTH - n) / 2), n = 블록 행렬 크기 (SPN-1). I,J,L,S,T,Z = 3, O = 4 */
    public int getSpawnColumn(Block block) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * 블록을 Spawn 위치(row 0, getSpawnColumn, rotation 0)에 놓는다 (SPN-1, SPN-3).
     * 유효하면 앵커를 옮기고 true, 기존 고정 칸과 겹치면 상태를 바꾸지 않고 false (= Game Over 조건).
     */
    public boolean placeAtSpawn(Block block) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Hard Drop 거리:블록을 아래로 몇 칸 내릴 수 있는지 (0 이상). 상태를 바꾸지 않는다 */
    public int getDropDistance(Block block) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 한 칸의 값 (0..7). 범위 밖이면 IllegalArgumentException */
    public int getCell(int row, int col) {
        throw new UnsupportedOperationException("TODO");
    }

    /** UI/스냅샷용 20x10 복사본 (벽 제외). 수정해도 게임에 영향이 없다 */
    public int[][] copyCells() {
        int[][] copy = new int[HEIGHT + 2 * OFFSET][WIDTH + 2 * OFFSET];

        for (int row = 0; row < copy.length; row++) {
            copy[row] = Arrays.copyOfRange(board[row], 0, board[row].length);
        }
        
        return copy;
    }
}
