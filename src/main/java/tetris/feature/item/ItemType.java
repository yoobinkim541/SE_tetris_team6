package tetris.feature.item;

/**
 * 블록 한 칸에 붙는 아이템 종류. 표시 문자(symbol)는 아이템마다 달라야 한다.
 * 각 종류는 Lock될 때 실행할 효과(effect)를 하나씩 갖는다. 새 아이템은 여기에 한 줄과 ItemEffect 구현 하나만 추가하면 된다.
 * 무게추('W')는 블록 일부가 아니라 블록 전체가 아이템이고 움직임 규칙이 달라서 여기에 두지 않는다(WeightBlock).
 *
 * 가정(팀 합의 필요): Lock 단계에서 GameSession은 블록의 itemCellAt으로 얻은 ItemType의 effect()를 호출한다.
 * 그래서 GameSession은 LineClearEffect 같은 구체 클래스를 몰라도 된다.
 */
public enum ItemType {
    LINE_CLEAR('L', new LineClearEffect()),
    BOMB('B', new BombEffect()),
    DOUBLE_SCORE('D', new DoubleScoreEffect()),
    SLOW('S', new SlowEffect());

    private final char symbol;
    private final ItemEffect effect;

    ItemType(char symbol, ItemEffect effect) {
        this.symbol = symbol;
        this.effect = effect;
    }

    public char symbol() {
        return symbol;
    }

    /** 이 아이템이 붙은 블록이 Lock될 때 호출할 효과 */
    public ItemEffect effect() {
        return effect;
    }
}
