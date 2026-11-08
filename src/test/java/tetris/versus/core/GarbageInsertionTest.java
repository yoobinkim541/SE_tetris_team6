package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.component.block.*;
import tetris.feature.data.Board;
import tetris.feature.game.*;
import tetris.feature.item.*;
import tetris.feature.versus.api.*;

class GarbageInsertionTest {
    static Block cell(int row, int col) {
        Block b = new Block(new int[][]{{1}}) {
            @Override public BlockType getType() { return BlockType.O; }
        };
        b.setPosition(row, col); return b;
    }
    static AttackRows rows() {
        return new AttackRows(List.of(new int[]{0,9,9,9,9,9,9,9,9,9}, new int[]{9,0,9,9,9,9,9,9,9,9}));
    }
    static Board board(GameSession s) throws Exception {
        Field f=GameSession.class.getDeclaredField("board");f.setAccessible(true);return (Board)f.get(s);
    }
    static GameSession session() {
        return new GameSession(GameMode.CLASSIC,Difficulty.NORMAL,OBlock::new,new GameClock(){
            public void start(int ms){} public void stop(){}
        },new GameListener(){ public void onChanged(GameSnapshot s){} public void onGameOver(GameSnapshot s){} },
        new SpawnBoundary(){public void onLockResolved(LockResult r){} public boolean deferSpawn(){return true;}});
    }
    @Test void rejectsOversizedInsertionBeforeChangingBoard() {
        Board b=new Board();assertTrue(b.tryPlaceBlock(cell(19,0)));
        var oversized=new AttackRows(java.util.Collections.nCopies(11,new int[]{0,9,9,9,9,9,9,9,9,9}));
        assertThrows(IllegalArgumentException.class,()->b.insertAttackRows(oversized));
        assertEquals(4,b.copyCells()[19][0]);
    }
    @Test void shiftsCellsAndItemsPreservesRowOrderAndWalls() {
        Board b=new Board(); Block marked=cell(17,2); marked.attachItem(0,0,ItemType.DOUBLE_SCORE);
        assertTrue(b.tryPlaceBlock(marked));
        assertFalse(b.insertAttackRows(rows()));
        assertEquals(4,b.copyCells()[15][2]);
        assertEquals(ItemType.DOUBLE_SCORE,b.copyItemCells().get(new Cell(15,2)));
        assertArrayEquals(new int[]{0,9,9,9,9,9,9,9,9,9},b.copyCells()[18]);
        assertArrayEquals(new int[]{9,0,9,9,9,9,9,9,9,9},b.copyCells()[19]);
        assertFalse(b.canPlace(new int[][]{{1}},20,0));
        assertFalse(b.canPlace(new int[][]{{1}},0,-1));
        assertTrue(b.copyItemCells().keySet().stream().noneMatch(c->c.row()>=18));
    }
    @Test void visibleAndHiddenFixedCellsAboveVisibleBoardCountAsOverflow() {
        for(int row:new int[]{-2,-1,0,1}) {
            Board b=new Board();assertTrue(b.tryPlaceBlock(cell(row,0)));
            assertTrue(b.insertAttackRows(rows()),"row="+row);
        }
        Board b=new Board();assertTrue(b.tryPlaceBlock(cell(2,0)));
        assertFalse(b.insertAttackRows(rows()));assertFalse(b.isEmpty(0,0));
    }
    @Test void attackOnlyAtBoundaryAndOnlyOnceIncludingNoClearLock() throws Exception {
        GameSession s=session();s.start();
        assertThrows(IllegalStateException.class,()->s.applyPendingAttack(rows()));
        s.hardDrop();assertTrue(s.isSpawnPending());assertNull(s.snapshot().currentBlock());
        s.applyPendingAttack(rows());s.resumeSpawn();
        assertNotNull(s.snapshot().currentBlock());assertEquals(9,s.snapshot().cells()[19][0]);
        assertThrows(IllegalStateException.class,()->s.applyPendingAttack(rows()));
        s.resumeSpawn();assertEquals(9,s.snapshot().cells()[19][0]);
    }
    @Test void secondInsertionAtSameBoundaryIsRejectedWithoutChangingBoard() {
        var s=session();s.start();s.hardDrop();s.applyPendingAttack(rows());
        var before=s.snapshot().cells();
        assertThrows(IllegalStateException.class,()->s.applyPendingAttack(rows()));
        assertArrayEquals(before,s.snapshot().cells());
        s.resumeSpawn();s.hardDrop();assertDoesNotThrow(()->s.applyPendingAttack(rows()));
    }
    @Test void insertingGarbageRemovesBottomItemMarks() {
        var b=new Board();var marked=cell(19,2);marked.attachItem(0,0,ItemType.DOUBLE_SCORE);
        assertTrue(b.tryPlaceBlock(marked));b.insertAttackRows(rows());
        assertEquals(ItemType.DOUBLE_SCORE,b.copyItemCells().get(new Cell(17,2)));
        assertFalse(b.copyItemCells().containsKey(new Cell(19,2)));
    }
    @Test void clearingMustSettleBeforeInsertionAndOverflowEndsSession() throws Exception {
        GameSession s=session();s.start();
        for(int r:new int[]{18,19})for(int c=0;c<10;c++)if(c!=4&&c!=5)assertTrue(board(s).tryPlaceBlock(cell(r,c)));
        s.hardDrop();assertEquals(GameState.CLEARING,s.getState());
        assertThrows(IllegalStateException.class,()->s.applyPendingAttack(rows()));
        s.tick();assertTrue(s.isSpawnPending());
        assertTrue(board(s).tryPlaceBlock(cell(0,0)));s.applyPendingAttack(rows());
        assertEquals(GameState.GAME_OVER,s.getState());assertFalse(s.isSpawnPending());
        s.resumeSpawn();assertNull(s.snapshot().currentBlock());
    }
}
