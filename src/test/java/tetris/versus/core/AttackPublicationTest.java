package tetris.versus.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import tetris.component.block.*;
import tetris.feature.data.Board;
import tetris.feature.game.*;
import tetris.feature.item.ItemType;
import tetris.feature.versus.api.*;
import tetris.feature.versus.PendingAttackQueue;

class AttackPublicationTest {
    private static class Clock implements GameClock {
        boolean running;
        @Override public void start(int interval) { running=true; }
        @Override public void stop() { running=false; }
    }
    private static class Boundary implements SpawnBoundary {
        final List<LockResult> results=new ArrayList<>();
        final boolean defer;
        Boundary(boolean defer) { this.defer=defer; }
        @Override public void onLockResolved(LockResult result) { results.add(result); }
        @Override public boolean deferSpawn() { return defer; }
    }
    private GameSession session(Boundary boundary,Clock clock,ItemType item) {
        return new GameSession(item == null ? GameMode.CLASSIC : GameMode.ITEM,Difficulty.NORMAL,() -> {
            var block=new OBlock(); if(item!=null) block.attachItem(0,0,item); return block;
        },clock,new GameListener() {
            public void onChanged(GameSnapshot snapshot) { }
            public void onGameOver(GameSnapshot snapshot) { }
        },boundary);
    }
    private Board board(GameSession session) throws Exception {
        var field=GameSession.class.getDeclaredField("board"); field.setAccessible(true); return (Board)field.get(session);
    }
    private void fillExcept(Board board,int row,int... holes) {
        for(int col=0;col<10;col++) {
            boolean hole=false; for(int h:holes) hole|=h==col; if(hole) continue;
            Block cell=new Block(new int[][]{{1}}) { @Override public BlockType getType() { return BlockType.J; } };
            cell.setPosition(row,col); assertTrue(board.tryPlaceBlock(cell));
        }
    }
    @Test void publishesOnceAfterClearingIncludingPauseAndKeepsPreDeletionHoles() throws Exception {
        var boundary=new Boundary(false); var clock=new Clock(); var session=session(boundary,clock,null); session.start();
        fillExcept(board(session),18,4,5); fillExcept(board(session),19,4,5);
        session.hardDrop(); assertEquals(GameState.CLEARING,session.getState()); assertTrue(boundary.results.isEmpty());
        session.pause(); session.tick(); session.resume(); assertTrue(boundary.results.isEmpty());
        session.tick(); assertEquals(1,boundary.results.size());
        var result=boundary.results.getFirst(); assertEquals(2,result.clearedRows()); assertEquals(2,result.attack().size());
        for(int[] row:result.attack().rows()) for(int c=0;c<10;c++) assertEquals(c==4||c==5?0:9,row[c]);
        session.tick(); assertEquals(1,boundary.results.size()); assertEquals(2,session.snapshot().lines());
    }
    @Test void lItemAddsIncompleteRowAndDeduplicatesNaturalRow() throws Exception {
        var boundary=new Boundary(false); var session=session(boundary,new Clock(),ItemType.LINE_CLEAR); session.start();
        fillExcept(board(session),18,0,4,5); fillExcept(board(session),19,4,5);
        session.hardDrop(); session.tick();
        var result=boundary.results.getFirst(); assertEquals(2,result.clearedRows()); assertEquals(2,result.attack().size());
        assertEquals(0,result.attack().rows().getFirst()[0]);
        assertEquals(9,result.attack().rows().getLast()[0]);
        var duplicate=new Boundary(false); var duplicateSession=session(duplicate,new Clock(),ItemType.LINE_CLEAR); duplicateSession.start();
        fillExcept(board(duplicateSession),18,4,5); fillExcept(board(duplicateSession),19,4,5);
        duplicateSession.hardDrop(); duplicateSession.tick(); assertEquals(2,duplicate.results.getFirst().clearedRows());
    }
    @Test void singleLineAndBombCellDeletionDoNotCreateAttackRows() throws Exception {
        for(ItemType item:new ItemType[]{null,ItemType.BOMB}) {
            var boundary=new Boundary(false); var session=session(boundary,new Clock(),item); session.start();
            if(item==null) fillExcept(board(session),19,4,5);
            session.hardDrop(); if(session.getState()==GameState.CLEARING) session.tick();
            assertEquals(1,boundary.results.size()); assertTrue(boundary.results.getFirst().attack().isEmpty());
        }
    }
    @Test void deferredSpawnStopsClockIgnoresInputAndResumesOnlyOnce() {
        var boundary=new Boundary(true); var clock=new Clock(); var session=session(boundary,clock,null); session.start();
        session.hardDrop(); assertEquals(1,boundary.results.size()); assertTrue(session.isSpawnPending());
        assertNull(session.snapshot().currentBlock()); assertFalse(clock.running);
        assertDoesNotThrow(() -> { session.moveLeft(); session.moveRight(); session.moveDown(); session.rotateRight(); session.hardDrop(); session.tick(); });
        assertEquals(1,boundary.results.size());
        session.pause(); session.resume(); assertTrue(session.isSpawnPending()); assertFalse(clock.running);
        session.resumeSpawn(); assertFalse(session.isSpawnPending()); assertNotNull(session.snapshot().currentBlock()); assertTrue(clock.running);
        var before=session.snapshot(); session.resumeSpawn(); assertEquals(before.currentBlock().getRow(),session.snapshot().currentBlock().getRow());
        session.hardDrop(); session.end(); assertFalse(session.isSpawnPending()); session.resumeSpawn(); assertNull(session.snapshot().currentBlock());
    }
    @Test void endDuringClearingDiscardsUnpublishedAttack() throws Exception {
        var boundary=new Boundary(false); var session=session(boundary,new Clock(),null); session.start();
        fillExcept(board(session),18,4,5); fillExcept(board(session),19,4,5);
        session.hardDrop(); session.end(); session.tick(); assertTrue(boundary.results.isEmpty());
        session.start(); session.hardDrop(); assertEquals(1,boundary.results.size()); assertTrue(boundary.results.getFirst().attack().isEmpty());
    }
    @Test void queueReceivesAttackOnlyAtTheSettledLockBoundary() throws Exception {
        var queue=new PendingAttackQueue();
        var boundary=new Boundary(true) {
            @Override public void onLockResolved(LockResult result) { super.onLockResolved(result); queue.append(result.attack()); }
        };
        var session=session(boundary,new Clock(),null); session.start();
        fillExcept(board(session),18,4,5); fillExcept(board(session),19,4,5);
        session.hardDrop(); assertEquals(0,queue.size()); session.tick();
        assertEquals(2,queue.size()); assertTrue(session.isSpawnPending());
        assertEquals(2,queue.drain().size()); assertEquals(0,queue.size());
    }
    @Test void weightCrushDoesNotCountClearedCellsAsDeletedRows() throws Exception {
        var boundary=new Boundary(false);
        var session=new GameSession(GameMode.ITEM,Difficulty.NORMAL,WeightBlock::new,new Clock(),new GameListener() {
            public void onChanged(GameSnapshot snapshot) { }
            public void onGameOver(GameSnapshot snapshot) { }
        },boundary);
        session.start(); for(int row=17;row<20;row++) fillExcept(board(session),row,0);
        session.hardDrop();
        assertEquals(1,boundary.results.size()); assertEquals(0,boundary.results.getFirst().clearedRows());
        assertTrue(boundary.results.getFirst().attack().isEmpty());
    }
    @Test void inlineResumeAndEndCallbacksDoNotSpawnTwiceOrPublishTwice() {
        for(boolean end:new boolean[]{false,true}) {
            var holder=new GameSession[1];
            var boundary=new Boundary(true) {
                @Override public void onLockResolved(LockResult result) {
                    super.onLockResolved(result);
                    if(end) holder[0].end(); else holder[0].resumeSpawn();
                }
            };
            holder[0]=session(boundary,new Clock(),null); holder[0].start(); holder[0].hardDrop();
            assertEquals(1,boundary.results.size()); assertFalse(holder[0].isSpawnPending());
            if(end) assertEquals(GameState.ENDED,holder[0].getState());
            else assertNotNull(holder[0].snapshot().currentBlock());
        }
    }
}
