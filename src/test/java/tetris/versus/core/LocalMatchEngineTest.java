package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import tetris.component.block.OBlock;
import tetris.feature.game.*;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.*;

class LocalMatchEngineTest {
    long now, sequence;
    LocalMatchEngine engine() {
        var e=new LocalMatchEngine(()->now, id->OBlock::new);e.start(MatchConfig.of(BattleMode.CLASSIC));return e;
    }
    void press(LocalMatchEngine e,PlayerId p,GameAction a){e.submit(new PlayerCommand(p,a,true,sequence++));}
    void release(LocalMatchEngine e,PlayerId p,GameAction a){e.submit(new PlayerCommand(p,a,false,sequence++));}
    static GameSession session(LocalMatchEngine e,PlayerId p)throws Exception {
        Field f=LocalMatchEngine.class.getDeclaredField("sessions");f.setAccessible(true);
        return ((GameSession[])f.get(e))[p.ordinal()];
    }
    static int firstCol(int[][] cells){for(int[] row:cells)for(int c=0;c<10;c++)if(row[c]!=0)return c;return -1;}
    @Test void independentInputsAndDueClocksDoNotFallEveryPulse() {
        var e=engine();press(e,PlayerId.ONE,GameAction.MOVE_LEFT);press(e,PlayerId.TWO,GameAction.MOVE_RIGHT);e.advance(0);
        assertEquals(3,firstCol(e.snapshot().one().cells()));assertEquals(5,firstCol(e.snapshot().two().cells()));
        long initial=e.snapshot().one().score();e.advance(999);assertEquals(initial,e.snapshot().one().score());
        e.advance(1000);assertEquals(initial+1,e.snapshot().one().score());
        e.advance(1001);assertEquals(initial+1,e.snapshot().one().score());
        e.advance(3000);assertEquals(initial+3,e.snapshot().one().score());
    }
    @Test void inputAfterLockWaitsForNextPulseAndRepeatedHardDropNeedsRelease() throws Exception {
        var e=engine();press(e,PlayerId.ONE,GameAction.HARD_DROP);release(e,PlayerId.ONE,GameAction.HARD_DROP);
        press(e,PlayerId.ONE,GameAction.MOVE_LEFT);e.advance(0);
        assertEquals(4,session(e,PlayerId.ONE).snapshot().currentBlock().getCol());
        e.advance(1);assertEquals(3,session(e,PlayerId.ONE).snapshot().currentBlock().getCol());
        press(e,PlayerId.ONE,GameAction.HARD_DROP);press(e,PlayerId.ONE,GameAction.HARD_DROP);e.advance(2);
        long score=e.snapshot().one().score();e.advance(3);assertEquals(score,e.snapshot().one().score());
    }
    @Test void duplicateHardDropBeforeReleaseCannotLeakIntoNextPulse() {
        var e=engine();press(e,PlayerId.ONE,GameAction.HARD_DROP);press(e,PlayerId.ONE,GameAction.HARD_DROP);
        release(e,PlayerId.ONE,GameAction.HARD_DROP);e.advance(0);
        long score=e.snapshot().one().score();e.advance(1);assertEquals(score,e.snapshot().one().score());
    }
    @Test void realDoubleClearQueuesAttackUntilReceiverLockAndConsumesOnce() throws Exception {
        var e=engine();var one=session(e,PlayerId.ONE);var two=session(e,PlayerId.TWO);
        for(int r:new int[]{18,19})for(int c=0;c<10;c++)if(c!=4&&c!=5)
            assertTrue(GarbageInsertionTest.board(one).tryPlaceBlock(GarbageInsertionTest.cell(r,c)));
        press(e,PlayerId.ONE,GameAction.HARD_DROP);e.advance(0);
        assertTrue(e.snapshot().two().pendingRows().isEmpty());e.advance(149);
        assertTrue(e.snapshot().two().pendingRows().isEmpty());e.advance(150);
        assertEquals(2,e.snapshot().two().pendingRows().size());assertEquals(0,two.snapshot().cells()[19][0]);
        press(e,PlayerId.TWO,GameAction.HARD_DROP);e.advance(151);
        assertTrue(e.snapshot().two().pendingRows().isEmpty());assertEquals(9,two.snapshot().cells()[19][0]);
        assertEquals(0,two.snapshot().cells()[19][4]);assertEquals(4,two.snapshot().cells()[17][4]);
        release(e,PlayerId.TWO,GameAction.HARD_DROP);press(e,PlayerId.TWO,GameAction.HARD_DROP);e.advance(152);
        assertEquals(9,two.snapshot().cells()[19][0]);assertEquals(0,two.snapshot().cells()[17][0]);
    }
    @Test void bothPlayersTopOutInSamePulseDrawRegardlessOfInputOrder() {
        for(boolean reverse:new boolean[]{false,true}) {
            var e=engine();
            for(int i=0;i<20&&e.snapshot().phase()!=MatchPhase.FINISHED;i++) {
                for(PlayerId p:reverse?new PlayerId[]{PlayerId.TWO,PlayerId.ONE}:PlayerId.values()) {
                    release(e,p,GameAction.HARD_DROP);press(e,p,GameAction.HARD_DROP);
                }
                e.advance(i);
            }
            assertEquals(MatchPhase.FINISHED,e.snapshot().phase());assertTrue(e.snapshot().result().isDraw());
            assertEquals(MatchResult.EndReason.TOPPED_OUT,e.snapshot().result().reason());
            var finalState=e.snapshot();press(e,PlayerId.ONE,GameAction.MOVE_LEFT);e.advance(100000);
            assertEquals(finalState,e.snapshot());e.close();
        }
    }
    @Test void oneTopOutHasOtherWinnerAndStopsBothClocks() throws Exception {
        var e=engine();for(int i=0;i<20&&e.snapshot().phase()!=MatchPhase.FINISHED;i++){
            long before=e.snapshot().revision();
            release(e,PlayerId.ONE,GameAction.HARD_DROP);press(e,PlayerId.ONE,GameAction.HARD_DROP);e.advance(i);
            if(e.snapshot().phase()==MatchPhase.FINISHED)assertEquals(before+1,e.snapshot().revision());
        }
        assertEquals(PlayerId.TWO,e.snapshot().result().winner());
        long score=e.snapshot().two().score();e.advance(100000);assertEquals(score,e.snapshot().two().score());
        Field f=LocalMatchEngine.class.getDeclaredField("clocks");f.setAccessible(true);
        for(var c:(tetris.feature.versus.DueTimeClock[])f.get(e))assertFalse(c.isRunning());
    }
    @Test void pauseStopsBothAndResumeStartsFullIntervalDuplicateSequencesIgnored() {
        var e=engine();e.submit(new PlayerCommand(PlayerId.ONE,GameAction.MOVE_LEFT,true,10));
        e.submit(new PlayerCommand(PlayerId.ONE,GameAction.MOVE_LEFT,true,10));e.advance(0);
        assertEquals(3,firstCol(e.snapshot().one().cells()));sequence=11;
        press(e,PlayerId.ONE,GameAction.PAUSE);e.advance(500);assertEquals(MatchPhase.PAUSED,e.snapshot().phase());
        e.advance(5000);assertEquals(0,e.snapshot().two().score());
        release(e,PlayerId.ONE,GameAction.PAUSE);press(e,PlayerId.ONE,GameAction.PAUSE);e.advance(5000);
        e.advance(5999);assertEquals(0,e.snapshot().two().score());e.advance(6000);assertEquals(1,e.snapshot().two().score());
    }
    @Test void quitForfeitsAndRestartUsesNewMatchAndClearsHeldInputs() {
        var e=engine();String id=e.snapshot().matchId();press(e,PlayerId.TWO,GameAction.QUIT);e.advance(0);
        assertEquals(PlayerId.ONE,e.snapshot().result().winner());assertEquals(MatchResult.EndReason.FORFEIT,e.snapshot().result().reason());
        e.start(MatchConfig.of(BattleMode.CLASSIC));assertNotEquals(id,e.snapshot().matchId());
        assertEquals(MatchPhase.PLAYING,e.snapshot().phase());assertEquals(0,e.snapshot().one().score());
        e.submit(new PlayerCommand(PlayerId.ONE,GameAction.HARD_DROP,true,0));e.advance(0);
        assertTrue(e.snapshot().one().score()>0,"fresh match accepts a reset sequence and fresh press");
        e.submit(new PlayerCommand(PlayerId.TWO,GameAction.QUIT,true,0));e.advance(0);
        assertEquals(MatchPhase.FINISHED,e.snapshot().phase(),"previous Quit latch must not survive restart");
        e.close();e.close();assertThrows(IllegalStateException.class,()->e.start(MatchConfig.of(BattleMode.CLASSIC)));
    }
    @Test void allThreeBattleModesStart() {
        for(BattleMode mode:BattleMode.values()) {
            var e=new LocalMatchEngine(()->0,id->OBlock::new);e.start(MatchConfig.of(mode));
            assertEquals(MatchPhase.PLAYING,e.snapshot().phase());e.close();
        }
    }
}
