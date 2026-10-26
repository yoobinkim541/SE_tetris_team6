package tetris.feature;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.item.ItemType;

/** S5-A5: 아이템 표시 위치는 현재·Next 블록 사본의 itemCellAt으로 전달한다. */
class ItemSnapshotTest {
    @Test
    void currentAndNextCarryItemsAndRotationKeepsOldSnapshotIndependent() {
        IBlock current = new IBlock();
        current.attachItem(1, 0, ItemType.BOMB);
        OBlock next = new OBlock();
        next.attachItem(0, 0, ItemType.SLOW);
        GameSession session = GameTestSupport.startedSession(current, next);
        GameSnapshot before = session.snapshot();
        assertEquals(ItemType.BOMB, before.currentBlock().itemCellAt(1, 0).orElseThrow());
        assertEquals(ItemType.SLOW, before.nextBlock().itemCellAt(0, 0).orElseThrow());
        session.rotateRight();
        assertEquals(ItemType.BOMB, session.snapshot().currentBlock().itemCellAt(0, 2).orElseThrow());
        assertEquals(ItemType.BOMB, before.currentBlock().itemCellAt(1, 0).orElseThrow());
    }

    @Test
    void rotatingSnapshotCopiesCannotChangeSessionCurrentOrNext() {
        IBlock block = new IBlock();
        block.attachItem(1, 0, ItemType.DOUBLE_SCORE);
        GameSession session = GameTestSupport.startedSession(block, block.copy());
        GameSnapshot snapshot = session.snapshot();
        snapshot.currentBlock().rotateClockwise();
        snapshot.nextBlock().rotateClockwise();
        assertEquals(ItemType.DOUBLE_SCORE, session.snapshot().currentBlock().itemCellAt(1, 0).orElseThrow());
        assertEquals(ItemType.DOUBLE_SCORE, session.snapshot().nextBlock().itemCellAt(1, 0).orElseThrow());
    }

    @Test
    void ordinaryBlocksHaveNoItemMarkers() {
        GameSnapshot snapshot = GameTestSupport.startedSession(new IBlock(), new OBlock()).snapshot();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                assertTrue(snapshot.currentBlock().itemCellAt(r, c).isEmpty());
                assertTrue(snapshot.nextBlock().itemCellAt(r, c).isEmpty());
            }
        }
    }
}
