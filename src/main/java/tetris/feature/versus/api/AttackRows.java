package tetris.feature.versus.api;

import java.util.List;

/**
 * 한 번의 줄 삭제로 상대에게 보내는 공격 줄 (R3-ATTACK, R3-GARBAGE). 불변 사본.
 * 각 줄은 너비 10, 값은 0(빈칸) 또는 {@link VersusRules#ATTACK_CELL}. 순서는 위→아래.
 * 10줄 제한은 받는 쪽 대기열이 적용하므로(R3-CAP) 여기서는 자르지 않는다.
 */
public record AttackRows(List<int[]> rows) {
    public static final AttackRows NONE = new AttackRows(List.of());

    public AttackRows {
        if (rows == null) throw new IllegalArgumentException("rows");
        rows = rows.stream().map(AttackRows::copyRow).toList();
    }

    @Override
    public List<int[]> rows() {
        return rows.stream().map(int[]::clone).toList();
    }

    public int size() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    static int[] copyRow(int[] row) {
        if (row == null || row.length != VersusRules.BOARD_COLS) throw new IllegalArgumentException("attack row width");
        for (int v : row) {
            if (v != 0 && v != VersusRules.ATTACK_CELL) throw new IllegalArgumentException("attack row value " + v);
        }
        return row.clone();
    }
}
