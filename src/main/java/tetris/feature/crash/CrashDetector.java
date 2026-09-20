package tetris.feature.crash;

import tetris.component.block.*;

public class CrashDetector {
    //#region 판정 raw 로직
    //Block이 Board의 최상단에 닿았는지 확인(게임오버 판정)
    public static boolean IsBlockOnTop(int[][] board, int baseline) {
        if (baseline < 0 || baseline >= board.length) 
            throw new IllegalArgumentException(String.format("baseline should be between %d and %d. Value : %d", 0, board.length - 1, baseline));

        for (int _row = 0; _row < baseline; _row++) {
            for (int _col = 0; _col < board[_row].length; _col++) {
                if (board[_row][_col] != 0) return true;
            }
        }
        
        return false;
    }

    //Block의 유효한 부분이 Board index 내부 범위에 있는지 판정
    public static boolean IsInBoard(int[][] board, int[][] shape, int offsetRow, int offsetCol) {
        for (int _row = 0; _row < shape.length; _row++) {
            for (int _col = 0; _col < shape[_row].length; _col++) {
                if (shape[_row][_col] == 0) continue;

                int row = offsetRow + _row;
                int col = offsetCol + _col;

                if (row < 0 || row >= board.length) return false;
                else if (col < 0 || col >= board[row].length) return false;
            }
        }

        return true;
    }

    public static boolean IsInBoard(int[][] board, Block block) {
        return IsInBoard(board, block.getShape(), block.getRow(), block.getCol());
    }

    //Block을 해당 위치에 배치했을 때 board에 있는 블록과 겹치는 부분이 있는지 판정
    public static boolean IsOnBlock(int[][] board, int[][] shape, int offsetRow, int offsetCol) {
        for (int _row = 0; _row < shape.length; _row++) {
            for (int _col = 0; _col < shape[_row].length; _col++) {
                if (shape[_row][_col] == 0) continue;

                int row = offsetRow + _row;
                int col = offsetCol + _col;

                if (row < 0 || row >= board.length) continue;
                else if (col < 0 || col >= board[row].length) continue;

                if (board[row][col] != 0) return true;
            }
        }

        return false;
    }

    public static boolean IsOnBlock(int[][] board, Block block) {
        return IsOnBlock(board, block.getShape(), block.getRow(), block.getCol());
    }

    //주어진 row index가 꽉 차있는지 판정
    public static boolean IsRowFull(int[][] board, int row) {
        //check is valid row index
        if (row < 0 || row >= board.length) return false;

        for (int index = 0; index < board[row].length; index++) {
            if (board[row][index] == 0) return false;
        }

        return true;
    }
    //#endregion
}
