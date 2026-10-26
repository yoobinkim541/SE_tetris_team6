package tetris.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.item.*;

class GameModifiersLifecycleTest {
    @Test
    void doubleScoreCoversAcquisitionAndTenFurtherLocks() {
        GameModifiers modifiers = new GameModifiers();
        assertEquals(1, modifiers.scoreMultiplier());
        modifiers.beginLock();
        modifiers.activateDoubleScore();
        assertEquals(2, modifiers.scoreMultiplier());
        modifiers.finishLock();
        assertEquals(List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 10)), modifiers.effects());
        for (int i = 0; i < 10; i++) {
            modifiers.beginLock();
            assertEquals(2, modifiers.scoreMultiplier());
            modifiers.finishLock();
        }
        assertEquals(1, modifiers.scoreMultiplier());
        assertTrue(modifiers.effects().isEmpty());
    }

    @Test
    void reacquisitionResetsWithoutStackingOrPrematureConsumption() {
        GameModifiers modifiers = new GameModifiers();
        modifiers.beginLock();
        modifiers.activateDoubleScore();
        modifiers.finishLock();
        modifiers.beginLock();
        modifiers.finishLock();
        modifiers.beginLock();
        modifiers.activateDoubleScore();
        modifiers.activateDoubleScore();
        modifiers.finishLock();
        assertEquals(2, modifiers.scoreMultiplier());
        assertEquals(10, modifiers.effects().getFirst().remainingBlocks());
    }

    @Test
    void slowConsumesTenSpawnsAndKeepsLastBlockSlowWithZeroFutureSpawns() {
        GameModifiers modifiers = new GameModifiers();
        modifiers.activateSlow();
        assertEquals(1.0, modifiers.fallMultiplier());
        for (int i = 0; i < 10; i++) {
            modifiers.onSpawn();
            assertEquals(1.5, modifiers.fallMultiplier());
            assertEquals(List.of(new ActiveEffect(ItemType.SLOW, 9 - i)), modifiers.effects());
        }
        modifiers.onSpawn();
        assertEquals(1.0, modifiers.fallMultiplier());
        assertTrue(modifiers.effects().isEmpty());
    }

    @Test
    void slowRenewalAndDoubleScoreAreIndependentAndSnapshotsImmutable() {
        GameModifiers modifiers = new GameModifiers();
        modifiers.beginLock();
        modifiers.activateDoubleScore();
        modifiers.activateSlow();
        modifiers.finishLock();
        modifiers.onSpawn();
        List<ActiveEffect> before = modifiers.effects();
        modifiers.beginLock();
        modifiers.activateSlow();
        modifiers.finishLock();
        assertEquals(List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 9), new ActiveEffect(ItemType.SLOW, 10)), modifiers.effects());
        assertEquals(List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 10), new ActiveEffect(ItemType.SLOW, 9)), before);
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
    }
}
