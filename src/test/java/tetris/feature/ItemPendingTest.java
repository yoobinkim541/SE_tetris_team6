package tetris.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.WeightBlock;
import tetris.feature.game.*;
import tetris.feature.rule.ItemBlockGenerator;

class ItemPendingTest {
    private static Block row() {
        int[][] shape = new int[10][10];
        Arrays.fill(shape[0], 1);
        return new Block(shape) {
            @Override public BlockType getType() { return BlockType.I; }
        };
    }

    private static boolean hasItem(Block block) {
        if (block.isWeight()) return true;
        for (int r = 0; r < block.getShape().length; r++)
            for (int c = 0; c < block.getShape()[r].length; c++)
                if (block.itemCellAt(r, c).isPresent()) return true;
        return false;
    }

    private static void clearLine(GameSession session) {
        session.hardDrop();
        assertEquals(GameState.CLEARING, session.getState());
        session.tick();
    }

    @Test
    void tenthLineReservesNewNextOnceAndKeepsExistingNextOrdinary() {
        ItemBlockGenerator generator = new ItemBlockGenerator(ItemPendingTest::row, WeightBlock::new, new Random(42));
        GameSession session = new GameSession(GameMode.ITEM, Difficulty.HARD, generator,
                new GameTestSupport.RecordingClock(), new GameTestSupport.RecordingListener());
        session.start();
        assertEquals(Difficulty.NORMAL, session.getDifficulty());
        assertEquals(10, session.snapshot().nextItemLines());
        for (int i = 0; i < 9; i++) {
            clearLine(session);
            assertFalse(hasItem(session.snapshot().nextBlock()));
        }
        assertEquals(1, session.snapshot().nextItemLines());
        clearLine(session);
        assertFalse(hasItem(session.snapshot().currentBlock()));
        assertTrue(hasItem(session.snapshot().nextBlock()));
        assertEquals(10, session.snapshot().nextItemLines());
        clearLine(session);
        assertTrue(hasItem(session.snapshot().currentBlock()));
        assertFalse(hasItem(session.snapshot().nextBlock()));
        session.pause();
        session.restart();
        assertEquals(0, session.snapshot().lines());
        assertEquals(10, session.snapshot().nextItemLines());
        assertFalse(hasItem(session.snapshot().nextBlock()));
    }

    @Test
    void classicNeverReservesItems() {
        GameSession session = new GameSession(GameMode.CLASSIC, Difficulty.EASY, ItemPendingTest::row,
                new GameTestSupport.RecordingClock(), new GameTestSupport.RecordingListener());
        session.start();
        for (int i = 0; i < 20; i++) clearLine(session);
        assertEquals(Difficulty.EASY, session.getDifficulty());
        assertEquals(0, session.snapshot().nextItemLines());
        assertFalse(hasItem(session.snapshot().nextBlock()));
    }

    @Test
    void itemModeWrapsPlainGeneratorAndClassicUsesDifficultyIntervals() {
        GameSession item = new GameSession(GameMode.ITEM, Difficulty.EASY, ItemPendingTest::row,
                new GameTestSupport.RecordingClock(), new GameTestSupport.RecordingListener());
        item.start();
        for (int i = 0; i < 10; i++) clearLine(item);
        assertTrue(hasItem(item.snapshot().nextBlock()));
        for (Difficulty difficulty : Difficulty.values()) {
            GameTestSupport.RecordingClock clock = new GameTestSupport.RecordingClock();
            GameSession classic = new GameSession(GameMode.CLASSIC, difficulty, ItemPendingTest::row,
                    clock, new GameTestSupport.RecordingListener());
            classic.start();
            for (int i = 0; i < 5; i++) clearLine(classic);
            assertEquals(1000 - difficulty.levelStepMillis(), clock.starts.getLast());
        }
    }
}
