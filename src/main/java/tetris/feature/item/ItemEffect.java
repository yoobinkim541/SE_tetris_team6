package tetris.feature.item;

/**
 * 아이템 하나가 Lock될 때 실행하는 효과. 아이템이 붙은 블록이 Lock된 직후, 줄을 지우기 전에 한 번 호출된다.
 * 한 인스턴스를 모든 게임이 같이 쓰므로(ItemType 상수) 효과 객체에 상태를 두지 않는다.
 * 남은 블록 수 같은 지속 상태는 GameModifiers에 둔다.
 */
public interface ItemEffect {
    /** 보드를 바꾸지 않고 지울 행·칸만 돌려준다. 지속 효과(점수 2배, 슬로우 등)는 modifiers에 등록한다 */
    ItemResult onLock(BoardView board, int itemRow, int itemCol, GameModifiers modifiers);
}
