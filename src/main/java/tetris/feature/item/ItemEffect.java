package tetris.feature.item;

/** 아이템 하나의 Lock 시점 효과 (ARC-2). Lock 직후(§5 L2) 한 번 호출된다. */
public interface ItemEffect {
    /** 보드를 바꾸지 않고 지울 대상만 돌려준다. 지속 효과(D, S)는 modifiers에 등록한다 */
    ItemResult onLock(BoardView board, int itemRow, int itemCol, GameModifiers modifiers);
}
