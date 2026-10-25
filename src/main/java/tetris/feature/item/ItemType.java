package tetris.feature.item;

/**
 * 아이템 종류 (ARC-1). 문자는 화면 표시용이며 서로 달라야 한다.
 * 무게추('W')는 블록 자체가 아이템이라 ItemEffect가 아니라 별도 블록 종류(WeightBlock)로 둔다 (ARC-3).
 * TODO(C 트랙): 각 항목에 ItemEffect 구현을 연결한다 (#56, #67~#69).
 */
public enum ItemType {
    LINE_CLEAR('L'),
    BOMB('B'),
    DOUBLE_SCORE('D'),
    SLOW('S');

    private final char symbol;

    ItemType(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }
}
