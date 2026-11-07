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
    public AttackRows snapshot() { return new AttackRows(new ArrayList<>(rows)); }
    /** Called once by the receiving engine at its next lock/spawn boundary. */
    public AttackRows drain() { var result=snapshot(); rows.clear(); return result; }
    public void clear() { rows.clear(); }
}
