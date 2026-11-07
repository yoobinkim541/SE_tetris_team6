package tetris.feature.versus;

import java.util.ArrayList;
import java.util.List;
import tetris.feature.versus.api.AttackRows;
import tetris.feature.versus.api.VersusRules;

/**
 * 받은 공격 줄 대기열 (R3-CAP). 화면의 "넘어간 블럭 표시부분"과 같은 순서(위→아래)로 들고 있다.
 *
 * <ul>
 *   <li>새 공격은 기존 줄의 아래쪽에 붙는다</li>
 *   <li>이미 10줄이면 새 공격은 통째로 무시한다</li>
 *   <li>합쳐서 10줄을 넘으면 가장 아래쪽 줄부터 잘라 10줄로 맞춘다</li>
 * </ul>
 * 꺼낼 때({@link #drain()})와 조회할 때 모두 사본을 준다. EDT 한 스레드에서만 쓴다.
 */
public final class PendingAttackQueue {
    private final List<int[]> rows = new ArrayList<>();

    /**
     * 공격을 받는다.
     *
     * @return 한 줄이라도 대기열에 들어갔으면 true. 빈 공격이거나 이미 가득 차 무시했으면 false
     */
    public boolean offer(AttackRows attack) {
        if (attack == null) throw new IllegalArgumentException("attack");
        if (attack.isEmpty() || isFull()) return false;
        rows.addAll(attack.rows());
        while (rows.size() > VersusRules.MAX_PENDING_ROWS) rows.remove(rows.size() - 1);
        return true;
    }

    /** 다음 Spawn 전에 보드 아래에 넣을 줄을 모두 꺼내고 대기열을 비운다 (R3-ORDER) */
    public AttackRows drain() {
        if (rows.isEmpty()) return AttackRows.NONE;
        AttackRows out = new AttackRows(rows);
        rows.clear();
        return out;
    }

    /** 화면 표시용 사본, 위→아래 */
    public List<int[]> rows() {
        return rows.stream().map(int[]::clone).toList();
    }

    public int size() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public boolean isFull() {
        return rows.size() >= VersusRules.MAX_PENDING_ROWS;
    }

    /** 새 경기·재대전 시작 */
    public void clear() {
        rows.clear();
    }
}
