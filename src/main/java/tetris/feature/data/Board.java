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

    // 블록을 움직일 수 있는지 없는지를 판정 (다음 위치에 가상 블럭을 만든 후 lnBoard, OnBlock 확인)
    public boolean CanPlace(Block block, int nextRow, int nextCol){
          // 충돌 검사
          boolean inBoard = CrashDetector.IsInBoard(
                board,
                block.getShape(),
                nextRow,
                nextCol
          );

          boolean onBlock = CrashDetector.IsOnBlock(
                board,
                block.getShape(),
                nextRow,
                nextCol

          );
          return inBoard && !onBlock;
      }
    
    public boolean moveBlock(Block block, int nextRow, int nextCol) {
          // 블록 이동
          boolean canPlace = CanPlace(block, nextRow, nextCol);
          if(canPlace) {
           block.setPosition(nextRow, nextCol); 
           return true;  
          }
          return false;
      }

    public boolean PlaceBlock(Block block) {
        // 블록 배치
        if(block == null){
            throw new
            IllegalArgumentException("block cannot be null");
        }

        int row = block.getRow();
        int col = block.getCol();

        if(!CanPlace(block, row, col)){
            return false;
        }

        int[][] shape = block.getShape();
        
        for(int shapeRow = 0; shapeRow < shape.length; shapeRow++){
            for(int shapeCol = 0; shapeCol < shape[shapeRow].length; shapeCol++){
                int value = shape[shapeRow][shapeCol];

                if(value == 0) continue;

                int boardRow = block.getRow() + shapeRow;
                int boardCol = block.getCol() + shapeCol;

                board[boardRow][boardCol] = value;
            }
        }

        return true;
    }

    private boolean isRowFull(int row){
        if(row < 1 || row > HEIGHT){
            return false;
        }
        for(int col = 1; col <= WIDTH; col++){
            if(board[row][col] == 0) return false;
        }
        return true;
    }

    private void ClearLine(int row){
        // 한 줄 삭제 
        if(isRowFull(row)){
            for(int i = 1; i <= WIDTH; i++){
                board[row][i] = 0;
            }
        }

        
    }

    public void ClearLines(int row, int count) {
          // 여러 줄 삭제
        for(int i = row; i < row + count; i++){
            ClearLine(i);
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
        for(int row = 1; row <= HEIGHT; row++){
            if(!isRowFull(row)) continue;
            _result.add(Integer.valueOf(row));
        }
        return _result;
    }
  }
