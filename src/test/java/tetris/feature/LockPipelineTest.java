package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tetris.feature.GameTestSupport.sequence;
import static tetris.feature.GameTestSupport.startedSession;

import org.junit.jupiter.api.Test;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.debug.AsciiBoard;
import tetris.feature.GameTestSupport.RecordingClock;
import tetris.feature.GameTestSupport.RecordingListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.rule.ImmediateLockPolicy;

/** LCK-2, CLR-1~3, 명세 4.1 시나리오 (Lock, 줄 삭제, Level +2 사이클, Hard Drop d=0, Game Over) */
public class LockPipelineTest {
    private static void tick(GameSession session, int times) {
        for (int i = 0; i < times; i++) session.tick();
    }

    //#region 하강 시도 (§3.1 Tick)
    @Test
    void tick_한_번이면_한_칸_내려가고_Level만큼_점수() { // SCR-1
        GameSession session = startedSession(new OBlock(), new TBlock());
        session.tick();

        GameSnapshot s = session.snapshot();
        assertEquals("..........", AsciiBoard.row(s, 0));
        assertEquals("....oo....", AsciiBoard.row(s, 1));
        assertEquals("....oo....", AsciiBoard.row(s, 2));
        assertEquals(1, s.score());
    }

    @Test
    void O는_18번_내려가_바닥에_닿는다() {
        GameSession session = startedSession(new OBlock(), new TBlock());
        tick(session, 18);

        GameSnapshot s = session.snapshot();
        assertEquals("..........", AsciiBoard.row(s, 17));
        assertEquals("....oo....", AsciiBoard.row(s, 18));
        assertEquals("....oo....", AsciiBoard.row(s, 19));
        assertEquals(18, s.score());
    }

    @Test
    void 바닥에_닿은_뒤의_하강_시도는_실패하고_점수가_늘지_않는다() { // SCR-4
        GameSession session = startedSession(new IBlock(), new TBlock());
        tick(session, 19);   // I 막대: 0행 -> 19행
        assertEquals("...iiii...", AsciiBoard.row(session.snapshot(), 19));
        assertEquals(19, session.snapshot().score());

        tick(session, 3);
        assertEquals("...iiii...", AsciiBoard.row(session.snapshot(), 19));
        assertEquals(19, session.snapshot().score());
    }

    @Test
    void 하강할_때마다_화면_갱신을_알린다() {
        RecordingListener listener = new RecordingListener();
        GameSession session = new GameSession(sequence(new OBlock(), new TBlock()), new RecordingClock(), listener);
        session.start();
        tick(session, 3);
        assertEquals(1 + 3, listener.changed.size()); // start 1번 + tick 3번
    }

    @Test
    void PLAYING이_아니면_tick을_무시한다() { // TMR-2
        GameSession session = new GameSession(sequence(new OBlock()), new RecordingClock(), new RecordingListener());
        session.tick(); // start 전 (LOADING)
        assertEquals(GameState.LOADING, session.getState());
        assertEquals(0, session.snapshot().score());
    }

    @Test
    void 하강에_실패하면_즉시_Lock() { // LCK-2
        ImmediateLockPolicy policy = new ImmediateLockPolicy();
        assertTrue(policy.shouldLock(true));
        assertFalse(policy.shouldLock(false));
    }
    //#endregion
}
