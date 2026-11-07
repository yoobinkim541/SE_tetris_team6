package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.item.Cell;
import tetris.feature.versus.AttackExtractor;
import tetris.feature.versus.api.AttackRows;

/** S7-A2: 삭제 전 사본에서 공격 줄 만들기 (R3-ATTACK, D-09, D-10) */
class AttackRowsTest {
    private static final int X = 9; // ATTACK_CELL
    private static final int B = 3; // 아무 블록 색

    /** rows의 지정 행을 색 B로 모두 채운 20x10 보드 */
    private static int[][] boardWithFullRows(int... rows) {
        int[][] board = new int[20][10];
        for (int r : rows) java.util.Arrays.fill(board[r], B);
        return board;
    }

    private static List<Cell> cells(int... rowCols) {
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < rowCols.length; i += 2) out.add(new Cell(rowCols[i], rowCols[i + 1]));
        return out;
    }

    private static void assertRow(int[] actual, int... expected) {
        assertArrayEquals(expected, actual);
    }

    @Test
    void singleLineClearSendsNothing() {
        int[][] board = boardWithFullRows(19);
        assertSame(AttackRows.NONE, AttackExtractor.extract(board, List.of(19), cells(19, 0)));
        assertSame(AttackRows.NONE, AttackExtractor.extract(board, List.of(), cells(19, 0)));
    }

    @Test
    void twoLinesWithOBlockLeaveATwoByTwoHole() {
        int[][] board = boardWithFullRows(18, 19);
        AttackRows attack = AttackExtractor.extract(board, List.of(18, 19), cells(18, 4, 18, 5, 19, 4, 19, 5));
        assertEquals(2, attack.size());
        assertRow(attack.rows().get(0), X, X, X, X, 0, 0, X, X, X, X);
        assertRow(attack.rows().get(1), X, X, X, X, 0, 0, X, X, X, X);
    }

    @Test
    void rotatedLClearingThreeLinesKeepsItsShapeAsHoles() {
        // L을 시계방향으로 돌린 모양이 col 0..1에 걸쳐 3줄을 채움:  rows 17..19
        //   17: [0][1]  18: [1]  19: [1]   (요구사항 p4 예제와 같은 형태)
        int[][] board = boardWithFullRows(17, 18, 19);
        AttackRows attack = AttackExtractor.extract(board, List.of(19, 17, 18),
                cells(17, 0, 17, 1, 18, 1, 19, 1));
        assertEquals(3, attack.size());
        assertRow(attack.rows().get(0), 0, 0, X, X, X, X, X, X, X, X);
        assertRow(attack.rows().get(1), X, 0, X, X, X, X, X, X, X, X);
        assertRow(attack.rows().get(2), X, 0, X, X, X, X, X, X, X, X);
    }

    @Test
    void verticalITetrisLeavesOneColumnHoleInFourRows() {
        int[][] board = boardWithFullRows(16, 17, 18, 19);
        AttackRows attack = AttackExtractor.extract(board, List.of(16, 17, 18, 19),
                cells(16, 9, 17, 9, 18, 9, 19, 9));
        assertEquals(4, attack.size());
        for (int[] row : attack.rows()) assertRow(row, X, X, X, X, X, X, X, X, X, 0);
    }

    @Test
    void lineClearItemRowKeepsItsOriginalEmptyCells() {
        // 18행은 완성, 10행은 L 아이템이 지우는 불완전 행 (빈칸 col 2,7). 중복된 18은 한 번만
        int[][] board = boardWithFullRows(18);
        java.util.Arrays.fill(board[10], B);
        board[10][2] = 0;
        board[10][7] = 0;
        AttackRows attack = AttackExtractor.extract(board, List.of(18, 10, 18), cells(10, 4, 18, 4));
        assertEquals(2, attack.size());
        assertRow(attack.rows().get(0), X, X, 0, X, 0, X, X, 0, X, X); // 위→아래 순서
        assertRow(attack.rows().get(1), X, X, X, X, 0, X, X, X, X, X);
    }

    @Test
    void hiddenRowsDoNotCountTowardTheTwoLineThreshold() {
        int[][] board = boardWithFullRows(19);
        assertTrue(AttackExtractor.extract(board, List.of(-1, -2, 19), cells(19, 0)).isEmpty());
    }

    @Test
    void existingAttackCellsStayGrayAndInputIsNotMutated() {
        int[][] board = boardWithFullRows(18, 19);
        board[19][0] = X;
        AttackRows attack = AttackExtractor.extract(board, List.of(18, 19), cells(18, 3, 19, 3));
        assertEquals(X, attack.rows().get(1)[0]);
        assertEquals(B, board[18][3]); // 원본 보드는 바꾸지 않는다
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> AttackExtractor.extract(new int[19][10], List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> AttackExtractor.extract(new int[20][9], List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> AttackExtractor.extract(null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> AttackExtractor.extract(new int[20][10], null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> AttackExtractor.extract(new int[20][10], List.of(), null));
    }
}
