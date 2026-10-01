package tetris.feature;

import java.util.ArrayList;
import java.util.List;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.rule.BlockGenerator;

/** GameSession 테스트용 가짜 협력 객체. 타이머 없이 tick()을 직접 호출하는 방식(TMR-2)을 전제로 한다 */
final class GameTestSupport {
    private GameTestSupport() {
    }

    /**
     * 정해진 순서대로 블록을 내주는 생성기 (BLK-3: 고정 순열 주입). 다 쓰면 처음부터 반복한다.
     * Lock 후 Spawn마다 Next 큐를 채우느라 생성기를 한 번 더 부르므로, 테스트가 블록 수를 정확히 맞추지 않아도 되게 한다.
     * 같은 인스턴스를 두 번 내주면 상태가 공유되므로 두 바퀴째부터는 복사본을 내준다.
     */
    static BlockGenerator sequence(Block... blocks) {
        int[] next = {0};
        return () -> {
            int i = next[0]++;
            return i < blocks.length ? blocks[i] : blocks[i % blocks.length].copy();
        };
    }

    /** 보드의 특정 칸을 채울 때 쓰는 1x1 블록. 기록할 때 종류 값이 필요해서 O로 둔다 */
    static Block cell() {
        return new Block(new int[][] {{1}}) {
            @Override
            public BlockType getType() {
                return BlockType.O;
            }
        };
    }

    /** 블록 순서만 정해서 시작한 세션 */
    static GameSession startedSession(Block... blocks) {
        GameSession session = new GameSession(sequence(blocks), new RecordingClock(), new RecordingListener());
        session.start();
        return session;
    }

    static final class RecordingClock implements GameClock {
        final List<Integer> starts = new ArrayList<>();
        int stops;

        @Override public void start(int intervalMillis) { starts.add(intervalMillis); }
        @Override public void stop() { stops++; }
    }

    static final class RecordingListener implements GameListener {
        final List<GameSnapshot> changed = new ArrayList<>();
        final List<GameSnapshot> gameOvers = new ArrayList<>();

        @Override public void onChanged(GameSnapshot snapshot) { changed.add(snapshot); }
        @Override public void onGameOver(GameSnapshot snapshot) { gameOvers.add(snapshot); }
    }
}
