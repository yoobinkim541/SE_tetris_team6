package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.OBlock;
import tetris.component.block.WeightBlock;
import tetris.feature.data.Board;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSession;
import tetris.feature.item.GameModifiers;
import tetris.feature.item.ItemType;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.*;

/** S9-A1 시간제한, S9-A2 아이템 대전 (R3-TIME, R3-MODES, R3-ITEM, R3-ATTACK) */
class BattleModesEngineTest {
    long now, sequence;

    LocalMatchEngine engine(MatchConfig config) {
        var e = new LocalMatchEngine(() -> now, id -> OBlock::new);
        e.start(config);
        return e;
    }

    static MatchConfig timed(long limit) {
        return new MatchConfig(BattleMode.TIMED, Difficulty.NORMAL, limit);
    }

    void press(LocalMatchEngine e, PlayerId p, GameAction a) {
        e.submit(new PlayerCommand(p, a, true, sequence++));
    }

    void tap(LocalMatchEngine e, PlayerId p, GameAction a) {
        e.submit(new PlayerCommand(p, a, true, sequence++));
        e.submit(new PlayerCommand(p, a, false, sequence++));
    }

    static GameSession session(LocalMatchEngine e, PlayerId p) throws Exception {
        Field f = LocalMatchEngine.class.getDeclaredField("sessions");
        f.setAccessible(true);
        return ((GameSession[]) f.get(e))[p.ordinal()];
    }

    static Board board(GameSession s) throws Exception {
        return GarbageInsertionTest.board(s);
    }

    static void fill(GameSession s, int row, int... holes) throws Exception {
        Board b = board(s);
        outer:
        for (int c = 0; c < 10; c++) {
            for (int h : holes) if (h == c) continue outer;
            assertTrue(b.tryPlaceBlock(GarbageInsertionTest.cell(row, c)));
        }
    }

    // ── S9-A1 시간제한 ─────────────────────────────

    @Test
    void classicAndItemHaveNoClockButTimedCountsDownFromConfiguredLimit() {
        assertEquals(-1, engine(MatchConfig.of(BattleMode.CLASSIC)).snapshot().remainingMillis());
        assertEquals(-1, engine(MatchConfig.of(BattleMode.ITEM)).snapshot().remainingMillis());
        var e = engine(timed(10_000));
        assertEquals(10_000, e.snapshot().remainingMillis());
        e.advance(4_000);
        assertEquals(6_000, e.snapshot().remainingMillis());
        assertEquals(MatchPhase.PLAYING, e.snapshot().phase());
        assertEquals(VersusRules.DEFAULT_TIME_LIMIT_MILLIS, engine(MatchConfig.of(BattleMode.TIMED)).snapshot().remainingMillis());
    }

    @Test
    void timeUpComparesSettledScoresAndKeepsAttackRules() throws Exception {
        var e = engine(timed(10_000));
        fill(session(e, PlayerId.ONE), 18, 4, 5);
        fill(session(e, PlayerId.ONE), 19, 4, 5);
        press(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(0);
        e.advance(150);
        assertEquals(2, e.snapshot().two().pendingRows().size(), "normal attack still applies in TIMED");
        e.advance(9_999);
        assertEquals(MatchPhase.PLAYING, e.snapshot().phase());
        assertEquals(1, e.snapshot().remainingMillis());
        e.advance(10_000);
        var end = e.snapshot();
        assertEquals(MatchPhase.FINISHED, end.phase());
        assertEquals(MatchResult.win(PlayerId.ONE, MatchResult.EndReason.TIME_UP), end.result());
        assertEquals(0, end.remainingMillis());
        assertTrue(end.one().score() > end.two().score());
    }

    @Test
    void equalScoresAtDeadlineAreADraw() {
        var e = engine(timed(5_000));
        e.advance(5_000);
        assertEquals(MatchResult.draw(MatchResult.EndReason.TIME_UP), e.snapshot().result());
    }

    @Test
    void inputAtOrAfterDeadlineIsIgnoredButInputBeforeItCounts() {
        var late = engine(timed(5_000));
        now = 5_000;
        press(late, PlayerId.ONE, GameAction.HARD_DROP);
        late.advance(5_000);
        assertTrue(late.snapshot().result().isDraw(), "input at the deadline must not score");

        now = 0;
        var early = engine(timed(5_000));
        now = 4_999;
        press(early, PlayerId.ONE, GameAction.HARD_DROP);
        early.advance(5_000);
        assertEquals(PlayerId.ONE, early.snapshot().result().winner(), "input queued before the deadline counts");
    }

    @Test
    void gravityTickExactlyAtDeadlineDoesNotScore() {
        var e = engine(timed(1_000));
        e.advance(1_000);
        assertEquals(0, e.snapshot().one().score());
        assertEquals(0, e.snapshot().two().score());
    }

    @Test
    void clearingStillInProgressAtDeadlineIsExcludedFromTheComparison() throws Exception {
        var e = engine(timed(1_000));
        fill(session(e, PlayerId.ONE), 19, 4, 5);
        fill(session(e, PlayerId.ONE), 18, 4, 5);
        now = 900;
        press(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(900);
        long hardDropOnly = e.snapshot().one().score();
        e.advance(1_100);
        var end = e.snapshot();
        assertEquals(MatchResult.EndReason.TIME_UP, end.result().reason());
        assertEquals(hardDropOnly, end.one().score(), "line clear bonus resolves after the deadline");
        assertTrue(end.two().pendingRows().isEmpty(), "unsettled clear publishes no attack");
    }

    @Test
    void pauseFreezesTheRemainingTime() {
        var e = engine(timed(10_000));
        e.advance(2_000);
        now = 2_000;
        press(e, PlayerId.ONE, GameAction.PAUSE);
        e.advance(2_000);
        assertEquals(MatchPhase.PAUSED, e.snapshot().phase());
        assertEquals(8_000, e.snapshot().remainingMillis());
        e.advance(50_000);
        assertEquals(MatchPhase.PAUSED, e.snapshot().phase());
        assertEquals(8_000, e.snapshot().remainingMillis());
        now = 50_000;
        e.submit(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, false, sequence++));
        press(e, PlayerId.ONE, GameAction.PAUSE);
        e.advance(50_000);
        assertEquals(MatchPhase.PLAYING, e.snapshot().phase());
        assertEquals(8_000, e.snapshot().remainingMillis());
        e.advance(57_999);
        assertEquals(MatchPhase.PLAYING, e.snapshot().phase());
        e.advance(58_000);
        assertEquals(MatchResult.EndReason.TIME_UP, e.snapshot().result().reason());
    }

    @Test
    void topOutInTheDeadlinePulseBeatsTimeUp() {
        // Count how many drops it takes P1 to top out, then make the last one land in the deadline pulse.
        var probe = engine(MatchConfig.of(BattleMode.CLASSIC));
        int drops = 0;
        while (probe.snapshot().phase() != MatchPhase.FINISHED) {
            tap(probe, PlayerId.ONE, GameAction.HARD_DROP);
            probe.advance(drops++);
        }
        int needed = drops;
        var e = engine(timed(needed));
        for (int i = 0; i < needed; i++) {
            now = i;
            tap(e, PlayerId.ONE, GameAction.HARD_DROP);
            if (i < needed - 1) e.advance(i);
        }
        e.advance(needed);
        assertEquals(MatchResult.win(PlayerId.TWO, MatchResult.EndReason.TOPPED_OUT), e.snapshot().result());
    }

    @Test
    void inputAfterTimeUpIsIgnored() {
        var e = engine(timed(1_000));
        e.advance(1_000);
        var end = e.snapshot();
        now = 2_000;
        press(e, PlayerId.TWO, GameAction.HARD_DROP);
        e.advance(2_000);
        assertEquals(end, e.snapshot());
    }

    @Test
    void timedSessionsUseNormalBlocksOnly() throws Exception {
        var e = engine(MatchConfig.of(BattleMode.TIMED));
        assertEquals(GameMode.CLASSIC, session(e, PlayerId.ONE).getMode());
        assertEquals(GameMode.CLASSIC, session(e, PlayerId.TWO).getMode());
    }

    // ── S9-A2 아이템 대전 ─────────────────────────

    /** Returns queued blocks first, then plain O blocks. */
    static final class Scripted implements BlockGenerator {
        final Deque<Supplier<Block>> queue = new ArrayDeque<>();
        @Override public Block next() {
            return queue.isEmpty() ? new OBlock() : queue.removeFirst().get();
        }
    }

    final Scripted oneBlocks = new Scripted();
    final Scripted twoBlocks = new Scripted();

    LocalMatchEngine itemEngine() {
        var e = new LocalMatchEngine(() -> now, id -> id == PlayerId.ONE ? oneBlocks : twoBlocks);
        e.start(MatchConfig.of(BattleMode.ITEM));
        return e;
    }

    static Supplier<Block> oWith(int r, int c, ItemType item) {
        return () -> {
            var b = new OBlock();
            b.attachItem(r, c, item);
            return b;
        };
    }

    @Test
    void itemModeRunsBothSessionsInItemModeWithIndependentGenerators() throws Exception {
        var e = itemEngine();
        assertEquals(GameMode.ITEM, session(e, PlayerId.ONE).getMode());
        assertEquals(GameMode.ITEM, session(e, PlayerId.TWO).getMode());
        Field g = GameSession.class.getDeclaredField("generator");
        g.setAccessible(true);
        assertNotSame(g.get(session(e, PlayerId.ONE)), g.get(session(e, PlayerId.TWO)));
    }

    @Test
    void naturalRowPlusSeparateLineItemRowSendsTwoRows() throws Exception {
        // First spawned block is the current one; the queue feeds it.
        oneBlocks.queue.add(oWith(0, 0, ItemType.LINE_CLEAR));
        var e = itemEngine();
        var one = session(e, PlayerId.ONE);
        fill(one, 19, 4, 5);
        assertTrue(board(one).tryPlaceBlock(GarbageInsertionTest.cell(18, 0)));
        press(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(0);
        e.advance(150);
        var pending = e.snapshot().two().pendingRows();
        assertEquals(2, pending.size(), "natural row 19 + L row 18");
        assertArrayEquals(new int[]{9, 0, 0, 0, 0, 0, 0, 0, 0, 0}, pending.get(0));
    }

    @Test
    void lineItemOnTheNaturalRowCountsOnce() throws Exception {
        oneBlocks.queue.add(oWith(1, 0, ItemType.LINE_CLEAR));
        var e = itemEngine();
        fill(session(e, PlayerId.ONE), 19, 4, 5);
        press(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(0);
        e.advance(150);
        assertTrue(e.snapshot().two().pendingRows().isEmpty(), "union of the same row is one row");
        assertTrue(e.snapshot().one().score() > 0);
    }

    @Test
    void bombAndWeightCellDeletionsNeverAttack() throws Exception {
        oneBlocks.queue.add(oWith(1, 1, ItemType.BOMB));
        oneBlocks.queue.add(WeightBlock::new);
        var e = itemEngine();
        var one = session(e, PlayerId.ONE);
        fill(one, 19, 4, 5, 6);
        fill(one, 18, 4, 5, 6);
        press(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(0);
        e.advance(150);
        assertTrue(e.snapshot().two().pendingRows().isEmpty(), "bomb cells are not rows");
        e.submit(new PlayerCommand(PlayerId.ONE, GameAction.HARD_DROP, false, sequence++));
        e.advance(151);
        assertTrue(session(e, PlayerId.ONE).snapshot().currentBlock().isWeight());
        press(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(152);
        e.advance(400);
        assertTrue(e.snapshot().two().pendingRows().isEmpty(), "weight crush is not a row");
    }

    @Test
    void attackRowsNeverCarryItemMetadataIntoTheReceiver() throws Exception {
        oneBlocks.queue.add(oWith(0, 0, ItemType.LINE_CLEAR));
        var e = itemEngine();
        var one = session(e, PlayerId.ONE);
        var two = session(e, PlayerId.TWO);
        fill(one, 19, 4, 5);
        Block marked = GarbageInsertionTest.cell(18, 0);
        marked.attachItem(0, 0, ItemType.DOUBLE_SCORE);
        assertTrue(board(one).tryPlaceBlock(marked));
        press(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(0);
        e.advance(150);
        press(e, PlayerId.TWO, GameAction.HARD_DROP);
        e.advance(151);
        assertEquals(9, two.snapshot().cells()[19][0]);
        assertTrue(two.snapshot().lockedItems().isEmpty(), "attack cells have no items");
    }

    @Test
    void doubleScoreAndSlowEffectsStayInTheirOwnSession() throws Exception {
        oneBlocks.queue.add(oWith(0, 0, ItemType.DOUBLE_SCORE));
        oneBlocks.queue.add(oWith(0, 0, ItemType.SLOW));
        var e = itemEngine();
        press(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(0);
        e.submit(new PlayerCommand(PlayerId.ONE, GameAction.HARD_DROP, false, sequence++));
        press(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(1);
        GameModifiers mine = modifiers(session(e, PlayerId.ONE));
        GameModifiers theirs = modifiers(session(e, PlayerId.TWO));
        assertEquals(2, mine.scoreMultiplier());
        assertTrue(mine.fallMultiplier() > 1.0);
        assertEquals(1, theirs.scoreMultiplier());
        assertEquals(1.0, theirs.fallMultiplier());
        assertEquals(0, e.snapshot().two().score());
    }

    @Test
    void tenLineItemReservationIsPerPlayer() throws Exception {
        var e = itemEngine();
        var one = session(e, PlayerId.ONE);
        Field lines = GameSession.class.getDeclaredField("lines");
        lines.setAccessible(true);
        lines.setInt(one, 9);
        fill(one, 19, 4, 5);
        press(e, PlayerId.ONE, GameAction.HARD_DROP);
        e.advance(0);
        e.advance(150);
        // The reservation is consumed as soon as the Next preview refills on Spawn.
        assertTrue(isItemBlock(one.snapshot().nextBlock()), "P1 crossed 10 lines");
        assertFalse(isItemBlock(session(e, PlayerId.TWO).snapshot().nextBlock()), "P2 reservation untouched");
        assertFalse(itemPending(session(e, PlayerId.TWO)));
    }

    @Test
    void snapshotMarksItemCellsWithTheirSymbols() {
        oneBlocks.queue.add(oWith(0, 0, ItemType.BOMB));
        var e = itemEngine();
        int[][] cells = e.snapshot().one().cells();
        int[][] items = e.snapshot().one().items();
        int marked = 0;
        for (int r = 0; r < 20; r++) for (int c = 0; c < 10; c++) {
            if (items[r][c] != 0) {
                marked++;
                assertEquals('B', items[r][c]);
                assertNotEquals(0, cells[r][c]);
            }
        }
        assertEquals(1, marked);
        for (int[] row : e.snapshot().two().items()) for (int v : row) assertEquals(0, v);
    }

    static GameModifiers modifiers(GameSession s) throws Exception {
        Field f = GameSession.class.getDeclaredField("modifiers");
        f.setAccessible(true);
        return (GameModifiers) f.get(s);
    }

    static boolean isItemBlock(Block block) {
        if (block.isWeight()) return true;
        int[][] shape = block.getShape();
        for (int r = 0; r < shape.length; r++) for (int c = 0; c < shape[r].length; c++) {
            if (block.itemCellAt(r, c).isPresent()) return true;
        }
        return false;
    }

    static boolean itemPending(GameSession s) throws Exception {
        Field g = GameSession.class.getDeclaredField("generator");
        g.setAccessible(true);
        Object generator = g.get(s);
        Field p = generator.getClass().getDeclaredField("itemPending");
        p.setAccessible(true);
        return p.getBoolean(generator);
    }
}
