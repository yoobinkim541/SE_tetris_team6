package tetris.component.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import tetris.feature.item.ItemType;

public abstract class Block implements Cloneable {
    protected int[][] shape;
    private int row;
    private int col;

    // 블록 하나에는 아이템이 최대 하나라서 칸 배열 대신 종류와 위치만 둔다. item이 null이면 일반 블록
    private ItemType item;
    private int itemRow;
    private int itemCol;

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
    
    /** 모양 격자의 (row, col) 칸에 붙은 아이템. 아이템이 없는 칸이나 격자 밖이면 empty */
    public Optional<ItemType> itemCellAt(int row, int col) {
        if (item == null || row != itemRow || col != itemCol) return Optional.empty();
        return Optional.of(item);
    }

    /**
     * 모양 격자의 (row, col) 칸에 아이템을 붙인다. 이후 회전·복사해도 같은 칸에 붙어 있다.
     * 격자 밖이거나 빈 칸이면 IllegalArgumentException, 이미 아이템이 붙어 있으면 IllegalStateException.
     */
    public void attachItem(int row, int col, ItemType type) {
        if (type == null) throw new IllegalArgumentException("type cannot be null");
        if (row < 0 || row >= shape.length || col < 0 || col >= shape[row].length)
            throw new IllegalArgumentException(String.format("cell (%d, %d) is outside the shape", row, col));
        if (shape[row][col] == 0)
            throw new IllegalArgumentException(String.format("cell (%d, %d) is empty", row, col));
        if (item != null) throw new IllegalStateException("block already has an item : " + item);

        item = type;
        itemRow = row;
        itemCol = col;
    }

    /** 채워진 칸 중 하나를 같은 확률로 골라 아이템을 붙인다. 예외 조건은 attachItem과 같다 */
    public void attachItemToRandomCell(ItemType type, Random random) {
        if (random == null) throw new IllegalArgumentException("random cannot be null");

        List<int[]> filled = new ArrayList<>();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) filled.add(new int[] {r, c});
            }
        }
        int[] cell = filled.get(random.nextInt(filled.size()));
        attachItem(cell[0], cell[1], type);
    }

    /** 무게추 여부. GameSession의 하강·회전 분기는 이 값 하나로 판단한다 (ARC-3) */
    public boolean isWeight() {
        return false;
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

    /** 모양과 회전 상태, 붙은 아이템 위치를 함께 시계방향 90° 돌린다. 돌려도 되는지는 RotationPolicy가 먼저 판정한다 */
    public void rotateClockwise() {
        int n = shape.length;
        shape = rotateRight();
        rotation = (rotation + 1) % 4;

        // rotateRight와 같은 좌표 변환: (r, c) → (c, n - 1 - r)
        int rotatedRow = itemCol;
        itemCol = n - 1 - itemRow;
        itemRow = rotatedRow;
    }

    /** 스냅샷용 깊은 복사. 서브클래스(종류)와 붙은 아이템은 유지되고, 복사본을 바꿔도 원본에 영향이 없다 */
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

