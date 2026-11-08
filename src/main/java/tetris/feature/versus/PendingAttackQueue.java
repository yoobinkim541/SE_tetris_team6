package tetris.feature.versus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import tetris.feature.versus.api.AttackRows;
import tetris.feature.versus.api.VersusRules;

/** EDT-confined FIFO: keep the earliest ten rows and discard the new bottom overflow. */
public final class PendingAttackQueue {
    private final ArrayDeque<int[]> rows=new ArrayDeque<>();
    public int append(AttackRows attack) {
        Objects.requireNonNull(attack); int accepted=0;
        for(int[] row:attack.rows()) {
            if(rows.size()==VersusRules.MAX_PENDING_ROWS) break;
            rows.addLast(row); accepted++;
        }
        return accepted;
    }
    public int size() { return rows.size(); }
    public boolean isEmpty() { return rows.isEmpty(); }
    public boolean isFull() { return size() == VersusRules.MAX_PENDING_ROWS; }
    public boolean offer(AttackRows attack) {
        if (attack == null) throw new IllegalArgumentException("attack");
        return append(attack) > 0;
    }
    public java.util.List<int[]> rows() { return snapshot().rows(); }
    public AttackRows snapshot() { return rows.isEmpty() ? AttackRows.NONE : new AttackRows(new ArrayList<>(rows)); }
    /** Called once by the receiving engine at its next lock/spawn boundary. */
    public AttackRows drain() { var result=snapshot(); rows.clear(); return result; }
    public void clear() { rows.clear(); }
}
