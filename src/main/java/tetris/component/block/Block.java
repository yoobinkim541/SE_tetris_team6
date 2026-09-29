package tetris.component.block;

public abstract class Block {
    protected int[][] shape;
    private int row;
    private int col;

    protected Block(int[][] shape) {
        this.shape = shape;
    }

    protected Block(int[][] shape, int row, int col) {
        this(shape);
        setPosition(row, col);
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
    
    // 블록 종류 (보드에 기록할 셀 값 1..7 / 색·문자 결정). 7개 서브클래스가 override한다
    public BlockType getType() {
        throw new UnsupportedOperationException("서브클래스에서 override 필요");
    }

    // 회전 상태 0..3 (0 = 초기 모양, 시계방향으로 +1). RotationPolicy가 회전 성공 시 갱신한다
    private int rotation;

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
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

