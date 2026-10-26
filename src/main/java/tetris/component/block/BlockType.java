package tetris.component.block;

/**
 * 블록 종류. 보드에 기록되는 셀 값은 ordinal() + 1 (I=1 … Z=7, 무게추 W=8).
 * W는 아이템 모드의 무게추 전용이라 일반 블록 생성 대상이 아니다. 생성할 때는 ordinary()를 쓴다.
 */
public enum BlockType {
    I, J, L, O, S, T, Z, W;

    private static final BlockType[] ORDINARY = {I, J, L, O, S, T, Z};

    /** 일반 블록 7종 (무게추 제외) */
    public static BlockType[] ordinary() {
        return ORDINARY.clone();
    }

    public int cellValue() {
        return ordinal() + 1;
    }

    public char letter() {
        return name().charAt(0);
    }
}
