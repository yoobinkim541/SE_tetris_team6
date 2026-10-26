package tetris.feature;

import static org.junit.jupiter.api.Assertions.*;
import static tetris.feature.GameTestSupport.sequence;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.feature.data.Board;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameState;
import tetris.feature.item.Cell;
import tetris.feature.item.ItemType;

class ItemEffectHookTest {
    @Test
    void lineItemClearsIncompleteRowAfterAnimationAndScoresOnce() {
        IBlock item = new IBlock();
        item.attachItem(1, 0, ItemType.LINE_CLEAR);
        GameTestSupport.RecordingClock clock = new GameTestSupport.RecordingClock();
        GameSession session = new GameSession(sequence(item, new OBlock()), clock,
                new GameTestSupport.RecordingListener());
        session.start();
        session.hardDrop();
        long dropScore = session.snapshot().score();
        assertEquals(GameState.CLEARING, session.getState());
        assertEquals(150, clock.starts.getLast());
        assertEquals(10, session.snapshot().clearingCells().size());
        assertEquals(0, session.snapshot().lines());
        session.tick();
        assertEquals(1, session.snapshot().lines());
        assertEquals(dropScore + 100, session.snapshot().score());
        for (int cell : session.snapshot().cells()[19]) assertEquals(0, cell);
        session.tick();
        assertEquals(1, session.snapshot().lines());
    }

    @Test
    void fullRowAndLineItemAreCountedOnce() {
        int[][] shape = new int[10][10];
        java.util.Arrays.fill(shape[0], 1);
        Block row = new Block(shape) {
            @Override public tetris.component.block.BlockType getType() {
                return tetris.component.block.BlockType.I;
            }
        };
        row.attachItem(0, 4, ItemType.LINE_CLEAR);
        GameSession session = GameTestSupport.startedSession(row, new OBlock());
        session.hardDrop();
        long before = session.snapshot().score();
        session.tick();
        assertEquals(1, session.snapshot().lines());
        assertEquals(before + 100, session.snapshot().score());
    }

    @Test
    void arbitraryRowsCompactIncludingHiddenRowsAndCellsDoNotCompact() {
        Board board = new Board();
        Block cell = GameTestSupport.cell();
        cell.setPosition(-1, 0);
        assertTrue(board.tryPlaceBlock(cell));
        cell.setPosition(18, 1);
        assertTrue(board.tryPlaceBlock(cell));
        cell.setPosition(19, 2);
        assertTrue(board.tryPlaceBlock(cell));
        assertEquals(1, board.clearCells(Set.of(new Cell(19, 2), new Cell(20, 0))));
        assertFalse(board.isEmpty(18, 1));
        board.removeRows(Set.of(19, -2, 99));
        assertFalse(board.isEmpty(19, 1));
        assertFalse(board.isEmpty(0, 0));
        assertTrue(board.isEmpty(-1, 0));
    }
}

