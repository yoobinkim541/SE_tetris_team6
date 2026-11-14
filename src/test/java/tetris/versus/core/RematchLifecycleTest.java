package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tetris.component.block.OBlock;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameState;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.*;

/**
 * R3-REMATCH, R3-LOSS, D-03: 같은 엔진으로 여러 판을 이어 할 때 전판의 공격 대기열·held 키·
 * CLEARING due-time·일시정지·점수가 다음 판으로 새지 않고, 한 판의 종료는 정확히 한 번만 확정된다.
 */
class RematchLifecycleTest {
    private static final BattleMode[] MODES = {BattleMode.CLASSIC, BattleMode.ITEM, BattleMode.TIMED};
    long now, sequence;

    LocalMatchEngine engine() { return new LocalMatchEngine(() -> now, id -> OBlock::new); }
    void press(LocalMatchEngine e, PlayerId p, GameAction a) { e.submit(new PlayerCommand(p, a, true, sequence++)); }
    void release(LocalMatchEngine e, PlayerId p, GameAction a) { e.submit(new PlayerCommand(p, a, false, sequence++)); }
    void tap(LocalMatchEngine e, PlayerId p, GameAction a) { press(e, p, a); release(e, p, a); }

    /** P1 보드 아래 두 줄을 O블록 자리만 비워 둔다. Hard Drop 한 번이면 두 줄 삭제 → P2에게 공격 2줄 */
    static void prepareDoubleClear(LocalMatchEngine e) throws Exception {
        GameSession one = LocalMatchEngineTest.session(e, PlayerId.ONE);
        for (int r : new int[]{18, 19}) for (int c = 0; c < 10; c++) if (c != 4 && c != 5)
            assertTrue(GarbageInsertionTest.board(one).tryPlaceBlock(GarbageInsertionTest.cell(r, c)));
    }

    static int filledCells(int[][] cells) {
        int n = 0;
        for (int[] row : cells) for (int v : row) if (v != 0) n++;
        return n;
    }

    void assertFreshMatch(LocalMatchEngine e, BattleMode mode, long startedAt) {
        MatchSnapshot s = e.snapshot();
        assertEquals(0, s.revision());
        assertEquals(MatchPhase.PLAYING, s.phase());
        assertNull(s.result());
        for (MatchSnapshot.PlayerView v : new MatchSnapshot.PlayerView[]{s.one(), s.two()}) {
            assertEquals(0, v.score(), "전판 점수 누출");
            assertTrue(v.pendingRows().isEmpty(), "전판 공격 누출");
            assertEquals(4, filledCells(v.cells()), "현재 블록 외 전판 칸 누출");
        }
        assertEquals(mode.timed() ? VersusRules.DEFAULT_TIME_LIMIT_MILLIS : -1, s.remainingMillis());
        assertEquals(startedAt, now);
    }

    /** 공격 대기·CLEARING 중 Pause·held Hard Drop을 남긴 채 다음 판을 시작하는 것을 10번 반복 */
    @Test void tenRematchesStartCleanAfterPausedClearingWithQueuedAttackAndHeldKeys() throws Exception {
        var e = engine();
        Set<String> ids = new HashSet<>();
        for (int round = 0; round < 10; round++) {
            BattleMode mode = MODES[round % MODES.length];
            now = round * 100_000L;
            e.start(MatchConfig.of(mode));
            assertFreshMatch(e, mode, round * 100_000L);
            assertTrue(ids.add(e.snapshot().matchId()), "매치 ID 재사용");

            // 1) 공격 2줄을 P2 대기열에 올린다
            prepareDoubleClear(e);
            tap(e, PlayerId.ONE, GameAction.HARD_DROP);
            e.advance(now);
            now += 150; e.advance(now);
            assertEquals(2, e.snapshot().two().pendingRows().size());

            // 2) P1이 다시 CLEARING을 만들고, P 키를 누른 채(놓지 않음) Pause. P2 대기열 2줄은 그대로 남는다
            prepareDoubleClear(e);
            tap(e, PlayerId.ONE, GameAction.HARD_DROP);
            e.advance(++now);
            assertEquals(GameState.CLEARING, LocalMatchEngineTest.session(e, PlayerId.ONE).getState());
            press(e, PlayerId.ONE, GameAction.PAUSE);
            e.advance(++now);
            assertEquals(MatchPhase.PAUSED, e.snapshot().phase());
            assertEquals(2, e.snapshot().two().pendingRows().size());
            assertTrue(e.snapshot().one().score() > 0);
        }

        // 마지막 판 뒤 재시작: 전판 CLEARING 150ms·낙하 1000ms due가 새 판에서 먼저 실행되지 않는다
        now = 2_000_000L;
        e.start(MatchConfig.of(BattleMode.CLASSIC));
        assertFreshMatch(e, BattleMode.CLASSIC, 2_000_000L);
        e.advance(now + 999);
        assertEquals(0, e.snapshot().one().score(), "새 판 첫 tick은 시작 1000ms 뒤");
        assertEquals(MatchPhase.PLAYING, e.snapshot().phase(), "전판 Pause 누출");
        // 전판에서 놓지 않은 P 키 latch가 풀려 있어야 새 판의 첫 Pause가 동작한다
        press(e, PlayerId.ONE, GameAction.PAUSE);
        e.advance(now + 1000);
        assertEquals(MatchPhase.PAUSED, e.snapshot().phase(), "전판 Pause latch 누출");
        e.close();
    }

    /** 진행 중 재시작: 누른 채인 Hard Drop held가 새 판으로 넘어가지 않는다 */
    @Test void restartDuringPlayReleasesHeldGameplayKeys() {
        var e = engine();
        e.start(MatchConfig.of(BattleMode.CLASSIC));
        press(e, PlayerId.TWO, GameAction.HARD_DROP);
        e.advance(0);
        assertTrue(e.snapshot().two().score() > 0);
        now = 10;
        e.start(MatchConfig.of(BattleMode.CLASSIC));
        press(e, PlayerId.TWO, GameAction.HARD_DROP);
        e.advance(10);
        assertTrue(e.snapshot().two().score() > 0, "전판 held 키 누출");
        e.close();
    }

    /** 기권으로 끝난 판은 결과가 한 번만 확정되고, 이후 Quit·Pause·시간 경과가 결과를 바꾸지 않는다 */
    @Test void forfeitFinishesExactlyOnceAndRematchClearsResult() {
        var e = engine();
        e.start(MatchConfig.of(BattleMode.TIMED));
        press(e, PlayerId.TWO, GameAction.QUIT);
        press(e, PlayerId.ONE, GameAction.QUIT); // 같은 pulse의 두 번째 Quit은 무시
        e.advance(0);
        MatchSnapshot finished = e.snapshot();
        assertEquals(MatchPhase.FINISHED, finished.phase());
        assertEquals(PlayerId.ONE, finished.result().winner());
        assertEquals(MatchResult.EndReason.FORFEIT, finished.result().reason());
        assertEquals(1, finished.revision());

        release(e, PlayerId.TWO, GameAction.QUIT);
        tap(e, PlayerId.TWO, GameAction.QUIT);
        tap(e, PlayerId.ONE, GameAction.PAUSE);
        e.advance(VersusRules.DEFAULT_TIME_LIMIT_MILLIS * 2);
        assertEquals(finished, e.snapshot(), "종료 후 상태 변경");

        now = VersusRules.DEFAULT_TIME_LIMIT_MILLIS * 3;
        e.start(MatchConfig.of(BattleMode.TIMED));
        assertFreshMatch(e, BattleMode.TIMED, now);
        assertNotEquals(finished.matchId(), e.snapshot().matchId());
        // 전판에서 누른 채인 Quit latch가 풀려 새 판의 Quit도 한 번에 동작한다
        press(e, PlayerId.ONE, GameAction.QUIT);
        e.advance(now);
        assertEquals(PlayerId.TWO, e.snapshot().result().winner());
        e.close();
    }

    /** 시간 제한 판에서 Pause 동안 마감이 멈추고, 재대전은 전판의 연장된 마감을 물려받지 않는다 */
    @Test void pausedDeadlineDoesNotCarryIntoRematch() {
        var e = engine();
        long limit = VersusRules.DEFAULT_TIME_LIMIT_MILLIS;
        e.start(MatchConfig.of(BattleMode.TIMED));
        e.advance(1_000);
        tap(e, PlayerId.TWO, GameAction.PAUSE);
        e.advance(2_000);
        assertEquals(limit - 2_000, e.snapshot().remainingMillis());
        e.advance(60_000);
        assertEquals(limit - 2_000, e.snapshot().remainingMillis(), "Pause 중 마감 진행");

        now = 70_000;
        e.start(MatchConfig.of(BattleMode.TIMED));
        e.advance(now + limit - 1);
        assertEquals(MatchPhase.PLAYING, e.snapshot().phase());
        assertEquals(1, e.snapshot().remainingMillis());
        e.advance(now + limit);
        assertEquals(MatchPhase.FINISHED, e.snapshot().phase());
        assertEquals(MatchResult.EndReason.TIME_UP, e.snapshot().result().reason());
        e.close();
    }

    /** D-03: 같은 pulse에 들어온 Pause 요청은 도착 순서대로 적용된다 (두 번이면 다시 진행) */
    @Test void pauseRequestsInSamePulseApplyInArrivalOrder() throws Exception {
        var e = engine();
        e.start(MatchConfig.of(BattleMode.CLASSIC));
        tap(e, PlayerId.ONE, GameAction.PAUSE);
        tap(e, PlayerId.TWO, GameAction.PAUSE);
        e.advance(10);
        assertEquals(MatchPhase.PLAYING, e.snapshot().phase());
        assertEquals(GameState.PLAYING, LocalMatchEngineTest.session(e, PlayerId.ONE).getState());
        assertEquals(GameState.PLAYING, LocalMatchEngineTest.session(e, PlayerId.TWO).getState());
        // 재개는 전체 간격으로 다시 센다: 10ms 시점 재개 → 1010ms에 첫 낙하
        e.advance(1_009);
        assertEquals(0, e.snapshot().one().score());
        e.advance(1_010);
        assertEquals(1, e.snapshot().one().score());
        e.close();
    }

    /** 동시 패배로 끝난 판 뒤 재대전도 깨끗하게 시작한다 (R3-LOSS → R3-REMATCH) */
    @Test void drawByDoubleTopOutThenRematch() {
        var e = engine();
        e.start(MatchConfig.of(BattleMode.CLASSIC));
        for (int i = 0; i < 20 && e.snapshot().phase() != MatchPhase.FINISHED; i++) {
            for (PlayerId p : PlayerId.values()) { release(e, p, GameAction.HARD_DROP); press(e, p, GameAction.HARD_DROP); }
            e.advance(now = i);
        }
        assertTrue(e.snapshot().result().isDraw());
        now = 1_000;
        e.start(MatchConfig.of(BattleMode.ITEM));
        assertFreshMatch(e, BattleMode.ITEM, 1_000);
        e.close();
    }

    /** 새 판은 명령 sequence를 0부터 다시 받는다. 전판의 마지막 번호보다 작아도 버리지 않는다 */
    @Test void rematchAcceptsSequenceNumbersRestartingFromZero() {
        var e = engine();
        e.start(MatchConfig.of(BattleMode.CLASSIC));
        sequence = 500;
        tap(e, PlayerId.ONE, GameAction.MOVE_LEFT);
        e.advance(0);
        e.start(MatchConfig.of(BattleMode.CLASSIC));
        sequence = 0;
        tap(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(0);
        assertTrue(e.snapshot().one().score() > 0, "전판 sequence 기준으로 새 판 입력 폐기");
        e.close();
    }

    @Test void closedEngineCannotRematch() {
        var e = engine();
        e.start(MatchConfig.of(BattleMode.CLASSIC));
        e.close();
        assertThrows(IllegalStateException.class, () -> e.start(MatchConfig.of(BattleMode.CLASSIC)));
    }
}
