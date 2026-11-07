package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.versus.api.AttackRows;
import tetris.feature.versus.api.LockResult;
import tetris.feature.versus.api.VersusRules;

/** S7-A1: 공격 줄·Lock 정산 계약 (R3-ATTACK, R3-GARBAGE, R3-ORDER) */
class AttackContractTest {

    private static int[] rowWithHoleAt(int col) {
        int[] row = new int[10];
        java.util.Arrays.fill(row, VersusRules.ATTACK_CELL);
        row[col] = 0;
        return row;
    }

    @Test
    void attackRowsAreImmutableCopiesInTopToBottomOrder() {
        int[] top = rowWithHoleAt(2);
        int[] bottom = rowWithHoleAt(7);
        List<int[]> source = new ArrayList<>(List.of(top, bottom));
        AttackRows attack = new AttackRows(source);

        top[2] = VersusRules.ATTACK_CELL;
        source.clear();
        attack.rows().get(0)[2] = VersusRules.ATTACK_CELL;

        assertEquals(2, attack.size());
        assertFalse(attack.isEmpty());
        assertEquals(0, attack.rows().get(0)[2]);
        assertEquals(0, attack.rows().get(1)[7]);
        assertTrue(AttackRows.NONE.isEmpty());
    }

    @Test
    void attackRowsRejectWrongWidthOrBlockColors() {
        assertThrows(IllegalArgumentException.class, () -> new AttackRows(null));
        assertThrows(IllegalArgumentException.class, () -> new AttackRows(List.of(new int[11])));
        int[] colored = new int[10];
        colored[0] = 4;
        assertThrows(IllegalArgumentException.class, () -> new AttackRows(List.of(colored)));
        List<int[]> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> new AttackRows(withNull));
    }

    @Test
    void attackRowsAreNotCappedHereBecauseTheReceiverQueueCaps() {
        List<int[]> twelve = new ArrayList<>();
        for (int i = 0; i < 12; i++) twelve.add(rowWithHoleAt(i % 10));
        assertEquals(12, new AttackRows(twelve).size());
    }

    @Test
    void lockResultAttacksOnlyFromTwoClearedRows() {
        AttackRows two = new AttackRows(List.of(rowWithHoleAt(0), rowWithHoleAt(1)));
        assertEquals(2, new LockResult(2, two).attack().size());
        assertTrue(new LockResult(1, AttackRows.NONE).attack().isEmpty());
        assertTrue(new LockResult(0, AttackRows.NONE).attack().isEmpty());

        AttackRows one = new AttackRows(List.of(rowWithHoleAt(0)));
        assertThrows(IllegalArgumentException.class, () -> new LockResult(1, one));
        assertThrows(IllegalArgumentException.class, () -> new LockResult(1, two));
        assertThrows(IllegalArgumentException.class, () -> new LockResult(-1, AttackRows.NONE));
        assertThrows(NullPointerException.class, () -> new LockResult(2, null));
    }
}
