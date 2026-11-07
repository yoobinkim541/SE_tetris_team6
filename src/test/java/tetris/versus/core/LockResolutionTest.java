package tetris.versus.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.OBlock;
import tetris.feature.data.Board;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.versus.api.LockResult;
import tetris.feature.versus.api.SpawnBoundary;

/** S7-A2: GameSession이 삭제 확정 때 LockResult를 한 번만 발행하는지 (R3-ATTACK, R3-ORDER, R3-REGRESSION) */
class LockResolutionTest {
    private static final int X = 9;

    private static final class Recorder implements SpawnBoundary {
        final List<LockResult> results = new ArrayList<>();

        @Override public void onLockResolved(LockResult result) { results.add(result); }

        @Override public boolean deferSpawn() { return false; }
    }

    private static final GameClock NO_CLOCK = new GameClock() {
        @Override public void start(int intervalMillis) { }

        @Override public void stop() { }
    };

    private static final GameListener NO_LISTENER = new GameListener() {
        @Override public void onChanged(GameSnapshot snapshot) { }

        @Override public void onGameOver(GameSnapshot snapshot) { }
    };

    private final Recorder recorder = new Recorder();

    /** O 블록만 나오는 세션. O는 col 4,5에 생성된다 */
    private GameSession oSession(SpawnBoundary boundary) {
        GameSession session = new GameSession(OBlock::new, NO_CLOCK, NO_LISTENER);
        if (boundary != null) session.setSpawnBoundary(boundary);
        session.start();
        return session;
    }

    private static void fillRowExcept(GameSession session, int row, int... holes) throws Exception {
        Field field = GameSession.class.getDeclaredField("board");
        field.setAccessible(true);
        Board board = (Board) field.get(session);
        for (int col = 0; col < Board.WIDTH; col++) {
            boolean hole = false;
            for (int h : holes) hole |= h == col;
            if (hole) continue;
            Block cell = new Block(new int[][] {{1}}) {
                @Override
                public BlockType getType() {
                    return BlockType.O;
                }
            };
            cell.setPosition(row, col);
            assertTrue(board.tryPlaceBlock(cell));
        }
    }

    @Test
    void twoLineClearIsResolvedOnceAfterClearingWithTheBlockShapeAsHoles() throws Exception {
        GameSession session = oSession(recorder);
        fillRowExcept(session, 18, 4, 5);
        fillRowExcept(session, 19, 4, 5);

        session.hardDrop();
        assertEquals(GameState.CLEARING, session.getState());
        assertTrue(recorder.results.isEmpty(), "CLEARING 시작 때는 보내지 않는다");

        session.pause();                    // CLEARING 중 Pause → Resume으로 150 ms를 다시 세도
        session.resume();
        assertTrue(recorder.results.isEmpty());

        session.tick();                     // 150 ms 경과 → 삭제 확정
        assertEquals(1, recorder.results.size());
        LockResult result = recorder.results.get(0);
        assertEquals(2, result.clearedRows());
        assertEquals(2, result.attack().size());
        for (int[] row : result.attack().rows()) {
            assertArrayEquals(new int[] {X, X, X, X, 0, 0, X, X, X, X}, row);
        }

        session.tick();                     // 다음 블록의 낙하. 다시 발행하지 않는다
        assertEquals(1, recorder.results.size());
    }

    @Test
    void lockWithoutClearAndSingleLineClearResolveWithoutAttack() throws Exception {
        GameSession session = oSession(recorder);
        session.hardDrop();                 // 빈 보드: 삭제 없음, CLEARING 없이 바로 정산
        assertEquals(1, recorder.results.size());
        assertEquals(0, recorder.results.get(0).clearedRows());
        assertTrue(recorder.results.get(0).attack().isEmpty());

        fillRowExcept(session, 17, 4, 5);   // O가 18~17행에 쌓이며 17행만 완성 → 1줄
        session.hardDrop();
        session.tick();
        assertEquals(2, recorder.results.size());
        assertEquals(1, recorder.results.get(1).clearedRows());
        assertTrue(recorder.results.get(1).attack().isEmpty());
    }

    @Test
    void singlePlayerSessionKeepsImmediateBoundary() throws Exception {
        GameSession session = oSession(null);
        fillRowExcept(session, 18, 4, 5);
        fillRowExcept(session, 19, 4, 5);
        session.hardDrop();
        session.tick();
        assertEquals(GameState.PLAYING, session.getState());
        assertEquals(2, session.snapshot().lines());
        assertThrows(IllegalArgumentException.class, () -> session.setSpawnBoundary(null));
    }

    @Test
    void endingDuringClearingDropsThePendingAttack() throws Exception {
        GameSession session = oSession(recorder);
        fillRowExcept(session, 18, 4, 5);
        fillRowExcept(session, 19, 4, 5);
        session.hardDrop();
        session.end();
        session.tick();
        assertTrue(recorder.results.isEmpty());

        session.start();                    // 새 판에서 이전 공격이 새어 나오지 않는다
        session.hardDrop();
        assertEquals(1, recorder.results.size());
        assertTrue(recorder.results.get(0).attack().isEmpty());
    }
}
