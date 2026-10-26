package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Random;
import org.junit.jupiter.api.Test;
import tetris.component.block.BlockType;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.rule.WeightedBlockGenerator;

/**
 * 실제 게임 세션이 분포 테스트를 통과한 WeightedBlockGenerator와 설정 난이도로 만들어지는지 검증한다.
 * MainFrame 자체는 화면 없는 환경에서 만들 수 없어서 세션을 만드는 함수만 확인한다.
 */
public class MainFrameSessionTest {
    private static final GameClock NO_CLOCK = new GameClock() {
        @Override
        public void start(int intervalMillis) {
        }

        @Override
        public void stop() {
        }
    };

    private static final GameListener NO_LISTENER = new GameListener() {
        @Override
        public void onChanged(GameSnapshot snapshot) {
        }

        @Override
        public void onGameOver(GameSnapshot snapshot) {
        }
    };

    // 새 게임은 Next 큐를 채우며 한 번, 첫 Spawn 뒤 큐를 다시 채우며 한 번 생성기를 부른다
    private static BlockType[] firstTwoBlocks(GameSession session) {
        session.start();
        GameSnapshot snapshot = session.snapshot();
        return new BlockType[] {snapshot.currentBlock().getType(), snapshot.nextBlock().getType()};
    }

    @Test
    void 일반_모드_세션은_설정_난이도를_쓴다() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameSession session = MainFrame.newSession(GameMode.CLASSIC, difficulty, new Random(1), NO_CLOCK, NO_LISTENER);
            assertEquals(GameMode.CLASSIC, session.getMode());
            assertEquals(difficulty, session.getDifficulty());
        }
    }

    @Test
    void 세션의_블록은_같은_시드의_WeightedBlockGenerator와_같은_순서로_나온다() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (long seed = 0; seed < 30; seed++) {
                WeightedBlockGenerator expected = new WeightedBlockGenerator(difficulty.weights(), new Random(seed));
                BlockType[] actual = firstTwoBlocks(
                        MainFrame.newSession(GameMode.CLASSIC, difficulty, new Random(seed), NO_CLOCK, NO_LISTENER));

                assertEquals(expected.next().getType(), actual[0], difficulty + " seed " + seed);
                assertEquals(expected.next().getType(), actual[1], difficulty + " seed " + seed);
            }
        }
    }

    @Test
    void 아이템_모드는_항상_NORMAL로_만든다() {
        GameSession session = MainFrame.newSession(GameMode.ITEM, Difficulty.HARD, new Random(1), NO_CLOCK, NO_LISTENER);
        assertEquals(GameMode.ITEM, session.getMode());
        assertEquals(Difficulty.NORMAL, session.getDifficulty());
        assertNotNull(firstTwoBlocks(session)[0]);
    }
}
