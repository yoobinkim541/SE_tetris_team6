package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tetris.component.block.Block;
import tetris.feature.GameTestSupport.RecordingClock;
import tetris.feature.GameTestSupport.RecordingListener;
import tetris.feature.data.Board;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.rule.RandomBlockGenerator;

/**
 * 비기능 테스트 (명세 15.3, 요구사항 p4·p15·p17): 시드 고정 무작위 입력 10,000회 이상, 매 연산 후 불변식 검사.
 * 처리 시간도 재서, 입력 1회 처리가 화면 한 프레임(약 16 ms)보다 충분히 짧은지 확인한다 (반복 입력에 즉시 반응, p8).
 */
public class RandomInputStressTest {
    private static final int OPERATIONS = 20_000;
    private static final int HIDDEN_ROWS = Board.MARGIN;
    // 입력 1회 평균 처리 시간 상한. 실측(약 0.4~1.5 μs, 2026-10-04)보다 수백 배 넉넉하게 잡아 느린 CI 러너에서도 흔들리지 않게 한다
    private static final long MAX_AVERAGE_NANOS = 1_000_000; // 1 ms

    private enum Input { LEFT, RIGHT, SOFT_DROP, ROTATE, HARD_DROP, TICK }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 42, 2026, 7_777, 123_456_789})
    void 무작위_입력_2만회_동안_불변식이_유지되고_입력_처리가_충분히_빠르다(long seed) {
        Random inputs = new Random(seed);
        Input[] choices = Input.values();
        GameSession session = newSession(seed);
        long previousScore = 0;
        int previousLevel = 1;
        int games = 1;
        long elapsedNanos = 0;

        for (int i = 0; i < OPERATIONS; i++) {
            Input input = choices[inputs.nextInt(choices.length)];
            long start = System.nanoTime();
            apply(session, input);
            elapsedNanos += System.nanoTime() - start;

            GameSnapshot snapshot = session.snapshot();
            String where = "seed " + seed + ", op " + i + " (" + input + ")";
            assertInvariants(snapshot, where);
            assertTrue(snapshot.score() >= previousScore, "점수가 줄었다: " + where);
            assertTrue(snapshot.level() >= previousLevel, "Level이 줄었다: " + where);
            previousScore = snapshot.score();
            previousLevel = snapshot.level();

            if (snapshot.state() == GameState.GAME_OVER) { // 새 게임으로 계속 (Score·Level 초기화)
                session.start();
                previousScore = 0;
                previousLevel = 1;
                games++;
            }
        }

        long average = elapsedNanos / OPERATIONS;
        System.out.printf("[stress] seed %d: %,d ops, %d games, avg %,d ns/op, total %,d ms%n",
                seed, OPERATIONS, games, average, elapsedNanos / 1_000_000);
        assertTrue(average < MAX_AVERAGE_NANOS, "입력 1회 평균 처리 시간이 너무 길다: " + average + " ns");
    }

    private static GameSession newSession(long seed) {
        GameSession session = new GameSession(
                new RandomBlockGenerator(new Random(seed)), new RecordingClock(), new RecordingListener());
        session.start();
        return session;
    }

    private static void apply(GameSession session, Input input) {
        switch (input) {
            case LEFT -> session.moveLeft();
            case RIGHT -> session.moveRight();
            case SOFT_DROP -> session.moveDown();
            case ROTATE -> session.rotateRight();
            case HARD_DROP -> session.hardDrop();
            case TICK -> session.tick();
        }
    }

    private static void assertInvariants(GameSnapshot snapshot, String where) {
        GameState state = snapshot.state();
        if (state != GameState.PLAYING && state != GameState.GAME_OVER) fail("예상 밖 상태 " + state + ": " + where);

        int[][] cells = snapshot.cells();
        assertEquals(Board.HEIGHT, cells.length, "보드 행 수: " + where);
        for (int[] row : cells) {
            assertEquals(Board.WIDTH, row.length, "보드 열 수: " + where);
            for (int value : row) {
                if (value < 0 || value > 7) fail("보드 값이 0..7 밖이다 (" + value + "): " + where);
            }
        }
        assertTrue(snapshot.level() >= 1, "Level은 1 이상: " + where);

        Block current = snapshot.currentBlock();
        if (state == GameState.GAME_OVER || current == null) return;
        int[][] shape = current.getShape();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 0) continue;
                int row = current.getRow() + r;
                int col = current.getCol() + c;
                if (col < 0 || col >= Board.WIDTH || row >= Board.HEIGHT || row < -HIDDEN_ROWS) {
                    fail("현재 블록이 보드 밖에 있다 (" + row + ", " + col + "): " + where);
                }
                if (row >= 0 && cells[row][col] != 0) fail("현재 블록이 고정 칸과 겹친다 (" + row + ", " + col + "): " + where);
            }
        }
    }
}
