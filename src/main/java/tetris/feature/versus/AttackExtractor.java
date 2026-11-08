package tetris.feature.versus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import tetris.feature.item.Cell;
import tetris.feature.versus.api.AttackRows;
import tetris.feature.versus.api.VersusRules;

/**
 * 삭제 전 보드 사본에서 상대에게 보낼 공격 줄을 만든다 (R3-ATTACK, D-09, D-10).
 *
 * <ul>
 *   <li>대상 행: 삭제될 행 중 보이는 행(0..19)의 중복 없는 합집합. 자연 완성 행과 L 아이템 행을 함께 센다</li>
 *   <li>보이는 대상 행이 2줄 미만이면 공격 없음</li>
 *   <li>각 행에서 방금 고정한 블록의 칸과 원래 빈칸은 0, 나머지 고정 칸은 {@link VersusRules#ATTACK_CELL}</li>
 *   <li>순서는 위→아래, 열은 왼→오</li>
 * </ul>
 * 아이템이 행이 아니라 칸만 지운 것(B/W 등)은 대상 행에 넣지 않는다.
 */
public final class AttackExtractor {
    private AttackExtractor() {
    }

    /**
     * @param visibleBeforeClear 블록을 보드에 기록한 뒤, 줄을 지우기 전의 보이는 영역 20x10 사본
     * @param clearedRows        이번 Lock에서 지울 행 번호 (숨은 행 포함 가능, 중복 가능)
     * @param lockedCells        방금 고정한 블록이 차지한 칸 (보드 논리 좌표)
     */
    public static AttackRows extract(int[][] visibleBeforeClear, Collection<Integer> clearedRows,
                                     Collection<Cell> lockedCells) {
        checkBoard(visibleBeforeClear);
        if (clearedRows == null || lockedCells == null) throw new IllegalArgumentException("rows/cells");

        Set<Integer> rows = new TreeSet<>();
        for (Integer row : clearedRows) {
            if (row != null && row >= 0 && row < VersusRules.BOARD_ROWS) rows.add(row);
        }
        if (rows.size() < VersusRules.MIN_ATTACK_LINES) return AttackRows.NONE;

        Set<Cell> locked = new HashSet<>(lockedCells);
        List<int[]> attack = new ArrayList<>(rows.size());
        for (int row : rows) {
            int[] out = new int[VersusRules.BOARD_COLS];
            for (int col = 0; col < VersusRules.BOARD_COLS; col++) {
                boolean filled = visibleBeforeClear[row][col] != 0;
                out[col] = filled && !locked.contains(new Cell(row, col)) ? VersusRules.ATTACK_CELL : 0;
            }
            attack.add(out);
        }
        return new AttackRows(attack);
    }

    private static void checkBoard(int[][] board) {
        if (board == null || board.length != VersusRules.BOARD_ROWS) throw new IllegalArgumentException("board height");
        for (int[] row : board) {
            if (row == null || row.length != VersusRules.BOARD_COLS) throw new IllegalArgumentException("board width");
        }
    }
}
