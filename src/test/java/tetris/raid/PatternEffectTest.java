package tetris.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.OBlock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.raid.BossPattern;
import tetris.feature.raid.PatternContext;
import tetris.feature.raid.Patterns;
import tetris.feature.raid.api.PatternId;
import tetris.feature.setting.ControlStyle;
import tetris.feature.versus.api.AttackRows;
import tetris.feature.versus.api.VersusRules;

@Timeout(30)
class PatternEffectTest {
    @Test
    void eachPatternChangesTheBoardQueueOrSession() {
        assertCloud(apply(PatternId.CLOUD, 1, 0, false));
        assertSummon(apply(PatternId.SUMMON_BLOCK, 1, 0, false));
        assertRock(apply(PatternId.ROCK_THROW, 1, 0, false), 2);
        assertRock(apply(PatternId.ROCK_THROW, 4, 0, false), 3);
        assertRock(apply(PatternId.ROCK_THROW, 1, 2, false), 1);
        assertEquals(0, apply(PatternId.SUMMON_BLOCK, 1, 2, false).gray);
        assertPillar(apply(PatternId.PILLAR, 1, 0, false));
        assertCurse(apply(PatternId.CURSE_BLOCKS, 1, 0, false));
        Harness seal = apply(PatternId.SEAL_PREVIEW, 1, 0, false);
        assertEquals(PatternId.SEAL_PREVIEW, seal.effect);
        assertEquals(4, seal.remaining);
        assertEquals(2, apply(PatternId.SEAL_PREVIEW, 1, 2, false).remaining);
        Harness overload = apply(PatternId.OVERLOAD, 1, 0, false);
        assertEquals(2.0, overload.session.gravityMultiplier());
        assertEquals(5, overload.remaining);
        assertEquals(2, apply(PatternId.OVERLOAD, 1, 2, false).remaining);
        Harness chain = apply(PatternId.CHAIN, 1, 0, false);
        assertEquals(1, chain.rows);
        assertEquals(null, chain.effect);
        Harness coopChain = apply(PatternId.CHAIN, 1, 0, true);
        assertEquals(1, coopChain.rows);
        assertEquals(PatternId.CHAIN, coopChain.effect);
        assertEquals(8, coopChain.remaining);
        assertEquals(PatternId.REVERSE, apply(PatternId.REVERSE, 1, 0, false).effect);
        assertEquals(1, apply(PatternId.REVERSE, 1, 2, false).remaining);
        assertTrue(apply(PatternId.SPLIT, 1, 0, false).split);
        assertRock(apply(PatternId.EARTHQUAKE, 1, 0, false), 1);
        assertRock(apply(PatternId.FIRE_BREATH, 1, 0, false), 4);
        assertRock(apply(PatternId.FIRE_BREATH, 4, 0, false), 6);
        Harness fire = apply(PatternId.FIRE_BREATH, 1, 0, true);
        assertEquals(2, fire.rows);
        assertEquals(2, fire.targets);
    }

    private static void assertCloud(Harness harness) {
        assertEquals(PatternId.CLOUD, harness.effect);
        assertEquals(5, harness.remaining);
        assertEquals(4, harness.area.length);
        assertEquals(4, harness.area[2]);
        assertEquals(3, harness.area[3]);
        assertTrue(harness.area[0] >= 0 && harness.area[0] <= 16);
        assertTrue(harness.area[1] >= 0 && harness.area[1] <= 7);
    }

    private static void assertSummon(Harness harness) {
        int gray = 0;
        int[][] cells = harness.session.snapshot().cells();
        for (int row = 10; row < 20; row++) for (int col = 0; col < 10; col++) if (cells[row][col] == VersusRules.ATTACK_CELL) gray++;
        assertTrue(gray >= 3 && gray <= 4, "chunk " + gray);
    }

    private static void assertRock(Harness harness, int rows) {
        assertEquals(rows, harness.rows);
        for (int[] row : harness.attack.rows()) {
            int holes = 0;
            for (int value : row) {
                if (value == 0) holes++;
                else assertEquals(VersusRules.ATTACK_CELL, value);
            }
            assertEquals(1, holes);
        }
    }

    private static void assertPillar(Harness harness) {
        int[][] cells = harness.session.snapshot().cells();
        int columns = 0;
        for (int col = 0; col < 10; col++) {
            int gray = 0;
            for (int row = 16; row < 20; row++) if (cells[row][col] == VersusRules.ATTACK_CELL) gray++;
            if (gray == 4) columns++;
        }
        assertEquals(1, columns);
    }

    private static void assertCurse(Harness harness) {
        assertTrue(harness.session.peekUpcomingType(0) == BlockType.S || harness.session.peekUpcomingType(0) == BlockType.Z);
        assertTrue(harness.session.peekUpcomingType(1) == BlockType.S || harness.session.peekUpcomingType(1) == BlockType.Z);
    }

    private static Harness apply(PatternId id, int level, int blockLines, boolean coop) {
        Harness harness = new Harness(level, blockLines, coop);
        BossPattern pattern = Patterns.of(id);
        assertEquals(id, pattern.id());
        pattern.apply(harness);
        return harness;
    }

    private static final class Harness implements PatternContext {
        final GameSession session = new GameSession(OBlock::new, new tetris.feature.game.GameClock() {
            public void start(int intervalMillis) { }
            public void stop() { }
        }, new GameListener() {
            public void onChanged(GameSnapshot state) { }
            public void onGameOver(GameSnapshot state) { }
        });
        final int level;
        final int blockLines;
        final boolean coop;
        final Random random = new Random(3);
        AttackRows attack = AttackRows.NONE;
        int rows;
        int targets;
        PatternId effect;
        int remaining;
        int[] area = new int[0];
        boolean split;
        int gray;

        Harness(int level, int blockLines, boolean coop) {
            this.level = level;
            this.blockLines = blockLines;
            this.coop = coop;
            session.start();
        }

        public int level() { return level; }
        public boolean coop() { return coop; }
        public int target() { return 0; }
        public int blockLines() { return blockLines; }
        public Random random() { return random; }
        public int players() { return coop ? 2 : 1; }
        public GameSession session(int player) { return session; }

        public void queueAttack(int player, AttackRows rows) {
            attack = rows;
            this.rows = rows.size();
            targets++;
        }

        public void addEffect(int player, PatternId pattern, int remainingPieces, int[] area) {
            effect = pattern;
            remaining = remainingPieces;
            this.area = area;
        }

        public void forceNext(int player, Block... pieces) {
            session.forceUpcoming(pieces);
        }

        public void setGravity(int player, double multiplier) {
            session.setGravityMultiplier(multiplier);
        }

        public void split() {
            split = true;
        }
    }
}
