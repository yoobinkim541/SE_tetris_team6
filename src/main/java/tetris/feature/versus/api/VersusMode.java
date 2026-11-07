package tetris.feature.versus.api;

/**
 * 대전 모드 3종 (R3-MODES). 싱글 플레이 {@code GameMode}(CLASSIC/ITEM)와 섞지 않는다.
 * 엔진에 넘길 때는 {@link #itemsEnabled()}로 싱글 모드를 고른다.
 */
public enum VersusMode {
    NORMAL(false, false),
    ITEM(true, false),
    TIME_LIMIT(false, true);

    private final boolean itemsEnabled;
    private final boolean timeLimited;

    VersusMode(boolean itemsEnabled, boolean timeLimited) {
        this.itemsEnabled = itemsEnabled;
        this.timeLimited = timeLimited;
    }

    /** 아이템이 등장하는지. ITEM만 true */
    public boolean itemsEnabled() {
        return itemsEnabled;
    }

    /** 타이머 종료 시 점수로 승패를 가리는지 (R3-TIME). TIME_LIMIT만 true */
    public boolean timeLimited() {
        return timeLimited;
    }
}
