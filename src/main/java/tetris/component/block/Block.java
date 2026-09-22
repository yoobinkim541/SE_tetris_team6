package tetris.component.block;

public abstract class Block {
    protected int[][] shape;
    private int row;
    private int col;

    protected Block(int[][] shape) {
        this.shape = shape;
    }

    public int[][] getShape() {
        return shape;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public void setPosition(int row, int col) {
        this.row = row;
        this.col = col;
    }
    
    public void setShape(int[][] newShape) {
        this.shape = newShape;
    }

    // 시계방향 90도 회전한 새 격자를 반환 (원본은 수정하지 않음)
    public int[][] rotateRight() {
        int n = shape.length;
        int[][] rotated = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                rotated[j][n - 1 - i] = shape[i][j];
            }
        }
        return rotated;
    }    

}

