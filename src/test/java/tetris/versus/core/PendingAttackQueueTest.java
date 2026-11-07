package tetris.versus.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import tetris.feature.versus.PendingAttackQueue;
import tetris.feature.versus.api.AttackRows;

class PendingAttackQueueTest {
    private AttackRows rows(int first,int count) {
        List<int[]> rows=new ArrayList<>();
        for(int i=first;i<first+count;i++) { int[] row=new int[10]; Arrays.fill(row,9); row[i%10]=0; rows.add(row); }
        return new AttackRows(rows);
    }
    @Test void retainsEarliestTenAndIgnoresFurtherRows() {
        var queue=new PendingAttackQueue();
        assertEquals(8,queue.append(rows(0,8))); assertEquals(2,queue.append(rows(8,3)));
        assertEquals(10,queue.size()); assertEquals(0,queue.append(rows(4,2)));
        for(int i=0;i<10;i++) assertEquals(0,queue.snapshot().rows().get(i)[i]);
        queue.snapshot().rows().getFirst()[0]=9;
        assertEquals(0,queue.snapshot().rows().getFirst()[0]);
        var drained=queue.drain(); assertEquals(10,drained.size()); assertEquals(0,queue.size());
        assertTrue(queue.drain().isEmpty());
        assertEquals(1,queue.append(rows(1,1))); queue.clear(); assertEquals(0,queue.size());
    }
    @Test void emptyAppendIsNoOpAndInputCopiesAreRetained() {
        var queue=new PendingAttackQueue(); int[] row=new int[10]; row[0]=9;
        var source=new AttackRows(List.of(row)); queue.append(source); row[0]=0;
        source.rows().getFirst()[0]=0;
        assertEquals(9,queue.snapshot().rows().getFirst()[0]);
        assertEquals(0,queue.append(AttackRows.NONE)); assertEquals(1,queue.size());
        assertThrows(NullPointerException.class,() -> queue.append(null));
    }
}
