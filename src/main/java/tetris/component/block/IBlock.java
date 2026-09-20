package tetris.component.block;

public class IBlock {

    // 4x4 격자. 1이 블럭이 차지하는 칸.
    private int[][] shape = {
        {0, 0, 0, 0},
        {1, 1, 1, 1},
        {0, 0, 0, 0},
        {0, 0, 0, 0}
    };

    // 보드에서의 위치 (격자의 왼쪽 위 칸 기준)
    private int row;
    private int col;

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

    //
    public void setShape(int[][] newShape) {
        this.shape = newShape;
    }
}
