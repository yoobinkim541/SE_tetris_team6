package tetris.feature.item;

import java.util.HashSet;
import java.util.Set;
import tetris.feature.data.Board;

/**
 * 폭탄 아이템('B')의 효과. 아이템 칸을 중심으로 한 3×3 칸 중 채워진 칸을 지울 칸으로 돌려준다.
 * 보드 밖은 빼고, 숨은 줄은 포함한다. 위 칸은 내려오지 않는다(행 삭제가 아니라 칸 삭제).
 * 이번 Lock에 꽉 찬 행에 속한 칸은 행 삭제로 지워지므로 빼서, 같은 칸이 두 번 지워지거나 점수를 두 번 받지 않게 한다.
 * 실제 삭제와 칸당 10점은 GameSession이 한 번만 계산한다(점수 2배·Level 무관).
 *
 * 가정(팀 합의 필요):
 * - onLock이 불릴 때 폭탄이 붙은 블록은 이미 보드에 기록돼 있다. 그래서 폭탄 칸 자신도 지울 칸에 들어간다.
 * - 돌려주는 칸 좌표는 행 삭제로 위 칸이 내려오기 전의 좌표다. GameSession은 행을 내리기 전에 이 칸들을 먼저 지워야 한다.
 */
public class BombEffect implements ItemEffect {
    private static final int RADIUS = 1; // 3×3
    private static final int TOP_ROW = -Board.MARGIN; // 숨은 줄 맨 위

    @Override
    public ItemResult onLock(BoardView board, int itemRow, int itemCol, GameModifiers modifiers) {
        Set<Cell> cells = new HashSet<>();
        for (int row = itemRow - RADIUS; row <= itemRow + RADIUS; row++) {
            if (row < TOP_ROW || row >= Board.HEIGHT || board.isRowFull(row)) continue;
            for (int col = itemCol - RADIUS; col <= itemCol + RADIUS; col++) {
                if (col < 0 || col >= Board.WIDTH || board.isEmpty(row, col)) continue;
                cells.add(new Cell(row, col));
            }
        }
        return new ItemResult(Set.of(), cells);
    }
}
