package tetris.feature.data;

import tetris.component.block.Block;
import tetris.feature.crash.CrashDetector;
import java.util.*;

public class Board {
      public static final int WIDTH = 10;
      public static final int HEIGHT = 20;
      public static final int WALL = -1;
      public static final int EMPTY = 0;
      public static final int WALL_MARGIN = 2;

      private final int[][] board;

      public Board() {
          board = CreateDefaultBoard();
      }

    private int[][] CreateDefaultBoard() { // 기본 테트리스 보드 생성 22 * 12 size 벽 -1 공백 0
        int[][] board = new int[HEIGHT + WALL_MARGIN][WIDTH + WALL_MARGIN]; 
            for(int i = 0; i <= WIDTH + 1; i++){
                board[0][i] = WALL;
                board[HEIGHT + 1][i] = WALL;
            }

            for(int i = 1; i <= HEIGHT; i++){
                board[i][0] = WALL;
                board[i][WIDTH + 1] = WALL;
            }

            for (int i = 1; i <= HEIGHT; i++) {
                for(int j = 1; j <= WIDTH; j++){
                    board[i][j] = EMPTY;
                }
            }

            return board;
    }

    // 블록 배치
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

    private boolean isRowFull(int row) {
        if(row < 1 || row > HEIGHT) {
            return false;
        }
        for(int col = 1; col <= WIDTH; col++) {
            if (board[row][col] == 0) return false;
        }
        return true;
    }

    private void ClearLine(int row) {
        // 한 줄 삭제 - GetFullRowCount를 경유하면 필요없는 guard인데 아직은 보류.
        // 만약 한 줄이 다 채워지지 않았어도 없애야 하는 상황이 생기면 guard 삭제하면 됨.
        if (isRowFull(row)) {
            for(int i = 1; i <= WIDTH; i++) {
                board[row][i] = 0;
            }
        }
    }

    public void ClearLines(ArrayList<Integer> targets) {
        // 여러 줄 삭제
        for(Integer row : targets) {
            ClearLine(row);
        }

        // 점수 로직 구현시 구현 사항
        // count = 1 > 100점
        // count = 2 > 200점 + 50점
        // count = 3 > 300점 + 100점
        // count = 4 > 400점 + 200점
    }

    public ArrayList<Integer> GetFullRowCount(){
        ArrayList<Integer> _result = new ArrayList<Integer>();

        // 현재 보드에 row 가 꽉찬 줄이 몇개인지 확인  
        for (int row = 1; row <= HEIGHT; row++) {
            if (!isRowFull(row)) continue;
            _result.add(Integer.valueOf(row));
        }
        return _result;
    }
}
