package tetris.feature.crash;

import tetris.component.block.*;

public class CrashDetector {
    //#region 판정 raw 로직
    // 명세 SPN-4/14장: Game Over는 Spawn 충돌로만 판정한다.
    // 상단 도달 여부(IsBlockOnTop)로는 판정하지 않으므로 해당 메서드는 제거했다.

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

    // 행 완성 판정은 Board.isRowFull이 담당한다 (보드 변경·행 규칙은 Board 소관)
    //#endregion
}
