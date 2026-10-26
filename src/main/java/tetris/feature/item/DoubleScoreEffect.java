package tetris.feature.item;

/**
 * 점수 2배 아이템('D')의 효과. 지우는 칸은 없고 GameModifiers에 점수 2배를 켜기만 한다.
 * 남은 블록 수 계산은 GameModifiers가 한다.
 */
public class DoubleScoreEffect implements ItemEffect {
    @Override
    public ItemResult onLock(BoardView board, int itemRow, int itemCol, GameModifiers modifiers) {
        modifiers.activateDoubleScore();
        return ItemResult.none();
    }
}
