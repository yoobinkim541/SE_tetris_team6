package tetris.component.block;

/** 블록 종류. 보드에 기록되는 셀 값은 ordinal() + 1 (I=1 … Z=7). */
public enum BlockType {
    I, J, L, O, S, T, Z;

    public int cellValue() {
        return ordinal() + 1;
    }

    public char letter() {
        return name().charAt(0);
    }
}
