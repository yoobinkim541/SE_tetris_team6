package tetris.component.block;

public abstract class Block implements Cloneable {
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

    // 회전 상태 0..3 (0 = 초기 모양, 시계방향으로 +1). 모양과 어긋나지 않도록 rotateClockwise에서만 바뀐다
    private int rotation;

    public int getRotation() {
        return rotation;
    }

    // 시계방향 90도 회전한 새 격자를 반환 (원본은 수정하지 않음). 회전 전 판정용
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

    /** 모양과 회전 상태를 함께 시계방향 90° 돌린다. 돌려도 되는지는 RotationPolicy가 먼저 판정한다 */
    public void rotateClockwise() {
        shape = rotateRight();
        rotation = (rotation + 1) % 4;
    }

    /** 스냅샷용 깊은 복사. 서브클래스(종류)는 유지되고, 복사본을 바꿔도 원본에 영향이 없다 */
    public Block copy() {
        try {
            Block copy = (Block) super.clone();
            copy.shape = new int[shape.length][];
            for (int i = 0; i < shape.length; i++) copy.shape[i] = shape[i].clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}

