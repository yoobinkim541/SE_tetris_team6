package tetris.feature.item;

import java.util.Set;

/**
 * 줄 삭제 아이템('L')의 효과. 아이템 칸이 놓인 행을, 꽉 차 있지 않아도 지울 행으로 돌려준다.
 * 실제 삭제·점수 계산은 호출하는 쪽(GameSession)이 하므로 보드와 지속 효과는 건드리지 않는다.
 *
 * 가정(팀 합의 필요):
 * - itemRow, itemCol은 블록 격자 좌표가 아니라 보드 논리 좌표(블록 위치 + 격자 위치)다.
 * - 숨은 줄(row -2, -1)에 놓여도 그 행을 그대로 돌려준다. 숨은 줄을 지울지는 GameSession이 정한다.
 * - 같은 Lock에서 이미 꽉 찬 행과 겹치는 경우 한 번만 세는 일(합치기)은 GameSession이 한다.
 */
public class LineClearEffect implements ItemEffect {
    @Override
    public ItemResult onLock(BoardView board, int itemRow, int itemCol, GameModifiers modifiers) {
        return new ItemResult(Set.of(itemRow), Set.of());
    }
}
