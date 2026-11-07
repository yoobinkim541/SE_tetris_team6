package tetris.feature.versus.api;

/**
 * 대전 모드 3종 (R3-MODES). 싱글 {@code GameMode}(CLASSIC/ITEM)에 대전 개념을 넣지 않고 따로 둔다.
 * 엔진에 넘길 때는 {@link #itemsEnabled()}로 각 세션의 싱글 모드를 고른다.
 */
public enum BattleMode {
    CLASSIC(false, false),
    ITEM(true, false),
    /** 시간제한. 일반 블록만 쓴다 (D-05) */
    TIMED(false, true);

    private final boolean itemsEnabled;
    private final boolean timed;

    BattleMode(boolean itemsEnabled, boolean timed) {
        this.itemsEnabled = itemsEnabled;
        this.timed = timed;
    }

    /** 아이템이 등장하는지 (R3-ITEM). ITEM만 true */
    public boolean itemsEnabled() {
        return itemsEnabled;
    }

    /** 마감 시각에 점수로 승패를 가리는지 (R3-TIME). TIMED만 true */
    public boolean timed() {
        return timed;
    }
}
