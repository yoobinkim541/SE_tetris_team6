package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.component.block.OBlock;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameState;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.PendingAttackQueue;
import tetris.feature.versus.api.*;

/**
 * 실제 {@link LocalMatchEngine} pulse에서 공격 도착 시점·대기열 상한·상단 초과·동시 패배·마감 경계를 고정 블록(O)으로 검사한다.
 * R3-ATTACK, R3-CAP, R3-ORDER(D-08, D-11), D-12, D-06.
 */
class AttackBoundaryEngineTest {
    private static final int ATTACK = VersusRules.ATTACK_CELL;
    long now, sequence;

    LocalMatchEngine engine(BattleMode mode) {
        var e = new LocalMatchEngine(() -> now, id -> OBlock::new);
        e.start(MatchConfig.of(mode));
        return e;
    }
    LocalMatchEngine engine() { return engine(BattleMode.CLASSIC); }
    void hardDrop(LocalMatchEngine e, PlayerId p) {
        e.submit(new PlayerCommand(p, GameAction.HARD_DROP, true, sequence++));
        e.submit(new PlayerCommand(p, GameAction.HARD_DROP, false, sequence++));
    }
    static GameSession session(LocalMatchEngine e, PlayerId p) throws Exception { return LocalMatchEngineTest.session(e, p); }
    static void place(LocalMatchEngine e, PlayerId p, int row, int col) throws Exception {
        assertTrue(GarbageInsertionTest.board(session(e, p)).tryPlaceBlock(GarbageInsertionTest.cell(row, col)));
    }
    /** 주어진 행들을 O블록 자리(4, 5열)만 비워 채운다 */
    static void fillExceptO(LocalMatchEngine e, PlayerId p, int... rows) throws Exception {
        for (int r : rows) for (int c = 0; c < 10; c++) if (c != 4 && c != 5) place(e, p, r, c);
    }
    static PendingAttackQueue queue(LocalMatchEngine e, PlayerId p) throws Exception {
        Field f = LocalMatchEngine.class.getDeclaredField("pending");
        f.setAccessible(true);
        return ((PendingAttackQueue[]) f.get(e))[p.ordinal()];
    }
    /** hole 열만 빈 공격 행 n개 */
    static AttackRows rows(int n, int hole) {
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i < n; i++) { int[] row = new int[10]; Arrays.fill(row, ATTACK); row[hole] = 0; list.add(row); }
        return new AttackRows(list);
    }
    static int[][] cells(LocalMatchEngine e, PlayerId p) throws Exception { return session(e, p).snapshot().cells(); }
    static boolean hasAttackCell(int[][] cells) {
        for (int[] row : cells) for (int v : row) if (v == ATTACK) return true;
        return false;
    }

    /** 공격이 받는 쪽 CLEARING 도중 도착하면 삽입하지 않고, 받는 쪽 삭제·점수 정산 뒤 다음 Spawn 전에 넣는다 (D-11) */
    @Test void attackArrivingDuringReceiverClearingWaitsForItsOwnClearToSettle() throws Exception {
        var e = engine();
        fillExceptO(e, PlayerId.ONE, 18, 19); // P1: 두 줄 삭제 → 공격 2줄
        fillExceptO(e, PlayerId.TWO, 19);     // P2: 한 줄 삭제(공격 없음), O 윗부분은 18행에 남음
        hardDrop(e, PlayerId.ONE);
        e.advance(0);
        hardDrop(e, PlayerId.TWO);
        e.advance(100);
        assertEquals(GameState.CLEARING, session(e, PlayerId.TWO).getState());

        e.advance(150); // P1 삭제 확정 → 공격 발행. P2는 아직 CLEARING
        assertEquals(2, e.snapshot().two().pendingRows().size());
        assertEquals(GameState.CLEARING, session(e, PlayerId.TWO).getState());
        assertFalse(hasAttackCell(cells(e, PlayerId.TWO)), "CLEARING 도중 즉시 삽입");
        long scoreBefore = e.snapshot().two().score();

        e.advance(250); // P2 삭제 정산 → 공격 삽입 → Spawn
        int[][] two = cells(e, PlayerId.TWO);
        assertTrue(e.snapshot().two().pendingRows().isEmpty());
        assertTrue(e.snapshot().two().score() > scoreBefore, "받는 쪽 삭제 점수가 먼저 정산");
        assertEquals(ATTACK, two[19][0]);
        assertEquals(0, two[19][4], "공격 행은 보낸 블록 모양의 빈칸 유지");
        int oCell = two[17][4];
        assertTrue(oCell != 0 && oCell != ATTACK, "자기 삭제 뒤 남은 칸이 공격 위로 이동");
        assertEquals(0, two[18][4]);
    }

    /** 같은 pulse에서 공격이 발행되고 받는 쪽이 Lock하면 그 경계에서 바로 삽입한다 (D-08: 발행 → 대기열 → 삽입 → Spawn) */
    @Test void attackPublishedInTheReceiverLockPulseIsInsertedAtThatBoundary() throws Exception {
        var e = engine();
        fillExceptO(e, PlayerId.ONE, 18, 19);
        hardDrop(e, PlayerId.ONE);
        e.advance(0);
        hardDrop(e, PlayerId.TWO);
        e.advance(150);
        int[][] two = cells(e, PlayerId.TWO);
        assertTrue(e.snapshot().two().pendingRows().isEmpty());
        assertEquals(ATTACK, two[18][0]);
        assertEquals(ATTACK, two[19][0]);
        assertNotEquals(0, two[16][4], "받는 쪽 O가 공격 2줄 위로 밀림");
        assertNotNull(session(e, PlayerId.TWO).snapshot().currentBlock(), "삽입 뒤 Spawn");
    }

    /** 9줄 대기 중 2줄이 오면 10줄만 남기고 가장 아래를 버리며, 10줄이면 다음 공격은 통째로 무시한다 (R3-CAP, D-10) */
    @Test void consecutiveAttacksAppendBelowAndCapAtTenRows() throws Exception {
        var e = engine();
        queue(e, PlayerId.TWO).append(rows(9, 0));
        fillExceptO(e, PlayerId.ONE, 18, 19);
        hardDrop(e, PlayerId.ONE);
        e.advance(0);
        e.advance(150);
        List<int[]> pending = e.snapshot().two().pendingRows();
        assertEquals(10, pending.size());
        for (int i = 0; i < 9; i++) assertEquals(0, pending.get(i)[0], "먼저 온 공격이 위");
        assertEquals(0, pending.get(9)[4], "새 공격의 첫 줄만 남음");
        assertEquals(ATTACK, pending.get(9)[0]);

        fillExceptO(e, PlayerId.ONE, 18, 19);
        hardDrop(e, PlayerId.ONE);
        e.advance(151);
        e.advance(301);
        List<int[]> after = e.snapshot().two().pendingRows();
        assertEquals(10, after.size());
        for (int i = 0; i < 10; i++) assertArrayEquals(pending.get(i), after.get(i), "가득 찬 대기열에 추가됨");

        hardDrop(e, PlayerId.TWO);
        e.advance(302);
        int[][] two = cells(e, PlayerId.TWO);
        assertTrue(e.snapshot().two().pendingRows().isEmpty());
        for (int r = 10; r < 19; r++) assertEquals(0, two[r][0]);
        assertEquals(0, two[19][4]);
        assertNotEquals(0, two[8][4], "O가 공격 10줄 위로 밀림");
        assertEquals(MatchPhase.PLAYING, e.snapshot().phase());
    }

    /** 공격 삽입으로 보이는 보드 위로 고정 칸이 밀리면 패배, 딱 0행까지면 계속 (D-12, R3-LOSS) */
    @Test void garbageOverflowAtTheVisibleTopLosesButReachingRowZeroDoesNot() throws Exception {
        for (int row : new int[]{9, 10}) {
            var e = engine();
            place(e, PlayerId.TWO, row, 0);
            queue(e, PlayerId.TWO).append(rows(10, 9));
            hardDrop(e, PlayerId.TWO);
            e.advance(0);
            if (row == 9) {
                assertEquals(MatchPhase.FINISHED, e.snapshot().phase());
                assertEquals(PlayerId.ONE, e.snapshot().result().winner());
                assertEquals(MatchResult.EndReason.TOPPED_OUT, e.snapshot().result().reason());
            } else {
                assertEquals(MatchPhase.PLAYING, e.snapshot().phase(), "row=" + row);
                assertNotEquals(0, cells(e, PlayerId.TWO)[0][0]);
            }
            e.close();
        }
    }

    /** 한쪽은 공격 삽입으로, 다른 쪽은 Spawn 충돌로 같은 pulse에 지면 무승부 */
    @Test void garbageOverflowAndSpawnTopOutInTheSamePulseAreADraw() throws Exception {
        var e = engine();
        place(e, PlayerId.ONE, 2, 4); // P1 O는 0~1행에 그대로 고정되고 다음 O가 들어갈 자리가 없음
        place(e, PlayerId.TWO, 9, 0);
        queue(e, PlayerId.TWO).append(rows(10, 9));
        hardDrop(e, PlayerId.ONE);
        hardDrop(e, PlayerId.TWO);
        e.advance(0);
        assertEquals(MatchPhase.FINISHED, e.snapshot().phase());
        assertTrue(e.snapshot().result().isDraw());
        assertEquals(MatchResult.EndReason.TOPPED_OUT, e.snapshot().result().reason());
    }

    /** 공격 삽입으로 인한 패배가 마감 pulse에 확정되면 TIME_UP보다 우선한다 (D-06), 진 쪽 점수가 높아도 같다 */
    @Test void garbageOverflowInTheDeadlinePulseBeatsTimeUp() throws Exception {
        long deadline = 500; // 첫 낙하(1000ms) 전에 마감해 중력 점수·자연 패배가 끼지 않게 한다
        var e = new LocalMatchEngine(() -> now, id -> OBlock::new);
        e.start(new MatchConfig(BattleMode.TIMED, Difficulty.NORMAL, deadline));
        place(e, PlayerId.TWO, 9, 0);
        queue(e, PlayerId.TWO).append(rows(10, 9));
        now = deadline - 1;
        hardDrop(e, PlayerId.TWO); // 마감 전에 도착한 입력
        e.advance(deadline);
        assertTrue(e.snapshot().two().score() > e.snapshot().one().score());
        assertEquals(MatchPhase.FINISHED, e.snapshot().phase());
        assertEquals(MatchResult.EndReason.TOPPED_OUT, e.snapshot().result().reason());
        assertEquals(PlayerId.ONE, e.snapshot().result().winner());
    }

    /** 일시정지 동안 도착한 공격은 대기열에만 남고, 재개 뒤 받는 쪽 Lock에서 한 번 삽입된다 */
    @Test void attackQueuedWhilePausedIsInsertedOnceAfterResume() throws Exception {
        var e = engine();
        fillExceptO(e, PlayerId.ONE, 18, 19);
        hardDrop(e, PlayerId.ONE);
        e.advance(0);
        e.submit(new PlayerCommand(PlayerId.TWO, GameAction.PAUSE, true, sequence++));
        e.submit(new PlayerCommand(PlayerId.TWO, GameAction.PAUSE, false, sequence++));
        e.advance(100); // P1 CLEARING 중 정지
        e.advance(5_000);
        assertTrue(e.snapshot().two().pendingRows().isEmpty(), "정지 중 CLEARING 완료");
        e.submit(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, true, sequence++));
        e.submit(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, false, sequence++));
        e.advance(5_001);
        e.advance(5_150);
        assertTrue(e.snapshot().two().pendingRows().isEmpty(), "재개 후 CLEARING 150ms를 처음부터 다시 셈");
        e.advance(5_151);
        assertEquals(2, e.snapshot().two().pendingRows().size());
        hardDrop(e, PlayerId.TWO);
        e.advance(5_152);
        assertTrue(e.snapshot().two().pendingRows().isEmpty());
        assertEquals(ATTACK, cells(e, PlayerId.TWO)[19][0]);
        hardDrop(e, PlayerId.TWO);
        e.advance(5_153);
        assertEquals(0, cells(e, PlayerId.TWO)[17][0], "같은 공격이 두 번 삽입됨");
    }
}
