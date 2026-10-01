package tetris.feature.game;

import java.util.List;
import tetris.component.block.Block;
import tetris.feature.data.Board;
import tetris.feature.rule.BasicRotationPolicy;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.rule.ImmediateLockPolicy;
import tetris.feature.rule.LevelPolicy;
import tetris.feature.rule.LockPolicy;
import tetris.feature.rule.NextBlockQueue;
import tetris.feature.rule.RotationPolicy;
import tetris.feature.rule.ScoringPolicy;
import tetris.feature.rule.SpawnPolicy;

/**
 * 게임 한 판의 흐름과 상태기계 (명세 4, 8장). Swing EDT 단일 스레드에서만 사용한다.
 * 기존 move/*, drop/*, clear/*, playsetting/*, timer/* 클래스의 역할을 이 클래스의 메서드로 흡수한다.
 * 현재 블록은 보드 배열에 그리지 않고 currentBlock으로만 들고 있다가 Lock할 때 보드에 기록한다.
 */
public class GameSession {
    private static final int PREVIEW_COUNT = 1; // NXT-1

    private final Board board = new Board();
    private final BlockGenerator generator;
    private NextBlockQueue nextQueue; // start()마다 새로 만들어 생성기 순서가 새 게임의 첫 블록부터 이어지게 한다
    private Block currentBlock;
    private GameState state = GameState.LOADING;
    private long score;
    private int lines; // 누적 삭제 줄 (표시용)

    private final SpawnPolicy spawnPolicy = new SpawnPolicy();
    private final RotationPolicy rotationPolicy = new BasicRotationPolicy();
    private final LockPolicy lockPolicy = new ImmediateLockPolicy();
    private final LevelPolicy levelPolicy = new LevelPolicy();
    private final ScoringPolicy scoringPolicy = new ScoringPolicy();
    private final GameClock clock;
    private final GameListener listener;

    public GameSession(BlockGenerator generator, GameClock clock, GameListener listener) {
        if (generator == null) throw new IllegalArgumentException("generator cannot be null");
        if (clock == null) throw new IllegalArgumentException("clock cannot be null");
        if (listener == null) throw new IllegalArgumentException("listener cannot be null");
        this.generator = generator;
        this.clock = clock;
        this.listener = listener;
    }

    // ── 수명주기 ─────────────────────────────
    /** PIPE-4 */
    public void start() {
        state = GameState.LOADING;
        board.reset();
        score = 0;
        lines = 0;
        levelPolicy.reset();
        nextQueue = new NextBlockQueue(generator, PREVIEW_COUNT);
        currentBlock = null;

        state = GameState.PLAYING; // Spawn 충돌 시 PLAYING -> GAME_OVER 전이가 되도록 먼저 전환
        spawnNextBlock();
        if (state == GameState.PLAYING) listener.onChanged(snapshot());
    }

    public void restart() {
        // PAUSED -> LOADING -> 새 세션 (RST-1). 점수는 저장하지 않음
    }

    public void end() {
        // -> ENDED. 타이머 정지, 세션 폐기
    }

    // ── 조작 (PLAYING에서만 유효, 실패는 무시) ──
    public void moveLeft() {
        if (state != GameState.PLAYING) return;
        shift(-1);
    }

    public void moveRight() {
        if (state != GameState.PLAYING) return;
        shift(1);
    }

    /** Soft Drop: 자동 낙하와 같은 하강 시도. 낙하 타이머는 리셋하지 않는다 (TMR-4) */
    public void moveDown() {
        if (state != GameState.PLAYING) return;
        descendOrLock();
    }

    /** 실패하면 모양·위치·회전 상태를 바꾸지 않는다 (ROT-3) */
    public void rotateRight() {
        if (state != GameState.PLAYING) return;
        if (rotationPolicy.rotate(board, currentBlock)) listener.onChanged(snapshot());
    }

    public void hardDrop() {
        // board.getDropDistance(currentBlock)만큼 이동 -> +칸수 x Level -> 즉시 Lock
    }

    /** 자동 낙하 1회 = 하강 시도 (§3.1). PLAYING이 아니면 무시 (TMR-2) */
    public void tick() {
        if (state != GameState.PLAYING) return;
        descendOrLock();
    }

    // ── Pause / Quit ─────────────────────────
    public void pause() {
        // PLAYING -> PAUSED, 타이머 정지
    }

    public void resume() {
        // PAUSED -> PLAYING, 타이머를 전체 간격으로 재시작 (TMR-3)
    }

    public void togglePause() {
        // Pause 키: PLAYING이면 pause(), PAUSED면 resume(). 그 외 상태는 무시 (KEY: Pause 키는 최초 눌림만)
    }

    public void requestQuit() {
        // Quit 키: 확인창 동안 게임 정지 (QIT-1)
    }

    public void confirmQuit() {
        // Quit 확인 Yes: 세션 폐기 -> ENDED (점수는 저장하지 않음)
    }

    public void cancelQuit() {
        // Quit 확인 No: 이전 상태로 복귀
    }

    public void onFocusLost() {
        // 창 포커스 이탈: PLAYING이면 자동 Pause (PAU-3)
    }

    // ── 조회 ─────────────────────────────────
    public GameState getState() {
        return state;
    }

    public GameSnapshot snapshot() {
        return new GameSnapshot(
                board.copyCells(),
                currentBlock == null ? null : currentBlock.copy(),
                nextQueue == null ? null : nextQueue.peek(0).copy(),
                score,
                levelPolicy.getLevel(),
                lines,
                state);
    }

    // ── 명세 4장 파이프라인 (내부) ────────────
    // 좌우 이동. 실패하면 아무것도 바꾸지 않고 알리지도 않는다 (§3.1)
    private void shift(int deltaCol) {
        int row = currentBlock.getRow();
        int col = currentBlock.getCol() + deltaCol;
        if (!board.canPlace(currentBlock, row, col)) return;

        currentBlock.setPosition(row, col);
        listener.onChanged(snapshot());
    }

    // Tick과 Soft Drop 공통. 하강에 실패하면 LockPolicy 판단으로 Lock
    private void descendOrLock() {
        boolean descended = tryDescend();
        if (lockPolicy.shouldLock(!descended)) lockCurrentBlock();
        if (state == GameState.PLAYING) listener.onChanged(snapshot());
    }

    /** 성공하면 한 칸 내리고 +Level (SCR-1). 실패하면 아무것도 바꾸지 않는다 (SCR-4) */
    private boolean tryDescend() {
        int row = currentBlock.getRow() + 1;
        int col = currentBlock.getCol();
        if (!board.canPlace(currentBlock, row, col)) return false;

        currentBlock.setPosition(row, col);
        score += scoringPolicy.softDropScore(levelPolicy.getLevel());
        return true;
    }

    /** §4 LOCK. Level은 L5와 다음 Spawn의 S3에서만 바뀐다 (PIPE-1) */
    private void lockCurrentBlock() {
        if (!board.tryPlaceBlock(currentBlock))                                          // L1
            throw new IllegalStateException("current block is not at a placeable position");
            
        currentBlock = null; // 보드에 들어간 블록을 스냅샷이 두 번 그리지 않도록

        List<Integer> fullRows = board.findFullRows();                                // L2
        int clearedRows = fullRows.size();

        board.clearRows(fullRows);                                                    // L3
        score += scoringPolicy.lineClearBonus(clearedRows);                               // L4
        lines += clearedRows;
        levelPolicy.onLinesCleared(clearedRows);                                          // L5

        spawnNextBlock();
    }

    private void spawnNextBlock() {
        Block block = nextQueue.pop();                                             // S1
        int row = spawnPolicy.getSpawnRow(block);
        int col = spawnPolicy.getSpawnColumn(block);

        if (!board.canPlace(block, row, col)) {                                    // S2
            gameOver(); // SPN-4: 블록을 놓지 않고 카운터·Level·타이머도 바꾸지 않는다
            return;
        }

        block.setPosition(row, col);
        currentBlock = block;
        levelPolicy.onBlockSpawned();                                              // S3
        clock.start(levelPolicy.fallIntervalMillis());                             // S4
    }

    private void gameOver() {
        state = GameState.GAME_OVER;
        clock.stop();
        listener.onGameOver(snapshot());
    }
}
