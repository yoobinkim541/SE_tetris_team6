package tetris.feature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import tetris.component.block.Block;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.rule.BlockGenerator;

/** GameSession 테스트용 가짜 협력 객체. 타이머 없이 tick()을 직접 호출하는 방식(TMR-2)을 전제로 한다 */
final class GameTestSupport {
    private GameTestSupport() {
    }

    /** 정해진 순서대로 블록을 내주는 생성기 (BLK-3: 고정 순열 주입) */
    static BlockGenerator sequence(Block... blocks) {
        Iterator<Block> it = Arrays.asList(blocks).iterator();
        return it::next;
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
