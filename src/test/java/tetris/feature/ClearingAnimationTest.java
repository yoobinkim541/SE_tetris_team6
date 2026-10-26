package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tetris.feature.GameTestSupport.sequence;

import org.junit.jupiter.api.BeforeEach;
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

/** ANI-1~4, 명세 4.1: Lock 뒤 줄 삭제는 150 ms CLEARING을 거쳐 tick()에서 마무리된다 */
class ClearingAnimationTest {
    private static final int CLEAR_MILLIS = 150;

    private RecordingClock clock;
    private RecordingListener listener;
    private GameSession session;

    @BeforeEach
    void setUp() {
        clock = new RecordingClock();
        listener = new RecordingListener();
        session = new GameSession(sequence(new IBlock(), new IBlock(), new OBlock(), new TBlock(), new TBlock()), clock, listener);
        session.start();
    }

    private void tick(int times) {
        for (int i = 0; i < times; i++) session.tick();
    }

    /** I 두 개와 O로 19행을 채워 CLEARING에 들어간 상태까지 진행한다 */
    private void lockCompletingLine() {
        for (int i = 0; i < 3; i++) session.moveLeft();   // I: col 0..3
        tick(20);
        session.moveRight();                               // I: col 4..7
        tick(20);
        for (int i = 0; i < 4; i++) session.moveRight();  // O: col 8..9
        tick(19);
    }

    @Test
    void 줄이_완성되면_바로_지우지_않고_CLEARING에_들어간다() { // ANI-2, ANI-3
        lockCompletingLine();

        GameSnapshot s = session.snapshot();
        assertEquals(GameState.CLEARING, session.getState());
        assertEquals("IIIIIIIIOO", AsciiBoard.row(s, 19));     // 아직 지우기 전
        assertEquals(19 + 19 + 18, s.score());                  // 줄 보너스는 아직
        assertEquals(0, s.lines());
        assertEquals(CLEAR_MILLIS, clock.starts.get(clock.starts.size() - 1));
        assertEquals(GameState.CLEARING, listener.changed.get(listener.changed.size() - 1).state());
    }

    @Test
    void 사이_150ms가_지나면_삭제_점수_Spawn이_이어진다() { // ANI-3, 4.1
        lockCompletingLine();
        int startsBefore = clock.starts.size();
        session.tick();

        GameSnapshot s = session.snapshot();
        assertEquals(GameState.PLAYING, session.getState());
        assertEquals("........OO", AsciiBoard.row(s, 19));
        assertEquals(19 + 19 + 18 + 100, s.score());
        assertEquals(1, s.lines());
        assertNotNull(s.currentBlock());
        assertEquals(startsBefore + 1, clock.starts.size());    // Spawn이 타이머를 다시 시작
        assertEquals(1000, clock.starts.get(clock.starts.size() - 1)); // Level 1 간격
    }

    @Test
    void CLEARING_중에는_조작과_하드드롭이_무시된다() { // ANI-2
        lockCompletingLine();
        String boardBefore = AsciiBoard.row(session.snapshot(), 19);
        long scoreBefore = session.snapshot().score();

        session.moveLeft();
        session.moveRight();
        session.moveDown();
        session.rotateRight();
        session.hardDrop();

        GameSnapshot s = session.snapshot();
        assertEquals(GameState.CLEARING, session.getState());
        assertEquals(boardBefore, AsciiBoard.row(s, 19));
        assertEquals(scoreBefore, s.score());
        assertEquals(0, s.lines());
    }

    @Test
    void CLEARING에서_Pause하면_멈추고_Resume하면_150ms를_처음부터_센다() { // ANI-2
        lockCompletingLine();
        int stopsBefore = clock.stops;

        session.pause();
        assertEquals(GameState.PAUSED, session.getState());
        assertEquals(stopsBefore + 1, clock.stops);

        session.tick();                                         // PAUSED의 tick은 무시
        assertEquals("IIIIIIIIOO", AsciiBoard.row(session.snapshot(), 19));

        session.resume();
        assertEquals(GameState.CLEARING, session.getState());
        assertEquals(CLEAR_MILLIS, clock.starts.get(clock.starts.size() - 1));

        session.tick();
        assertEquals(GameState.PLAYING, session.getState());
        assertEquals(1, session.snapshot().lines());
    }

    @Test
    void CLEARING에서_togglePause와_포커스_이탈도_Pause로_동작한다() {
        lockCompletingLine();
        session.togglePause();
        assertEquals(GameState.PAUSED, session.getState());
        session.togglePause();
        assertEquals(GameState.CLEARING, session.getState());

        session.onFocusLost();
        assertEquals(GameState.PAUSED, session.getState());
    }

    @Test
    void CLEARING에서_Quit_확인창을_취소하면_CLEARING으로_돌아간다() { // QIT-1
        lockCompletingLine();
        session.requestQuit();
        assertEquals(GameState.PAUSED, session.getState());

        session.cancelQuit();
        assertEquals(GameState.CLEARING, session.getState());
        assertEquals(CLEAR_MILLIS, clock.starts.get(clock.starts.size() - 1));
    }

    @Test
    void CLEARING에서_Quit_확인창에서_Yes면_세션이_끝난다() {
        lockCompletingLine();
        session.requestQuit();
        session.confirmQuit();
        assertEquals(GameState.ENDED, session.getState());
    }

    @Test
    void 지울_줄이_없으면_CLEARING을_거치지_않는다() { // ANI-4
        tick(20);   // 첫 I를 중앙에 그대로 고정
        assertEquals(GameState.PLAYING, session.getState());
        assertTrue(clock.starts.stream().noneMatch(ms -> ms == CLEAR_MILLIS));
        assertNotNull(session.snapshot().currentBlock());
    }

    @Test
    void Restart하면_CLEARING_흔적이_남지_않는다() {
        lockCompletingLine();
        session.pause();
        session.restart();
        assertEquals(GameState.PLAYING, session.getState());
        assertEquals(0, session.snapshot().lines());
        session.pause();
        session.resume();
        assertEquals(GameState.PLAYING, session.getState()); // 예전 CLEARING 표시가 남아 있으면 CLEARING으로 돌아간다
    }
}
