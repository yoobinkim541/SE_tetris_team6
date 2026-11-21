package tetris.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.feature.data.Board;
import tetris.feature.game.GameSession;
import tetris.feature.raid.RaidEngine;
import tetris.feature.raid.RaidRules;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.CardId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidConfig;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.setting.ControlStyle;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.MatchClock;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

@Timeout(60)
class RaidEngineTest {
    @Test
    void soloStartsInCountdownThenPlaysAtLevelOneHp() {
        Harness harness = Harness.solo(1);
        RaidSnapshot start = harness.engine.snapshot();
        assertEquals(RaidPhase.COUNTDOWN, start.phase());
        assertEquals(1, start.players().size());
        assertEquals(165, start.boss().maxHp());
        assertEquals(8, start.boss().piecesLeft());
        assertTrue(start.boss().id() != null);
        assertTrue(start.events().stream().anyMatch(event -> event.type() == RaidEvent.Type.BOSS_SPAWN));
        harness.ready();
        assertEquals(RaidPhase.PLAYING, harness.engine.snapshot().phase());
        assertEquals(165, harness.engine.snapshot().boss().hp());
        harness.drop(0);
        if (harness.engine.snapshot().boss().id() != BossId.GOLDEN_TETRO) {
            assertEquals(7, harness.engine.snapshot().boss().piecesLeft());
        }
    }

    @Test
    void lineClearDealsTheTableAndComboCritSync() throws Exception {
        Harness harness = Harness.solo(2, (boss, level, previous, before, random) -> PatternId.SEAL_PREVIEW);
        harness.ready();
        int hp = harness.engine.snapshot().boss().hp();
        harness.clearTwo(0);
        assertEquals(hp - 25, harness.engine.snapshot().boss().hp());
        assertEquals(25, harness.event(RaidEvent.Type.DAMAGE).value());
        harness.clearTwo(0);
        assertEquals(30, harness.event(RaidEvent.Type.DAMAGE).value(), "combo 2 adds 5");
        assertEquals(2, harness.event(RaidEvent.Type.COMBO).value());

        Harness crit = Harness.solo(3, (boss, level, previous, before, random) -> PatternId.SEAL_PREVIEW);
        crit.ready();
        setLucky(crit.engine, 0);
        int hpBeforeCrit = crit.engine.snapshot().boss().hp();
        crit.clearTwo(0);
        assertEquals(hpBeforeCrit - 50, crit.engine.snapshot().boss().hp());
        assertEquals(50, crit.event(RaidEvent.Type.DAMAGE).value());
        assertNotNull(crit.event(RaidEvent.Type.CRITICAL));

        Harness coop = Harness.coop(4, (boss, level, previous, before, random) -> PatternId.SEAL_PREVIEW);
        coop.ready();
        int shared = coop.engine.snapshot().boss().hp();
        coop.clearTwo(0);
        coop.clearTwo(1);
        assertEquals(shared - 25 - 38, coop.engine.snapshot().boss().hp());
        assertNotNull(coop.event(RaidEvent.Type.SYNC));
        assertEquals(20, coop.engine.snapshot().gauge() - 40, "10 per line plus sync 20");
    }

    @Test
    void tSpinMultiplierIsAppliedByTheSharedDamageFunction() {
        assertEquals(38, tetris.feature.raid.RaidDamage.of(2, true, 1, false, false, 0));
        assertEquals(15, tetris.feature.raid.RaidDamage.of(1, true, 1, false, false, 0));
    }

    @Test
    void scriptedRockFiresAfterEightPiecesAndCountdownResets() {
        Harness harness = Harness.solo(6, (boss, level, previous, before, random) -> PatternId.ROCK_THROW);
        harness.ready();
        assertEquals(8, harness.engine.snapshot().boss().piecesLeft());
        for (int i = 0; i < 7; i++) harness.drop(0);
        assertEquals(1, harness.engine.snapshot().boss().piecesLeft());
        assertFalse(hasGray(harness.engine.snapshot()));
        harness.drop(0);
        assertTrue(hasGray(harness.engine.snapshot()), "rock inserts garbage before the next spawn");
        assertEquals(8, harness.engine.snapshot().boss().piecesLeft());
        assertNotNull(harness.event(RaidEvent.Type.BOSS_ATTACK));
    }

    @Test
    void blockWeakensAtTwoLinesAndNullifiesAtFour() throws Exception {
        Harness harness = Harness.coop(7, (boss, level, previous, before, random) -> PatternId.ROCK_THROW);
        harness.ready();
        assertEquals(0, harness.engine.snapshot().boss().target());
        harness.clearTwo(1);
        for (int i = 0; i < 8; i++) harness.drop(0);
        int grayRows = grayRows(harness.engine.snapshot().players().get(0).board().cells());
        assertEquals(1, grayRows, "2 lines weaken rock from 2 rows to 1");
        assertEquals(1, harness.event(RaidEvent.Type.BLOCKED).value());

        Harness full = Harness.coop(8, (boss, level, previous, before, random) -> PatternId.ROCK_THROW);
        full.ready();
        full.clearTwo(1);
        full.clearTwo(1);
        for (int i = 0; i < 8; i++) full.drop(0);
        assertEquals(0, grayRows(full.engine.snapshot().players().get(0).board().cells()));
        assertEquals(2, full.event(RaidEvent.Type.BLOCKED).value());
    }

    @Test
    void cleanseShortensCloudAndReviveNeedsEightThenTwelve() throws Exception {
        Harness harness = Harness.coop(9, (boss, level, previous, before, random) -> PatternId.CLOUD);
        harness.ready();
        assertEquals(0, harness.engine.snapshot().boss().target());
        for (int i = 0; i < 8; i++) harness.drop(0);
        int remaining = effect(harness.engine, 0, PatternId.CLOUD);
        assertEquals(5, remaining);
        harness.clearTwo(1);
        assertEquals(4, effect(harness.engine, 0, PatternId.CLOUD));
        assertNotNull(harness.event(RaidEvent.Type.CLEANSED));

        Harness revive = Harness.coop(10, (boss, level, previous, before, random) -> PatternId.SEAL_PREVIEW);
        revive.ready();
        for (int i = 0; i < 12 && revive.engine.snapshot().players().get(0).alive(); i++) revive.drop(0);
        assertFalse(revive.engine.snapshot().players().get(0).alive());
        assertEquals(8, revive.engine.snapshot().players().get(0).reviveRequired());
        for (int i = 0; i < 4; i++) revive.clearTwo(1);
        assertTrue(revive.engine.snapshot().players().get(0).alive());
        assertNotNull(revive.event(RaidEvent.Type.REVIVED));
        for (int i = 0; i < 12 && revive.engine.snapshot().players().get(0).alive(); i++) revive.drop(0);
        assertFalse(revive.engine.snapshot().players().get(0).alive());
        assertEquals(12, revive.engine.snapshot().players().get(0).reviveRequired());
    }

    @Test
    void gaugeUltimateFiresOnlyInsideTheWindowAndNullifiesTheNextPattern() throws Exception {
        Harness harness = Harness.coop(11, (boss, level, previous, before, random) -> PatternId.ROCK_THROW);
        harness.ready();
        setGauge(harness.engine, 100);
        int hp = harness.engine.snapshot().boss().hp();
        harness.engine.ultimate(PlayerId.ONE);
        harness.now += 1_501;
        harness.engine.advance(harness.now);
        harness.engine.ultimate(PlayerId.TWO);
        assertEquals(hp, harness.engine.snapshot().boss().hp());
        harness.now += 1_501;
        harness.engine.advance(harness.now);
        harness.engine.ultimate(PlayerId.ONE);
        harness.now += 400;
        harness.engine.advance(harness.now);
        harness.engine.ultimate(PlayerId.TWO);
        assertEquals(hp - 150, harness.engine.snapshot().boss().hp());
        assertEquals(0, harness.engine.snapshot().gauge());
        assertNotNull(harness.event(RaidEvent.Type.ULTIMATE));
        int left = harness.engine.snapshot().boss().piecesLeft();
        for (int i = 0; i < left; i++) harness.drop(0);
        assertNotNull(harness.event(RaidEvent.Type.SHIELDED));
        assertFalse(hasGray(harness.engine.snapshot()));
    }

    @Test
    void cardTimeoutPicksTheMiddleAndEachCardHasItsEffect() throws Exception {
        Harness harness = killOne(12);
        List<CardId> cards = harness.engine.snapshot().offer().cards();
        assertEquals(10_000, harness.engine.snapshot().offer().remainingMillis());
        harness.now += 9_999;
        harness.engine.advance(harness.now);
        assertEquals(RaidPhase.CARD_PICK, harness.engine.snapshot().phase());
        harness.now += 1;
        harness.engine.advance(harness.now);
        assertEquals(cards.get(1).ordinal(), harness.event(RaidEvent.Type.CARD_PICKED).value());
        assertNotEquals(RaidPhase.CARD_PICK, harness.engine.snapshot().phase());

        for (CardId card : CardId.values()) {
            Harness offer = offerWith(card);
            int index = offer.engine.snapshot().offer().cards().indexOf(card);
            int hp = offer.engine.snapshot().boss().hp();
            int gauge = offer.engine.snapshot().gauge();
            offer.engine.pickCard(PlayerId.ONE, index);
            assertCard(card, offer, hp, gauge);
        }
    }

    @Test
    void bossOrderCoversAllFourBeforeRepeatAndLevelScalesHp() throws Exception {
        Harness harness = Harness.solo(13, (boss, level, previous, before, random) -> PatternId.SEAL_PREVIEW);
        harness.ready();
        assertEquals(165, harness.engine.snapshot().boss().maxHp());
        Set<BossId> cycle = new HashSet<>();
        BossId last = null;
        for (int guard = 0; guard < 40 && cycle.size() < 4; guard++) {
            if (harness.engine.snapshot().phase() == RaidPhase.CARD_PICK) {
                harness.engine.pickCard(PlayerId.ONE, 0);
                if (harness.engine.snapshot().phase() == RaidPhase.CARD_PICK) harness.engine.pickCard(PlayerId.ONE, 0);
                continue;
            }
            BossId id = harness.engine.snapshot().boss().id();
            if (id != BossId.GOLDEN_TETRO && !id.equals(last)) {
                assertFalse(cycle.contains(id), "repeat before a full cycle: " + cycle);
                cycle.add(id);
                last = id;
            }
            if (harness.engine.snapshot().boss().hp() > 0) harness.clearTwo(0);
        }
        assertEquals(Set.of(BossId.JELLY_KING, BossId.STONE_GOLEM, BossId.NIGHT_WITCH, BossId.MECHA_DRAGON), cycle);
    }

    @Test
    void goldenFleesAfterFifteenSecondsAndAKillOffersTwoCards() {
        Harness golden = null;
        int found = 0;
        for (int seed = 0; seed < 80 && found < 3; seed++) {
            Harness harness = Harness.solo(seed);
            if (harness.engine.snapshot().boss().id() == BossId.GOLDEN_TETRO) {
                found++;
                if (golden == null) golden = harness;
                else harness.engine.close();
            } else harness.engine.close();
        }
        assertNotNull(golden, "5% should appear in 80 seeds");
        assertEquals(15_000, golden.engine.snapshot().boss().fleeMillis());
        golden.ready();
        assertEquals(15_000, golden.engine.snapshot().boss().fleeMillis());
        golden.now += 14_999;
        golden.engine.advance(golden.now);
        assertEquals(BossId.GOLDEN_TETRO, golden.engine.snapshot().boss().id());
        golden.now += 1;
        golden.engine.advance(golden.now);
        assertEquals(RaidEvent.Type.BOSS_ESCAPED, golden.event(RaidEvent.Type.BOSS_ESCAPED).type());
        assertEquals(0, golden.engine.snapshot().kills());
        assertEquals(1, golden.engine.snapshot().boss().level());
    }

    @Test
    void deadPlayerIsNotTargetedAndSameSeedReplays() throws Exception {
        Harness harness = Harness.coop(21, (boss, level, previous, before, random) -> PatternId.ROCK_THROW);
        harness.ready();
        for (int i = 0; i < 12 && harness.engine.snapshot().players().get(0).alive(); i++) harness.drop(0);
        assertFalse(harness.engine.snapshot().players().get(0).alive());
        assertEquals(1, harness.engine.snapshot().boss().target());
        for (int i = 0; i < 12 && harness.engine.snapshot().phase() == RaidPhase.PLAYING; i++) harness.drop(1);
        if (harness.engine.snapshot().phase() == RaidPhase.PLAYING) {
            assertEquals(1, harness.engine.snapshot().boss().target());
        }

        List<String> left = play(30);
        List<String> right = play(30);
        assertEquals(left, right);
    }

    @Test
    void pauseFreezesBothAndQuitFinishes() {
        Harness harness = Harness.coop(31);
        harness.ready();
        harness.engine.submit(new PlayerCommand(PlayerId.TWO, GameAction.PAUSE, true, 0));
        harness.now += 1;
        harness.engine.advance(harness.now);
        assertEquals(RaidPhase.PAUSED, harness.engine.snapshot().phase());
        long elapsed = harness.engine.snapshot().elapsedMillis();
        harness.now += 5_000;
        harness.engine.advance(harness.now);
        assertEquals(elapsed, harness.engine.snapshot().elapsedMillis());
        harness.engine.submit(new PlayerCommand(PlayerId.ONE, GameAction.QUIT, true, 0));
        harness.now += 1;
        harness.engine.advance(harness.now);
        assertEquals(RaidPhase.FINISHED, harness.engine.snapshot().phase());
        assertNotNull(harness.engine.snapshot().result());
        assertEquals(0, harness.engine.snapshot().result().kills());
    }

    private static List<String> play(long seed) throws Exception {
        Harness harness = Harness.solo(seed, (boss, level, previous, before, random) -> PatternId.SEAL_PREVIEW);
        harness.ready();
        List<String> digests = new ArrayList<>();
        digests.add(digest(harness.engine.snapshot()));
        for (int i = 0; i < 6; i++) {
            harness.drop(0);
            digests.add(digest(harness.engine.snapshot()));
        }
        harness.clearTwo(0);
        digests.add(digest(harness.engine.snapshot()));
        harness.engine.close();
        return digests;
    }

    private static String digest(RaidSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append(snapshot.phase()).append('|').append(snapshot.revision()).append('|')
                .append(snapshot.boss().id()).append('|').append(snapshot.boss().hp()).append('|')
                .append(snapshot.boss().piecesLeft()).append('|').append(snapshot.gauge()).append('|')
                .append(snapshot.events());
        for (RaidSnapshot.PlayerView player : snapshot.players()) {
            builder.append('|').append(player.alive()).append(player.damageDealt());
            for (int[] row : player.board().cells()) builder.append(Arrays.toString(row));
        }
        return builder.toString();
    }

    private static Harness killOne(long seed) throws Exception {
        Harness harness = Harness.solo(seed, (boss, level, previous, before, random) -> PatternId.SEAL_PREVIEW);
        harness.ready();
        while (harness.engine.snapshot().phase() == RaidPhase.PLAYING && harness.engine.snapshot().kills() == 0) {
            if (harness.engine.snapshot().boss().id() == BossId.GOLDEN_TETRO && harness.engine.snapshot().boss().hp() > 80) {
                harness.clearTwo(0);
            } else harness.clearTwo(0);
        }
        assertEquals(RaidPhase.CARD_PICK, harness.engine.snapshot().phase());
        return harness;
    }

    private static Harness offerWith(CardId card) throws Exception {
        for (int seed = 100; seed < 180; seed++) {
            Harness harness = killOne(seed);
            if (harness.engine.snapshot().offer().cards().contains(card)) return harness;
            harness.engine.close();
        }
        throw new AssertionError("card not offered: " + card);
    }

    private static void assertCard(CardId card, Harness harness, int hpBefore, int gaugeBefore) throws Exception {
        RaidSnapshot snapshot = harness.engine.snapshot();
        switch (card) {
            case SHARP_BLOCKS -> {
                harness.clearTwo(0);
                boolean crit = harness.event(RaidEvent.Type.CRITICAL) != null;
                int combo = harness.event(RaidEvent.Type.COMBO) == null ? 1 : harness.event(RaidEvent.Type.COMBO).value();
                assertEquals(tetris.feature.raid.RaidDamage.of(2, false, combo, crit, false, 1),
                        harness.event(RaidEvent.Type.DAMAGE).value());
            }
            case SHIELD -> {
                assertEquals(RaidPhase.PLAYING, snapshot.phase());
                for (int i = 0; i < snapshot.boss().piecesLeft(); i++) harness.drop(0);
                assertNotNull(harness.event(RaidEvent.Type.SHIELDED));
            }
            case TIME_WARP -> assertEquals(RaidRules.countdown(snapshot.boss().level()) + 4, snapshot.boss().piecesLeft());
            case CHEER -> assertEquals(Math.min(100, gaugeBefore + 50), snapshot.gauge());
            case TELESCOPE -> assertTrue(session(harness.engine, 0).previewCount() >= 3);
            case LUCK -> assertTrue(snapshot.phase() == RaidPhase.PLAYING);
            case SWEEP -> assertEquals(0, snapshot.players().get(0).board().cells()[19][0]);
            default -> throw new AssertionError(card);
        }
        assertTrue(hpBefore >= 0);
    }

    private static boolean hasGray(RaidSnapshot snapshot) {
        return grayRows(snapshot.players().get(0).board().cells()) > 0;
    }

    private static int grayRows(int[][] cells) {
        int rows = 0;
        for (int[] row : cells) for (int value : row) if (value == VersusRules.ATTACK_CELL) {
            rows++;
            break;
        }
        return rows;
    }

    private static int effect(RaidEngine engine, int player, PatternId pattern) {
        for (RaidSnapshot.Effect effect : engine.snapshot().players().get(player).effects()) {
            if (effect.pattern() == pattern) return effect.remainingPieces();
        }
        return -1;
    }

    private static void setGauge(RaidEngine engine, int gauge) throws Exception {
        Field field = RaidEngine.class.getDeclaredField("gauge");
        field.setAccessible(true);
        field.setInt(engine, gauge);
    }

    private static void setLucky(RaidEngine engine, int player) throws Exception {
        Field fighters = RaidEngine.class.getDeclaredField("fighters");
        fighters.setAccessible(true);
        Object fighter = java.lang.reflect.Array.get(fighters.get(engine), player);
        Field lucky = fighter.getClass().getDeclaredField("luckyFalling");
        lucky.setAccessible(true);
        lucky.setBoolean(fighter, true);
    }

    private static GameSession session(RaidEngine engine, int player) throws Exception {
        Field field = RaidEngine.class.getDeclaredField("sessions");
        field.setAccessible(true);
        return ((GameSession[]) field.get(engine))[player];
    }

    private static Board board(GameSession session) throws Exception {
        Field field = GameSession.class.getDeclaredField("board");
        field.setAccessible(true);
        return (Board) field.get(session);
    }

    private static Block cell(int row, int col) {
        Block block = new Block(new int[][] {{1}}) {
            @Override public BlockType getType() { return BlockType.O; }
        };
        block.setPosition(row, col);
        return block;
    }

    private static Block domino() {
        return new Block(new int[][] {{1, 1}}) {
            @Override public BlockType getType() { return BlockType.O; }
        };
    }

    private static void dropAt(GameSession session, int col) {
        int from = session.snapshot().currentBlock().getCol();
        for (int i = from; i > col; i--) session.moveLeft();
        for (int i = from; i < col; i++) session.moveRight();
        session.hardDrop();
    }

    private static final class Harness {
        final long[] sequence = new long[2];
        long now;
        final MatchClock clock = () -> now;
        final RaidEngine engine;

        Harness(RaidMode mode, long seed, ControlStyle style, java.util.function.IntFunction<BlockGenerator> generators,
                tetris.feature.raid.PatternSource patterns) {
            engine = new RaidEngine(new RaidConfig(mode, seed, style), clock, generators, patterns);
        }

        static Harness solo(long seed) {
            return new Harness(RaidMode.SOLO, seed, ControlStyle.CLASSIC, null, null);
        }

        static Harness solo(long seed, tetris.feature.raid.PatternSource patterns) {
            return new Harness(RaidMode.SOLO, seed, ControlStyle.CLASSIC, player -> OBlock::new, patterns);
        }

        static Harness coop(long seed) {
            return new Harness(RaidMode.COOP, seed, ControlStyle.CLASSIC, player -> OBlock::new, null);
        }

        static Harness coop(long seed, tetris.feature.raid.PatternSource patterns) {
            return new Harness(RaidMode.COOP, seed, ControlStyle.CLASSIC, player -> OBlock::new, patterns);
        }

        void ready() {
            now = 3_000;
            engine.advance(now);
        }

        void drop(int player) {
            PlayerId id = player == 0 ? PlayerId.ONE : PlayerId.TWO;
            engine.submit(new PlayerCommand(id, GameAction.HARD_DROP, false, sequence[player]++));
            engine.submit(new PlayerCommand(id, GameAction.HARD_DROP, true, sequence[player]++));
            now += 1;
            engine.advance(now);
        }

        void pump() {
            now += 1;
            engine.advance(now);
        }

        void clearTwo(int player) {
            try {
                GameSession session = session(engine, player);
                Board board = board(session);
                for (int row : new int[] {18, 19}) {
                    for (int col = 0; col < 10; col++) {
                        if (col == 4 || col == 5 || !board.isEmpty(row, col)) continue;
                        assertTrue(board.tryPlaceBlock(cell(row, col)));
                    }
                }
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
            drop(player);
            now += 150;
            engine.advance(now);
        }

        RaidEvent event(RaidEvent.Type type) {
            for (RaidEvent event : engine.snapshot().events()) if (event.type() == type) return event;
            return null;
        }
    }
}
