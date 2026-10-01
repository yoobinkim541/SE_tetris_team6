package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** §3.1 이동/충돌 (좌우 이동, Soft Drop, 벽 충돌 시 입력 무시·상태 불변, 이동 점수 0) */
public class MoveTest {
    private static void repeat(Runnable action, int times) {
        for (int i = 0; i < times; i++) action.run();
    }

    private static String row(GameSession session, int row) {
        return AsciiBoard.row(session.snapshot(), row);
    }

    //#region 좌우 이동
    @Test
    void 좌우로_한_칸씩_움직이고_점수는_0() { // SCR-4
        GameSession session = startedSession(new OBlock(), new TBlock());
        session.moveLeft();
        assertEquals("...oo.....", row(session, 0));
        session.moveRight();
        session.moveRight();
        assertEquals(".....oo...", row(session, 0));
        assertEquals(0, session.snapshot().score());
    }

    @Test
    void 왼쪽_벽에서는_더_움직이지_않는다() {
        GameSession session = startedSession(new IBlock(), new TBlock());
        repeat(session::moveLeft, 3);
        assertEquals("iiii......", row(session, 0));
        session.moveLeft();
        assertEquals("iiii......", row(session, 0));
    }

    @Test
    void 오른쪽_벽에서는_더_움직이지_않는다() {
        GameSession session = startedSession(new IBlock(), new TBlock());
        repeat(session::moveRight, 3);
        assertEquals("......iiii", row(session, 0));
        session.moveRight();
        assertEquals("......iiii", row(session, 0));
    }

    @Test
    void 실패한_이동은_화면_갱신을_알리지_않는다() { // §3.1 상태 불변
        RecordingListener listener = new RecordingListener();
        GameSession session = new GameSession(sequence(new IBlock(), new TBlock()), new RecordingClock(), listener);
        session.start();
        repeat(session::moveLeft, 5); // 성공 3번, 실패 2번
        assertEquals(1 + 3, listener.changed.size());
    }

    @Test
    void 착지한_뒤에도_다음_하강_시도_전까지는_좌우_이동_가능() { // LCK-2
        GameSession session = startedSession(new OBlock(), new TBlock());
        repeat(session::tick, 18);
        session.moveLeft();
        assertEquals("...oo.....", row(session, 19));
    }
    //#endregion

    //#region Soft Drop
    @Test
    void Soft_Drop은_한_칸_내리고_Level만큼_점수() { // SCR-1
        GameSession session = startedSession(new OBlock(), new TBlock());
        session.moveDown();
        GameSnapshot s = session.snapshot();
        assertEquals("....oo....", AsciiBoard.row(s, 1));
        assertEquals("....oo....", AsciiBoard.row(s, 2));
        assertEquals(1, s.score());
    }

    @Test
    void Soft_Drop은_낙하_타이머를_리셋하지_않는다() { // TMR-4
        RecordingClock clock = new RecordingClock();
        GameSession session = new GameSession(sequence(new OBlock(), new TBlock()), clock, new RecordingListener());
        session.start();
        repeat(session::moveDown, 5);
        assertEquals(1, clock.starts.size()); // Spawn 때 1번뿐
    }

    @Test
    void 바닥에서_Soft_Drop하면_점수_없이_고정된다() { // SCR-4, LCK-2 (Soft Drop 입력 즉시 Lock)
        GameSession session = startedSession(new OBlock(), new TBlock());
        repeat(session::moveDown, 18);
        assertEquals(18, session.snapshot().score());
        session.moveDown();
        assertEquals(18, session.snapshot().score());
        assertEquals("....OO....", row(session, 19));
    }
    //#endregion

    @Test
    void PLAYING이_아니면_조작을_무시한다() {
        GameSession session = new GameSession(sequence(new OBlock()), new RecordingClock(), new RecordingListener());
        session.moveLeft();
        session.moveRight();
        session.moveDown();
        session.rotateRight();
        assertEquals(0, session.snapshot().score());
    }
}
