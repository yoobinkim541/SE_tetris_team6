package tetris.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.WeightBlock;
import tetris.feature.data.Board;
import tetris.feature.game.*;
import tetris.feature.item.ActiveEffect;
import tetris.feature.item.ItemType;
import tetris.feature.rule.ItemBlockGenerator;
import tetris.feature.rule.LevelPolicy;

/** Cross-feature lifecycle checks using actual tetrominoes and item hooks. */
class StrictLifecycleAuditTest {
    private static Board board(GameSession session) throws Exception {
        Field field = GameSession.class.getDeclaredField("board");
        field.setAccessible(true);
        return (Board) field.get(session);
    }

    private static void fillExcept(Board board, int row, int... holes) {
        for (int col = 0; col < Board.WIDTH; col++) {
            boolean hole = false;
            for (int excluded : holes) hole |= col == excluded;
            if (hole) continue;
            Block cell = GameTestSupport.cell();
            cell.setPosition(row, col);
            assertTrue(board.tryPlaceBlock(cell));
        }
    }

    private static void prepareSingleLine(GameSession session) throws Exception {
        Board board = board(session);
        board.reset();
        fillExcept(board, 19, 3, 4, 5, 6);
    }

    private static void assertFrozen(GameSnapshot before, GameSnapshot after) {
        assertEquals(before.state(), after.state());
        assertEquals(before.score(), after.score());
        assertEquals(before.level(), after.level());
        assertEquals(before.lines(), after.lines());
        assertEquals(before.effects(), after.effects());
        assertEquals(before.nextItemLines(), after.nextItemLines());
        for (int row = 0; row < Board.HEIGHT; row++)
            assertArrayEquals(before.cells()[row], after.cells()[row]);
        if (before.currentBlock() == null) assertNull(after.currentBlock());
        else {
            assertEquals(before.currentBlock().getRow(), after.currentBlock().getRow());
            assertEquals(before.currentBlock().getCol(), after.currentBlock().getCol());
            assertEquals(before.currentBlock().getRotation(), after.currentBlock().getRotation());
        }
    }

    @Test
    void acquiredDoubleScoreSurvivesPausedClearingAndQuitCancellation() throws Exception {
        IBlock item = new IBlock();
        item.attachItem(1, 0, ItemType.DOUBLE_SCORE);
        GameSession session = GameTestSupport.startedSession(item, new IBlock());
        prepareSingleLine(session);
        session.hardDrop();
        assertEquals(GameState.CLEARING, session.getState());
        assertEquals(List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 10)), session.snapshot().effects());
        session.pause();
        session.requestQuit();
        GameSnapshot frozen = session.snapshot();
        session.tick();
        session.moveLeft();
        session.moveRight();
        session.moveDown();
        session.rotateRight();
        session.hardDrop();
        session.resume();
        session.restart();
        assertFrozen(frozen, session.snapshot());
        session.cancelQuit();
        assertFrozen(frozen, session.snapshot());
        session.resume();
        assertEquals(GameState.CLEARING, session.getState());
        long before = session.snapshot().score();
        session.tick();
        assertEquals(before + 200, session.snapshot().score());
        assertEquals(1, session.snapshot().lines());
        assertEquals(List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 10)), session.snapshot().effects());
    }

    @Test
    void finalSlowedBlockKeepsZeroRemainingEffectThroughQuitAndPause() throws Exception {
        IBlock item = new IBlock();
        item.attachItem(1, 0, ItemType.SLOW);
        var clock = new GameTestSupport.RecordingClock();
        GameSession session = new GameSession(GameTestSupport.sequence(item, new IBlock(), new IBlock(),
                new IBlock(), new IBlock(), new IBlock(), new IBlock(), new IBlock(),
                new IBlock(), new IBlock(), new IBlock(), new IBlock()), clock,
                new GameTestSupport.RecordingListener());
        session.start();
        for (int lock = 0; lock < 10; lock++) {
            prepareSingleLine(session);
            session.hardDrop();
            session.tick();
        }
        assertEquals(List.of(new ActiveEffect(ItemType.SLOW, 0)), session.snapshot().effects());
        int slowed = (int) Math.round(LevelPolicy.fallIntervalMillis(session.snapshot().level()) * 1.5);
        assertEquals(slowed, clock.starts.getLast());
        session.requestQuit();
        session.tick();
        session.cancelQuit();
        assertEquals(slowed, clock.starts.getLast());
        session.pause();
        session.resume();
        assertEquals(slowed, clock.starts.getLast());
        assertEquals(List.of(new ActiveEffect(ItemType.SLOW, 0)), session.snapshot().effects());
        prepareSingleLine(session);
        session.hardDrop();
        session.tick();
        assertTrue(session.snapshot().effects().isEmpty());
        assertEquals(LevelPolicy.fallIntervalMillis(session.snapshot().level()), clock.starts.getLast());
    }

    @Test
    void fourLineClearCrossesTenOnceAndPreservesExistingNext() throws Exception {
        int[] requests = {0};
        ItemBlockGenerator generator = new ItemBlockGenerator(IBlock::new, WeightBlock::new, new Random(42)) {
            @Override public void requestItem() { requests[0]++; super.requestItem(); }
        };
        GameSession session = new GameSession(GameMode.ITEM, Difficulty.NORMAL, generator,
                new GameTestSupport.RecordingClock(), new GameTestSupport.RecordingListener());
        session.start();
        Field lines = GameSession.class.getDeclaredField("lines");
        lines.setAccessible(true);
        lines.setInt(session, 9);
        Field policy = GameSession.class.getDeclaredField("levelPolicy");
        policy.setAccessible(true);
        LevelPolicy level = (LevelPolicy) policy.get(session);
        level.onLinesCleared(4);
        level.onLinesCleared(4);
        level.onLinesCleared(1);
        session.rotateRight(); // vertical I occupies column 5
        for (int row = 16; row < 20; row++) fillExcept(board(session), row, 5);
        session.hardDrop();
        assertEquals(GameState.CLEARING, session.getState());
        assertEquals(9, session.snapshot().lines());
        assertEquals(0, requests[0]);
        session.tick();
        assertEquals(13, session.snapshot().lines());
        assertEquals(3, session.snapshot().level());
        assertEquals(7, session.snapshot().nextItemLines());
        assertEquals(1, requests[0]);
        assertEquals(0, session.snapshot().currentBlock().getRotation());
        assertFalse(hasItem(session.snapshot().currentBlock()));
        assertTrue(hasItem(session.snapshot().nextBlock()));
    }

    private static boolean hasItem(Block block) {
        if (block.isWeight()) return true;
        for (int row = 0; row < block.getShape().length; row++)
            for (int col = 0; col < block.getShape()[row].length; col++)
                if (block.itemCellAt(row, col).isPresent()) return true;
        return false;
    }

    @Test
    void snapshotCellsStayIndependentAcrossClearingAndRestart() throws Exception {
        GameSession session = GameTestSupport.startedSession(new IBlock(), new IBlock());
        prepareSingleLine(session);
        session.hardDrop();
        GameSnapshot retained = session.snapshot();
        GameSnapshot mutated = session.snapshot();
        mutated.cells()[19][0] = 99;
        mutated.cells()[0] = new int[Board.WIDTH];
        assertEquals(4, session.snapshot().cells()[19][0]);
        session.tick();
        assertEquals(0, session.snapshot().cells()[19][0]);
        assertEquals(4, retained.cells()[19][0]);
        session.pause();
        session.restart();
        assertEquals(4, retained.cells()[19][0]);
        retained.cells()[0][0] = 99;
        assertEquals(0, session.snapshot().cells()[0][0]);
    }

    @Test
    void seededStateTransitionsFreezeAndResetWithoutLeakingQuitState() {
        Random random = new Random(20261026);
        GameSession session = GameTestSupport.startedSession(new IBlock(), new IBlock());
        for (int round = 0; round < 200; round++) {
            if (session.getState() == GameState.GAME_OVER || session.getState() == GameState.ENDED) session.start();
            session.moveDown();
            boolean pausedFirst = random.nextBoolean();
            if (pausedFirst) session.pause();
            session.requestQuit();
            GameSnapshot frozen = session.snapshot();
            session.tick();
            session.hardDrop();
            session.togglePause();
            session.restart();
            assertFrozen(frozen, session.snapshot());
            if (random.nextInt(4) == 0) {
                session.confirmQuit();
                assertEquals(GameState.ENDED, session.getState());
                GameSnapshot ended = session.snapshot();
                session.tick();
                session.resume();
                session.hardDrop();
                assertFrozen(ended, session.snapshot());
            } else {
                session.cancelQuit();
                assertEquals(pausedFirst ? GameState.PAUSED : GameState.PLAYING, session.getState());
                if (pausedFirst) session.resume();
                session.pause();
                session.restart();
                assertEquals(GameState.PLAYING, session.getState());
                assertEquals(0, session.snapshot().score());
                assertEquals(0, session.snapshot().lines());
                assertEquals(1, session.snapshot().level());
                for (int[] row : session.snapshot().cells()) assertArrayEquals(new int[Board.WIDTH], row);
            }
        }
    }
}
