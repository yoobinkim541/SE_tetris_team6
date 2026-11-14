package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tetris.component.block.WeightBlock;
import tetris.feature.game.Difficulty;
import tetris.feature.item.ItemType;
import tetris.feature.rule.ItemBlockGenerator;
import tetris.feature.rule.WeightedBlockGenerator;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.PendingAttackQueue;
import tetris.feature.versus.api.*;

/**
 * R3-REGRESSION, R3-ORDER, R3-ITEM 비기능 검사: 고정 시드로 두 플레이어의 무작위 press/release·Pause·가끔 Quit,
 * 무작위 pulse 간격, 무작위 공격 주입을 CLASSIC·ITEM·TIMED를 돌아가며 재대전으로 이어 실행하고 매 pulse 불변식을 본다.
 * 싱글의 0..8 범위는 {@code RandomInputStressTest}가 그대로 지키고, 대전 보드에서만 공격칸(9)을 허용한다.
 * 평균과 함께 최대 pulse 처리 시간을 기록한다.
 */
class VersusStressTest {
    private static final int PULSES = 6_000;
    private static final BattleMode[] MODES = {BattleMode.CLASSIC, BattleMode.ITEM, BattleMode.TIMED};
    private static final GameAction[] ACTIONS = {
            GameAction.MOVE_LEFT, GameAction.MOVE_RIGHT, GameAction.SOFT_DROP, GameAction.ROTATE, GameAction.HARD_DROP};
    /** 한 pulse 최대 처리 시간 상한. 실측(1 ms 미만)보다 넉넉히 잡아 느린 CI에서도 흔들리지 않게 하고, 값은 출력으로 남긴다 */
    private static final long MAX_PULSE_NANOS = 200_000_000L;

    long now, sequence;
    BattleMode mode;

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 42, 2026, 7_777, 123_456_789})
    void randomBattlesKeepVersusInvariantsAcrossRematches(long seed) throws Exception {
        Random random = new Random(seed);
        var e = new LocalMatchEngine(() -> now, id -> {
            Random blocks = new Random(seed * 31 + id.ordinal());
            var base = new WeightedBlockGenerator(Difficulty.NORMAL.weights(), blocks);
            if (!mode.itemsEnabled()) return base;
            // 무작위 플레이로는 10줄 삭제가 드물어, 다섯 효과와 무게추가 실제로 나오도록 매 블록 아이템을 요청한다
            return new ItemBlockGenerator(base, WeightBlock::new, blocks) {
                @Override public tetris.component.block.Block next() { requestItem(); return super.next(); }
            };
        });
        Map<MatchResult.EndReason, Integer> endings = new EnumMap<>(MatchResult.EndReason.class);
        EnumSet<BattleMode> played = EnumSet.noneOf(BattleMode.class);
        boolean sawAttackCell = false, sawItem = false, sawPending = false;
        int matches = 0;
        long totalNanos = 0, maxNanos = 0;

        Match match = startMatch(e, 0, played);
        matches++;
        for (int pulse = 0; pulse < PULSES; pulse++) {
            String where = "seed " + seed + ", pulse " + pulse + ", match " + matches + " " + mode;
            submitRandomInputs(e, random);
            if (random.nextInt(40) == 0) injectAttack(e, random);
            now += random.nextInt(8) == 0 ? random.nextInt(400) : random.nextInt(40);

            long start = System.nanoTime();
            e.advance(now);
            long nanos = System.nanoTime() - start;
            totalNanos += nanos;
            maxNanos = Math.max(maxNanos, nanos);

            MatchSnapshot s = e.snapshot();
            match.check(s, where);
            for (MatchSnapshot.PlayerView v : new MatchSnapshot.PlayerView[]{s.one(), s.two()}) {
                sawAttackCell |= Arrays.stream(v.cells()).flatMapToInt(Arrays::stream).anyMatch(c -> c == VersusRules.ATTACK_CELL);
                sawItem |= Arrays.stream(v.items()).flatMapToInt(Arrays::stream).anyMatch(c -> c != 0);
                sawPending |= !v.pendingRows().isEmpty();
            }
            if (s.phase() == MatchPhase.FINISHED) {
                endings.merge(s.result().reason(), 1, Integer::sum);
                match = startMatch(e, matches, played);
                matches++;
            }
        }
        e.close();

        System.out.printf("[versus-stress] seed=%d pulses=%,d matches=%d endings=%s avgPulseUs=%.1f maxPulseMs=%.3f%n",
                seed, PULSES, matches, endings, totalNanos / 1_000.0 / PULSES, maxNanos / 1_000_000.0);
        assertTrue(maxNanos < MAX_PULSE_NANOS, "최대 pulse 처리 시간 " + maxNanos + " ns");
        assertEquals(EnumSet.allOf(BattleMode.class), played, "세 모드 모두 실행");
        assertTrue(matches >= 3, "재대전이 여러 번 일어나야 한다: " + matches);
        assertTrue(endings.containsKey(MatchResult.EndReason.TOPPED_OUT), "패배 종료가 한 번 이상: " + endings);
        assertTrue(endings.containsKey(MatchResult.EndReason.TIME_UP), "시간 마감 종료가 한 번 이상: " + endings);
        assertTrue(sawAttackCell, "공격칸이 실제 보드에 삽입되어야 한다");
        assertTrue(sawPending, "대기열 표시가 실제로 생겨야 한다");
        assertTrue(sawItem, "ITEM 판에서 아이템 칸이 보여야 한다");
    }

    Match startMatch(LocalMatchEngine e, int index, EnumSet<BattleMode> played) {
        mode = MODES[index % MODES.length];
        played.add(mode);
        sequence = 0; // 새 판은 sequence를 0부터 다시 쓴다
        long limit = 4_000; // TIMED 판이 패배 전에 마감까지 가도록 짧게
        e.start(mode.timed() ? new MatchConfig(mode, Difficulty.NORMAL, limit) : MatchConfig.of(mode));
        Match match = new Match(mode);
        match.check(e.snapshot(), "start " + mode);
        return match;
    }

    void submitRandomInputs(LocalMatchEngine e, Random random) {
        for (PlayerId p : PlayerId.values()) {
            int n = random.nextInt(3);
            for (int i = 0; i < n; i++) {
                GameAction a = ACTIONS[random.nextInt(ACTIONS.length)];
                e.submit(new PlayerCommand(p, a, random.nextInt(4) != 0, sequence++));
            }
        }
        int shared = random.nextInt(400);
        PlayerId who = random.nextBoolean() ? PlayerId.ONE : PlayerId.TWO;
        if (shared < 3) tap(e, who, GameAction.PAUSE);
        else if (shared == 3 && random.nextInt(4) == 0) tap(e, who, GameAction.QUIT);
    }

    void tap(LocalMatchEngine e, PlayerId p, GameAction a) {
        e.submit(new PlayerCommand(p, a, true, sequence++));
        e.submit(new PlayerCommand(p, a, false, sequence++));
    }

    /** 실제 2줄 이상 삭제가 무작위 플레이에서 드물어, 받은 공격을 대기열에 직접 넣어 삽입·상한·상단 초과 경로를 돌린다 */
    static void injectAttack(LocalMatchEngine e, Random random) throws Exception {
        Field f = LocalMatchEngine.class.getDeclaredField("pending");
        f.setAccessible(true);
        PendingAttackQueue queue = ((PendingAttackQueue[]) f.get(e))[random.nextInt(2)];
        List<int[]> rows = new ArrayList<>();
        int count = 2 + random.nextInt(3);
        int hole = random.nextInt(10);
        for (int i = 0; i < count; i++) {
            int[] row = new int[10];
            Arrays.fill(row, VersusRules.ATTACK_CELL);
            row[hole] = 0;
            rows.add(row);
        }
        queue.append(new AttackRows(rows));
    }

    /** 한 판 동안의 불변식. 점수·revision·남은 시간의 단조성은 판마다 새로 센다 */
    static final class Match {
        final BattleMode mode;
        long lastRevision = -1, lastRemaining = Long.MAX_VALUE;
        long[] lastScore = {0, 0};
        boolean finished;
        MatchPhase lastPhase;

        Match(BattleMode mode) { this.mode = mode; }

        void check(MatchSnapshot s, String where) {
            assertFalse(finished, "종료 뒤 상태가 바뀜: " + where);
            assertTrue(s.revision() > lastRevision, "revision 단조 증가: " + where);
            lastRevision = s.revision();
            assertEquals(s.phase() == MatchPhase.FINISHED, s.result() != null, "결과와 단계 일치: " + where);
            if (s.result() != null && s.result().reason() == MatchResult.EndReason.TIME_UP) {
                assertTrue(mode.timed(), "TIME_UP은 TIMED만: " + where);
                long one = s.one().score(), two = s.two().score();
                if (s.result().isDraw()) assertEquals(one, two, where);
                else assertEquals(one > two ? PlayerId.ONE : PlayerId.TWO, s.result().winner(), where);
            }
            if (mode.timed()) {
                assertTrue(s.remainingMillis() >= 0 && s.remainingMillis() <= lastRemaining, "남은 시간 단조 감소: " + where);
                if (lastPhase == MatchPhase.PAUSED && s.phase() == MatchPhase.PAUSED) assertEquals(lastRemaining, s.remainingMillis(), "정지 중 마감 고정: " + where);
                lastRemaining = s.remainingMillis();
            } else {
                assertEquals(-1, s.remainingMillis(), where);
            }
            check(s.one(), 0, where + " P1");
            check(s.two(), 1, where + " P2");
            finished = s.phase() == MatchPhase.FINISHED;
            lastPhase = s.phase();
        }

        void check(MatchSnapshot.PlayerView v, int i, String where) {
            assertTrue(v.score() >= lastScore[i], "점수가 줄었다: " + where);
            lastScore[i] = v.score();
            int[][] cells = v.cells(), items = v.items();
            assertEquals(VersusRules.BOARD_ROWS, cells.length, where);
            for (int r = 0; r < cells.length; r++) {
                assertEquals(VersusRules.BOARD_COLS, cells[r].length, where);
                for (int c = 0; c < cells[r].length; c++) {
                    int value = cells[r][c], item = items[r][c];
                    assertTrue(value >= 0 && value <= VersusRules.ATTACK_CELL, "대전 칸 값 0..9 밖 " + value + ": " + where);
                    if (item != 0) {
                        assertTrue(mode.itemsEnabled(), "아이템 없는 모드에 아이템 칸: " + where);
                        assertNotEquals(0, value, "빈칸에 아이템 표시: " + where);
                        assertNotEquals(VersusRules.ATTACK_CELL, value, "공격칸에 아이템: " + where);
                        assertTrue(isItemSymbol(item), "알 수 없는 아이템 기호 " + item + ": " + where);
                    }
                }
            }
            List<int[]> pending = v.pendingRows();
            assertTrue(pending.size() <= VersusRules.MAX_PENDING_ROWS, "대기열 10줄 초과: " + where);
            for (int[] row : pending) {
                assertEquals(VersusRules.BOARD_COLS, row.length, where);
                for (int value : row) assertTrue(value == 0 || value == VersusRules.ATTACK_CELL, "대기열 값: " + where);
            }
        }

        static boolean isItemSymbol(int symbol) {
            for (ItemType type : ItemType.values()) if (type.symbol() == symbol) return true;
            return false;
        }
    }
}
