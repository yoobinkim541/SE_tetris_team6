package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.versus.PendingAttackQueue;
import tetris.feature.versus.api.AttackRows;

/** S7-A2: 10줄 대기열 (R3-CAP) */
class PendingAttackQueueTest {
    private final PendingAttackQueue queue = new PendingAttackQueue();

    /** 빈칸 위치를 표시로 쓰는 n줄 공격. i번째 줄의 빈칸은 (first + i) % 10 */
    private static AttackRows attack(int n, int first) {
        List<int[]> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int[] row = new int[10];
            java.util.Arrays.fill(row, 9);
            row[(first + i) % 10] = 0;
            rows.add(row);
        }
        return new AttackRows(rows);
    }

    private static int holeOf(int[] row) {
        for (int c = 0; c < row.length; c++) if (row[c] == 0) return c;
        return -1;
    }

    @Test
    void emptyQueueDrainsNothing() {
        assertTrue(queue.isEmpty());
        assertSame(AttackRows.NONE, queue.drain());
        assertFalse(queue.offer(AttackRows.NONE));
        assertThrows(IllegalArgumentException.class, () -> queue.offer(null));
    }

    @Test
    void laterAttacksGoBelowEarlierOnes() {
        assertTrue(queue.offer(attack(2, 0)));
        assertTrue(queue.offer(attack(3, 5)));
        List<int[]> rows = queue.rows();
        assertEquals(5, rows.size());
        assertEquals(List.of(0, 1, 5, 6, 7), rows.stream().map(PendingAttackQueueTest::holeOf).toList());
    }

    @Test
    void nineRowsPlusFourKeepsTheTopTenAndDropsTheLowest() {
        queue.offer(attack(9, 0));
        assertFalse(queue.isFull());
        assertTrue(queue.offer(attack(4, 0)));   // 새 줄 4개 중 맨 위 1개만 남는다
        assertEquals(10, queue.size());
        assertTrue(queue.isFull());
        assertEquals(0, holeOf(queue.rows().get(9))); // 새 공격의 첫 줄 (빈칸 0)
    }

    @Test
    void fullQueueIgnoresNewAttacksEntirely() {
        queue.offer(attack(10, 0));
        List<int[]> before = queue.rows();
        assertFalse(queue.offer(attack(2, 5)));
        assertEquals(10, queue.size());
        for (int i = 0; i < 10; i++) assertArrayEquals(before.get(i), queue.rows().get(i));
    }

    @Test
    void drainReturnsEverythingTopToBottomAndEmptiesTheQueue() {
        queue.offer(attack(3, 2));
        AttackRows drained = queue.drain();
        assertEquals(List.of(2, 3, 4), drained.rows().stream().map(PendingAttackQueueTest::holeOf).toList());
        assertTrue(queue.isEmpty());
        assertTrue(queue.offer(attack(1, 0)));    // 비운 뒤 다시 받을 수 있다
    }

    @Test
    void rowsAreDefensiveCopies() {
        queue.offer(attack(1, 0));
        queue.rows().get(0)[0] = 9;
        assertEquals(0, queue.rows().get(0)[0]);
        queue.clear();
        assertTrue(queue.isEmpty());
    }
}
