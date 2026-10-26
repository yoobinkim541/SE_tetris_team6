package tetris.feature.item;

/**
 * 슬로우 아이템('S')의 효과. 지우는 칸은 없고 GameModifiers에 슬로우를 켜기만 한다.
 * 어떤 블록이 느려질지와 남은 블록 수 계산은 GameModifiers가 한다.
 */
public class SlowEffect implements ItemEffect {
    @Override
    public ItemResult onLock(BoardView board, int itemRow, int itemCol, GameModifiers modifiers) {
        modifiers.activateSlow();
        return ItemResult.none();
    }
}
