package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tetris.feature.GameTestSupport.sequence;
import static tetris.feature.GameTestSupport.startedSession;

import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
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
    void 바닥에서_하강에_실패하면_점수_없이_고정된다() { // SCR-4, LCK-2
        GameSession session = startedSession(new IBlock(), new TBlock());
        tick(session, 19);   // I 막대: 0행 -> 19행
        assertEquals("...iiii...", AsciiBoard.row(session.snapshot(), 19));
        assertEquals(19, session.snapshot().score());

        session.tick();
        assertEquals("...IIII...", AsciiBoard.row(session.snapshot(), 19));
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

    //#region Lock과 쌓기 (§4 LOCK)
    // 하강 descents번 + 실패하는 하강 시도 1번(이때 Lock)
    private static void dropAndLock(GameSession session, int descents) {
        tick(session, descents + 1);
    }

    @Test
    void 바닥에_닿은_뒤_다음_하강_시도에서_고정되고_다음_블록이_나온다() { // LCK-2, LCK-4
        RecordingClock clock = new RecordingClock();
        GameSession session = new GameSession(sequence(new OBlock(), new TBlock(), new IBlock()), clock, new RecordingListener());
        session.start();
        dropAndLock(session, 18);

        GameSnapshot s = session.snapshot();
        assertEquals("....OO....", AsciiBoard.row(s, 18));   // 고정 칸은 대문자
        assertEquals("....OO....", AsciiBoard.row(s, 19));
        assertEquals("....t.....", AsciiBoard.row(s, 0));    // 다음 블록 Spawn
        assertEquals(2, clock.starts.size());                 // Spawn마다 타이머 재시작 (S4)
    }

    @Test
    void 다음_블록은_고정된_블록_위에_쌓인다() {
        GameSession session = startedSession(new OBlock(), new OBlock(), new TBlock());
        dropAndLock(session, 18);
        dropAndLock(session, 16);

        GameSnapshot s = session.snapshot();
        assertEquals("....OO....", AsciiBoard.row(s, 16));
        assertEquals("....OO....", AsciiBoard.row(s, 17));
        assertEquals("....OO....", AsciiBoard.row(s, 18));
        assertEquals("....OO....", AsciiBoard.row(s, 19));
    }

    @Test
    void 줄이_완성되면_지우고_위를_내리고_보너스() { // CLR-1, CLR-2, SCR-3
        GameSession session = startedSession(new IBlock(), new IBlock(), new OBlock(), new TBlock(), new TBlock());
        for (int i = 0; i < 3; i++) session.moveLeft();   // I: col 0..3
        dropAndLock(session, 19);
        session.moveRight();                               // I: col 4..7
        dropAndLock(session, 19);
        for (int i = 0; i < 4; i++) session.moveRight();  // O: col 8..9
        dropAndLock(session, 18);                          // 19행이 꽉 참

        GameSnapshot s = session.snapshot();
        assertEquals("..........", AsciiBoard.row(s, 18));
        assertEquals("........OO", AsciiBoard.row(s, 19)); // O의 윗줄만 내려옴
        assertEquals(19 + 19 + 18 + 100, s.score());
        assertEquals(1, s.lines());
    }

    @Test
    void Spawn_자리가_막히면_GAME_OVER() { // SPN-4
        Block[] blocks = new Block[12];
        for (int i = 0; i < blocks.length; i++) blocks[i] = new OBlock();
        RecordingClock clock = new RecordingClock();
        RecordingListener listener = new RecordingListener();
        GameSession session = new GameSession(sequence(blocks), clock, listener);
        session.start();

        for (int k = 0; k < 10; k++) dropAndLock(session, 18 - 2 * k); // 가운데에 O 10개를 쌓아 0행까지 채움

        GameSnapshot s = session.snapshot();
        assertEquals(GameState.GAME_OVER, session.getState());
        assertEquals(1, listener.gameOvers.size());
        assertEquals(1, clock.stops);
        assertNull(s.currentBlock());                        // 막힌 블록은 놓지 않는다
        assertEquals("....OO....", AsciiBoard.row(s, 0));    // 보드는 마지막 정상 상태
        assertEquals(90, s.score());                         // 18+16+...+2

        session.tick();                                      // GAME_OVER에서는 무시
        assertEquals(90, session.snapshot().score());
    }
    //#endregion
}
