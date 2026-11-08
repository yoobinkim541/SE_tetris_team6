package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.game.Difficulty;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.*;

/** S7-A1 계약 v1: R3-MODES, R3-ATTACK, R3-CAP, R3-TIME 규칙을 타입이 지키는지 */
class MatchContractTest {

    private static MatchSnapshot.PlayerView emptyView() {
        return new MatchSnapshot.PlayerView(new int[20][10], new int[0][], 0, List.of());
    }

    @Test
    void opponentIsTheOtherPlayer() {
        assertEquals(PlayerId.TWO, PlayerId.ONE.opponent());
        assertEquals(PlayerId.ONE, PlayerId.TWO.opponent());
    }

    @Test
    void onlyItemModeHasItemsAndOnlyTimeLimitIsTimed() {
        assertFalse(BattleMode.CLASSIC.itemsEnabled());
        assertTrue(BattleMode.ITEM.itemsEnabled());
        assertFalse(BattleMode.TIMED.itemsEnabled());
        assertTrue(BattleMode.TIMED.timed());
        assertFalse(BattleMode.CLASSIC.timed());
        assertFalse(BattleMode.ITEM.timed());
    }

    @Test
    void fixedRuleValuesMatchRequirements() {
        assertEquals(2, VersusRules.MIN_ATTACK_LINES);
        assertEquals(10, VersusRules.MAX_PENDING_ROWS);
        assertEquals(9, VersusRules.ATTACK_CELL);
        assertEquals(180_000L, VersusRules.DEFAULT_TIME_LIMIT_MILLIS);
    }

    @Test
    void timeLimitIsRequiredOnlyForTimedMode() {
        assertEquals(VersusRules.DEFAULT_TIME_LIMIT_MILLIS, MatchConfig.of(BattleMode.TIMED).timeLimitMillis());
        assertEquals(0, MatchConfig.of(BattleMode.CLASSIC).timeLimitMillis());
        assertEquals(Difficulty.NORMAL, MatchConfig.of(BattleMode.ITEM).difficulty());
        assertThrows(IllegalArgumentException.class, () -> new MatchConfig(BattleMode.TIMED, Difficulty.EASY, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchConfig(BattleMode.CLASSIC, Difficulty.EASY, 1000));
        assertThrows(NullPointerException.class, () -> new MatchConfig(null, Difficulty.EASY, 0));
    }

    @Test
    void commandRejectsMissingFieldsAndNegativeSequence() {
        var cmd = new PlayerCommand(PlayerId.TWO, GameAction.ROTATE, true, 0);
        assertEquals(PlayerId.TWO, cmd.playerId());
        assertThrows(IllegalArgumentException.class, () -> new PlayerCommand(PlayerId.ONE, GameAction.ROTATE, true, -1));
        assertThrows(NullPointerException.class, () -> new PlayerCommand(null, GameAction.ROTATE, true, 0));
        assertThrows(NullPointerException.class, () -> new PlayerCommand(PlayerId.ONE, null, true, 0));
    }

    @Test
    void playerViewCopiesDefensively() {
        int[][] board = new int[20][10];
        board[19][0] = VersusRules.ATTACK_CELL;
        int[] row = new int[10];
        row[3] = VersusRules.ATTACK_CELL;
        List<int[]> rows = new ArrayList<>(List.of(row));
        var view = new MatchSnapshot.PlayerView(board, new int[][]{{1, 1, 1, 1}}, 10, rows);

        board[19][0] = 0;
        row[3] = 0;
        rows.clear();
        assertEquals(VersusRules.ATTACK_CELL, view.cells()[19][0]);
        assertEquals(VersusRules.ATTACK_CELL, view.pendingRows().getFirst()[3]);

        view.cells()[19][0] = 0;
        view.next()[0][0] = 0;
        view.pendingRows().getFirst()[3] = 0;
        assertEquals(VersusRules.ATTACK_CELL, view.cells()[19][0]);
        assertEquals(1, view.next()[0][0]);
        assertEquals(VersusRules.ATTACK_CELL, view.pendingRows().getFirst()[3]);
    }

    @Test
    void playerViewRejectsInvalidShapes() {
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot.PlayerView(new int[19][10], new int[0][], 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot.PlayerView(new int[20][10], new int[5][4], 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot.PlayerView(new int[20][10], new int[1][5], 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot.PlayerView(new int[20][10], new int[0][], -1, List.of()));
        int[][] badCell = new int[20][10];
        badCell[0][0] = 10;
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot.PlayerView(badCell, new int[0][], 0, List.of()));
    }

    @Test
    void pendingQueueIsCappedAtTenAttackRows() {
        List<int[]> ten = Collections.nCopies(10, new int[10]);
        assertEquals(10, new MatchSnapshot.PlayerView(new int[20][10], new int[0][], 0, ten).pendingRows().size());
        List<int[]> eleven = Collections.nCopies(11, new int[10]);
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot.PlayerView(new int[20][10], new int[0][], 0, eleven));
        int[] colored = new int[10];
        colored[0] = 3;
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot.PlayerView(new int[20][10], new int[0][], 0, List.of(colored)));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot.PlayerView(new int[20][10], new int[0][], 0, List.of(new int[9])));
    }

    @Test
    void resultIsPresentExactlyWhenFinished() {
        var one = emptyView();
        var playing = new MatchSnapshot("m-1", 0, one, emptyView(), -1, MatchPhase.PLAYING, null);
        assertSame(one, playing.player(PlayerId.ONE));
        var done = new MatchSnapshot("m-1", 5, one, emptyView(), 0, MatchPhase.FINISHED,
                MatchResult.win(PlayerId.TWO, MatchResult.EndReason.TOPPED_OUT));
        assertEquals(PlayerId.TWO, done.result().winner());

        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot("m-1", 0, one, one, -1, MatchPhase.FINISHED, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot("m-1", 0, one, one, -1, MatchPhase.PLAYING, MatchResult.draw(MatchResult.EndReason.TIME_UP)));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot(" ", 0, one, one, -1, MatchPhase.PLAYING, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot("m-1", -1, one, one, -1, MatchPhase.PLAYING, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchSnapshot("m-1", 0, one, one, -2, MatchPhase.PLAYING, null));
    }

    @Test
    void drawsComeOnlyFromSameFrameTopOutOrTimeUpTie() {
        assertTrue(MatchResult.draw(MatchResult.EndReason.TIME_UP).isDraw());
        assertTrue(MatchResult.draw(MatchResult.EndReason.TOPPED_OUT).isDraw());
        assertFalse(MatchResult.win(PlayerId.ONE, MatchResult.EndReason.TIME_UP).isDraw());
        assertEquals(PlayerId.TWO, MatchResult.win(PlayerId.TWO, MatchResult.EndReason.FORFEIT).winner());
        assertThrows(IllegalArgumentException.class, () -> MatchResult.draw(MatchResult.EndReason.FORFEIT));
        assertThrows(NullPointerException.class, () -> MatchResult.win(null, MatchResult.EndReason.TIME_UP));
        assertThrows(NullPointerException.class, () -> MatchResult.draw(null));
    }

    @Test
    void singlePlayerSpawnBoundaryIsImmediate() {
        assertFalse(SpawnBoundary.IMMEDIATE.deferSpawn());
        assertDoesNotThrow(() -> SpawnBoundary.IMMEDIATE.onLockResolved(new LockResult(1, AttackRows.NONE)));
        long a = MatchClock.system().nowMillis();
        assertTrue(MatchClock.system().nowMillis() >= a);
    }
}
