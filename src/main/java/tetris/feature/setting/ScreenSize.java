package tetris.feature.setting;

/** 화면 크기 = 셀 표시 크기만 변경 (논리 보드는 항상 20x10). RND-1 */
public enum ScreenSize {
    SMALL(20), MEDIUM(30), LARGE(40);

    private final int cellPixels;

    ScreenSize(int cellPixels) {
        this.cellPixels = cellPixels;
    }

    public int cellPixels() {
        return cellPixels;
    }

    /** Small -> Medium -> Large -> Small 순환 */
    public ScreenSize next() {
        throw new UnsupportedOperationException("TODO");
    }
}
