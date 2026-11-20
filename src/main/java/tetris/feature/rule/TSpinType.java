package tetris.feature.rule;

/** 한 번의 Lock이 T-스핀인지 (TSP-1~4). NONE: 아님, MINI: T-스핀 미니, FULL: T-스핀 */
public enum TSpinType {
    NONE, MINI, FULL;

    public boolean isTSpin() {
        return this != NONE;
    }
}
