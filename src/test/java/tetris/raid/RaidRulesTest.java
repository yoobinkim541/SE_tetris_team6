package tetris.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.raid.AggroPolicy;
import tetris.feature.raid.PatternPicker;
import tetris.feature.raid.RaidDamage;
import tetris.feature.raid.RaidLuck;
import tetris.feature.raid.RaidRules;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidMode;

import java.util.Random;

@Timeout(30)
class RaidRulesTest {
    @Test
    void damageTableAppliesTSpinComboCritSyncAndSharpOnce() {
        assertEquals(10, RaidDamage.of(1, false, 1, false, false, 0));
        assertEquals(25, RaidDamage.of(2, false, 1, false, false, 0));
        assertEquals(45, RaidDamage.of(3, false, 1, false, false, 0));
        assertEquals(70, RaidDamage.of(4, false, 1, false, false, 0));
        assertEquals(38, RaidDamage.of(2, true, 1, false, false, 0));
        assertEquals(30, RaidDamage.of(2, false, 2, false, false, 0));
        assertEquals(35, RaidDamage.of(2, false, 3, false, false, 0));
        assertEquals(50, RaidDamage.of(2, false, 1, true, false, 0));
        assertEquals(38, RaidDamage.of(2, false, 1, false, true, 0));
        assertEquals(11, RaidDamage.of(1, false, 1, false, false, 1));
        assertEquals(0, RaidDamage.of(0, true, 4, true, true, 3));
    }

    @Test
    void hpTableAndCountdownMatchTheSpec() {
        assertEquals(300, RaidRules.hp(RaidMode.COOP, 1));
        assertEquals(390, RaidRules.hp(RaidMode.COOP, 2));
        assertEquals(505, RaidRules.hp(RaidMode.COOP, 3));
        assertEquals(660, RaidRules.hp(RaidMode.COOP, 4));
        assertEquals(858, RaidRules.hp(RaidMode.COOP, 5));
        assertEquals(165, RaidRules.hp(RaidMode.SOLO, 1));
        assertEquals(215, RaidRules.hp(RaidMode.SOLO, 2));
        assertEquals(280, RaidRules.hp(RaidMode.SOLO, 3));
        assertEquals(365, RaidRules.hp(RaidMode.SOLO, 4));
        assertEquals(8, RaidRules.countdown(1));
        assertEquals(7, RaidRules.countdown(2));
        assertEquals(5, RaidRules.countdown(4));
        assertEquals(4, RaidRules.countdown(5));
        assertEquals(4, RaidRules.countdown(9));
        assertTrue(RaidRules.goldenHp(RaidMode.COOP, 1) < RaidRules.hp(RaidMode.COOP, 1));
    }

    @Test
    void aggroClampsAndLuckDoublesTheDenomintor() {
        assertEquals(0.5, AggroPolicy.chanceP1(10, 10));
        assertEquals(0.8, AggroPolicy.chanceP1(100, 0));
        assertEquals(0.2, AggroPolicy.chanceP1(0, 100));
        assertTrue(Double.isNaN(AggroPolicy.chanceP1(0, 0)));
        Random random = new Random(1);
        int hits = 0;
        for (int i = 0; i < 1000; i++) if (RaidLuck.roll(random, false)) hits++;
        assertTrue(hits > 50 && hits < 160, "about 1 in 10, got " + hits);
        random = new Random(1);
        int doubled = 0;
        for (int i = 0; i < 1000; i++) if (RaidLuck.roll(random, true)) doubled++;
        assertTrue(doubled > hits, "doubled rate should land more luckies");
    }

    @Test
    void signatureStartsAtLevelTwoAndDoesNotRepeatThreeTimes() {
        Random random = new Random(4);
        PatternId previous = null;
        PatternId before = null;
        int signature = 0;
        for (int i = 0; i < 200; i++) {
            PatternId pick = PatternPicker.pick(BossId.STONE_GOLEM, 1, false, previous, before, random);
            assertTrue(pick == PatternId.ROCK_THROW || pick == PatternId.PILLAR);
            before = previous;
            previous = pick;
        }
        previous = null;
        before = null;
        int streak = 0;
        PatternId streakId = null;
        for (int i = 0; i < 300; i++) {
            PatternId pick = PatternPicker.pick(BossId.JELLY_KING, 3, false, previous, before, random);
            if (pick == PatternId.SPLIT) signature++;
            streak = pick == streakId ? streak + 1 : 1;
            streakId = pick;
            assertTrue(streak < 3, "three in a row: " + pick);
            before = previous;
            previous = pick;
        }
        assertTrue(signature > 0);
    }
}
