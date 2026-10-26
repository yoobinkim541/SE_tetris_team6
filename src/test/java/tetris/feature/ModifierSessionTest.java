package tetris.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tetris.component.block.*;
import tetris.feature.data.Board;
import tetris.feature.game.*;
import tetris.feature.item.*;

/** C의 효과 연결 전에도 A의 호출 시점·점수·타이머를 검증한다. */
class ModifierSessionTest {
    private static void set(GameSession session, String name, Object value) throws Exception {
        Field field = GameSession.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(session, value);
    }

    private static Block row() {
        int[][] shape = new int[10][10];
        Arrays.fill(shape[0], 1);
        return new Block(shape) {
            @Override public BlockType getType() { return BlockType.I; }
        };
    }

    @Test
    void rowBonusIsDoubledAndConsumptionWaitsUntilClearingCompletes() throws Exception {
        GameSession session = GameTestSupport.startedSession(row(), row());
        GameModifiers modifiers = new GameModifiers();
        modifiers.activateDoubleScore();
        set(session, "modifiers", modifiers);
        session.hardDrop();
        assertEquals(19, session.snapshot().score());
        assertEquals(10, session.snapshot().effects().getFirst().remainingBlocks());
        session.tick();
        assertEquals(219, session.snapshot().score());
        assertEquals(9, session.snapshot().effects().getFirst().remainingBlocks());
    }

    @Test
    void noClearLockStillConsumesDAndRestartResetsEffects() throws Exception {
        GameSession session = GameTestSupport.startedSession(new OBlock(), new OBlock());
        GameModifiers modifiers = new GameModifiers();
        modifiers.activateDoubleScore();
        set(session, "modifiers", modifiers);
        session.hardDrop();
        assertEquals(9, session.snapshot().effects().getFirst().remainingBlocks());
        session.pause();
        session.restart();
        assertTrue(session.snapshot().effects().isEmpty());
    }

    @Test
    void successfulSpawnConsumesSlowOnceAndResumeKeepsItsInterval() throws Exception {
        GameTestSupport.RecordingClock clock = new GameTestSupport.RecordingClock();
        GameSession session = new GameSession(GameTestSupport.sequence(row(), row()), clock, new GameTestSupport.RecordingListener());
        session.start();
        GameModifiers modifiers = new GameModifiers();
        modifiers.activateSlow();
        set(session, "modifiers", modifiers);
        session.hardDrop();
        assertEquals(150, clock.starts.getLast());
        session.tick();
        assertEquals(1500, clock.starts.getLast());
        assertEquals(List.of(new ActiveEffect(ItemType.SLOW, 9)), session.snapshot().effects());
        session.pause();
        session.resume();
        assertEquals(1500, clock.starts.getLast());
        assertEquals(9, session.snapshot().effects().getFirst().remainingBlocks());
    }

    @Test
    void failedSpawnDoesNotConsumeSlow() throws Exception {
        GameSession session = GameTestSupport.startedSession(new OBlock(), new OBlock());
        session.moveRight();
        session.moveRight();
        Board board = new Board();
        Block cell = GameTestSupport.cell();
        cell.setPosition(0, 4);
        assertTrue(board.tryPlaceBlock(cell));
        set(session, "board", board);
        GameModifiers modifiers = new GameModifiers();
        modifiers.activateSlow();
        set(session, "modifiers", modifiers);
        session.hardDrop();
        assertEquals(GameState.GAME_OVER, session.getState());
        assertEquals(10, session.snapshot().effects().getFirst().remainingBlocks());
    }

    @Test
    void scoringExample662DoublesOnlyRowsNotDropOrBombCells() throws Exception {
        GameSession session = GameTestSupport.startedSession(new OBlock());
        GameModifiers modifiers = new GameModifiers();
        modifiers.activateDoubleScore();
        set(session, "modifiers", modifiers);
        set(session, "score", 22L); // 이미 얻은 하강 점수
        Class<?> planType = Arrays.stream(GameSession.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("ClearPlan")).findFirst().orElseThrow();
        var constructor = planType.getDeclaredConstructor(List.class, Set.class);
        constructor.setAccessible(true);
        Object plan = constructor.newInstance(List.of(18, 19), Set.of());
        var award = GameSession.class.getDeclaredMethod("awardClearScore", planType, int.class);
        award.setAccessible(true);
        award.invoke(session, plan, 4); // 행 보너스 300 × 2 + 실제로 지운 4칸 × 10
        assertEquals(662, session.snapshot().score());
    }
}
