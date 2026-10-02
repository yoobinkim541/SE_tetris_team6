package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.feature.GameTestSupport.RecordingClock;
import tetris.feature.GameTestSupport.RecordingListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.rule.BlockGenerator;

/** PAU-1~3, RST-1, QIT-1, TMR-3, GameState 전이표 (정지 중 tick 무시, Resume 타이머 재시작, Restart 초기화, Quit 저장 안 함) */
public class GameSessionFlowTest {
    private final RecordingClock clock = new RecordingClock();
    private final RecordingListener listener = new RecordingListener();

    private GameSession newSession() {
        GameSession session = new GameSession(
                GameTestSupport.sequence(new OBlock(), new TBlock(), new IBlock()), clock, listener);
        session.start();
        return session;
    }

    //#region Pause / Resume
    @Test
    void pause하면_PAUSED가_되고_타이머가_멈춘다() { // PAU-1
        GameSession session = newSession();
        session.pause();

        assertEquals(GameState.PAUSED, session.getState());
        assertEquals(1, clock.stops);
    }

    @Test
    void PAUSED에서는_tick_이동_회전_드롭이_모두_무시된다() { // PAU-1
        GameSession session = newSession();
        session.pause();
        GameSnapshot before = session.snapshot();

        session.tick();
        session.moveLeft();
        session.moveRight();
        session.moveDown();
        session.rotateRight();
        session.hardDrop();

        GameSnapshot after = session.snapshot();
        assertEquals(before.score(), after.score());
        assertEquals(before.currentBlock().getRow(), after.currentBlock().getRow());
        assertEquals(before.currentBlock().getCol(), after.currentBlock().getCol());
    }

    @Test
    void resume하면_PLAYING으로_돌아오고_타이머를_전체_간격으로_다시_시작한다() { // TMR-3
        GameSession session = newSession();
        int startsBefore = clock.starts.size();
        session.pause();
        session.resume();

        assertEquals(GameState.PLAYING, session.getState());
        assertEquals(startsBefore + 1, clock.starts.size());
        assertEquals(clock.starts.get(0), clock.starts.get(startsBefore));
    }

    @Test
    void togglePause는_PLAYING과_PAUSED를_오간다() {
        GameSession session = newSession();
        session.togglePause();
        assertEquals(GameState.PAUSED, session.getState());
        session.togglePause();
        assertEquals(GameState.PLAYING, session.getState());
    }

    @Test
    void 창_포커스를_잃으면_자동으로_Pause된다() { // PAU-3
        GameSession session = newSession();
        session.onFocusLost();
        assertEquals(GameState.PAUSED, session.getState());
    }

    @Test
    void 이미_PAUSED이면_포커스_이탈은_아무것도_하지_않는다() {
        GameSession session = newSession();
        session.pause();
        int notifications = listener.changed.size();
        session.onFocusLost();

        assertEquals(notifications, listener.changed.size());
        assertEquals(1, clock.stops);
    }

    @Test
    void PLAYING이_아닐때_pause는_무시된다() {
        GameSession session = new GameSession(GameTestSupport.sequence(new OBlock()), clock, listener);
        session.pause(); // LOADING
        assertEquals(GameState.LOADING, session.getState());
    }
    //#endregion

    //#region Restart
    @Test
    void restart는_PAUSED에서만_동작하고_새_세션으로_초기화한다() { // RST-1
        GameSession session = newSession();
        session.hardDrop(); // 점수와 보드 상태를 만든다
        session.pause();
        session.restart();

        GameSnapshot s = session.snapshot();
        assertEquals(GameState.PLAYING, session.getState());
        assertEquals(0, s.score());
        assertEquals(1, s.level());
        assertEquals(0, s.lines());
        for (int[] row : s.cells()) for (int cell : row) assertEquals(0, cell);
    }

    @Test
    void PLAYING_중에는_restart가_무시된다() { // RST-1: Pause 메뉴에서만
        GameSession session = newSession();
        session.hardDrop();
        long score = session.snapshot().score();
        session.restart();

        assertEquals(score, session.snapshot().score());
    }
    //#endregion

    //#region Quit
    @Test
    void requestQuit은_게임을_멈추고_확인을_기다린다() { // QIT-1
        GameSession session = newSession();
        session.requestQuit();

        assertEquals(GameState.PAUSED, session.getState());
        assertEquals(1, clock.stops);
    }

    @Test
    void Quit_확인창_동안에는_resume이_무시된다() {
        GameSession session = newSession();
        session.requestQuit();
        session.resume();
        session.togglePause();

        assertEquals(GameState.PAUSED, session.getState());
    }

    @Test
    void Quit_확인창_동안에는_restart가_무시된다() {
        GameSession session = newSession();
        session.hardDrop();
        long score = session.snapshot().score();
        session.requestQuit();
        session.restart();

        assertEquals(score, session.snapshot().score());
        assertEquals(GameState.PAUSED, session.getState());
    }

    @Test
    void Quit_Yes면_ENDED가_되고_타이머가_멈춘다() { // QIT-1
        GameSession session = newSession();
        session.requestQuit();
        session.confirmQuit();

        assertEquals(GameState.ENDED, session.getState());
        assertTrue(clock.stops >= 1);
    }

    @Test
    void Quit_No면_PLAYING이었을_때_PLAYING으로_복귀하고_타이머를_재시작한다() { // QIT-1, TMR-3
        GameSession session = newSession();
        int startsBefore = clock.starts.size();
        session.requestQuit();
        session.cancelQuit();

        assertEquals(GameState.PLAYING, session.getState());
        assertEquals(startsBefore + 1, clock.starts.size());
    }

    @Test
    void Quit_No면_PAUSED였을_때_PAUSED로_남는다() { // QIT-1
        GameSession session = newSession();
        session.pause();
        int startsBefore = clock.starts.size();
        session.requestQuit();
        session.cancelQuit();

        assertEquals(GameState.PAUSED, session.getState());
        assertEquals(startsBefore, clock.starts.size());
    }

    @Test
    void PAUSED에서도_Quit_Yes면_ENDED가_된다() { // QIT-1
        GameSession session = newSession();
        session.pause();
        session.requestQuit();
        session.confirmQuit();

        assertEquals(GameState.ENDED, session.getState());
    }

    @Test
    void 확인창이_없을_때_confirmQuit과_cancelQuit은_무시된다() {
        GameSession session = newSession();
        session.confirmQuit();
        session.cancelQuit();

        assertEquals(GameState.PLAYING, session.getState());
    }

    @Test
    void ENDED_상태에서는_requestQuit이_무시된다() {
        GameSession session = newSession();
        session.end();
        session.requestQuit();
        session.confirmQuit();

        assertEquals(GameState.ENDED, session.getState());
    }
    //#endregion

    //#region end
    @Test
    void end는_ENDED로_전이하고_이후_조작을_무시한다() {
        GameSession session = newSession();
        session.end();
        session.tick();
        session.hardDrop();
        session.pause();

        assertEquals(GameState.ENDED, session.getState());
    }
    //#endregion

    //#region 생성자·보호 조건
    @Test
    void 생성자에_null을_넘기면_예외() {
        BlockGenerator generator = GameTestSupport.sequence(new OBlock());

        assertThrows(IllegalArgumentException.class, () -> new GameSession(null, clock, listener));
        assertThrows(IllegalArgumentException.class, () -> new GameSession(generator, null, listener));
        assertThrows(IllegalArgumentException.class, () -> new GameSession(generator, clock, null));
    }

    @Test
    void end를_두_번_불러도_한_번만_처리한다() {
        GameSession session = newSession();
        session.end();
        int stops = clock.stops;
        session.end();

        assertEquals(stops, clock.stops);
        assertEquals(GameState.ENDED, session.getState());
    }

    @Test
    void 확인창이_이미_떠_있으면_requestQuit을_다시_불러도_무시된다() {
        GameSession session = newSession();
        session.requestQuit();
        int stops = clock.stops;
        session.requestQuit();
        session.cancelQuit(); // 처음 requestQuit 기준으로 한 번만 복귀

        assertEquals(stops, clock.stops);
        assertEquals(GameState.PLAYING, session.getState());
    }

    @Test
    void 시작_전_LOADING_상태에서는_requestQuit이_무시된다() {
        GameSession session = new GameSession(GameTestSupport.sequence(new OBlock()), clock, listener);
        session.requestQuit();
        session.confirmQuit();

        assertEquals(GameState.LOADING, session.getState());
    }
    //#endregion
}
