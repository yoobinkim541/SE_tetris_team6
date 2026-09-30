package tetris.feature.game;

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
        // 왼쪽 1칸. 불가하면 무시, 점수 0
    }

    public void moveRight() {
        // 오른쪽 1칸. 불가하면 무시, 점수 0
    }

    public void moveDown() {
        // Soft Drop: 1칸 하강 시도 (성공 +Level, 실패 Lock)
    }

    public void rotateRight() {
        // 시계방향 회전 (RotationPolicy). 실패는 무시
    }

    public void hardDrop() {
        // board.getDropDistance(currentBlock)만큼 이동 -> +칸수 x Level -> 즉시 Lock
    }

    /** 자동 낙하 1회 = 하강 시도 (§3.1). 실패하면 LockPolicy 판단으로 Lock. PLAYING이 아니면 무시 (TMR-2) */
    public void tick() {
        if (state != GameState.PLAYING) return;

        boolean descended = tryDescend();
        if (lockPolicy.shouldLock(!descended)) lockCurrentBlock();
        if (state == GameState.PLAYING) listener.onChanged(snapshot());
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
    /** 성공하면 한 칸 내리고 +Level (SCR-1). 실패하면 아무것도 바꾸지 않는다 (SCR-4) */
    private boolean tryDescend() {
        int row = currentBlock.getRow() + 1;
        int col = currentBlock.getCol();
        if (!board.canPlace(currentBlock, row, col)) return false;

        currentBlock.setPosition(row, col);
        score += scoringPolicy.softDropScore(levelPolicy.getLevel());
        return true;
    }

    private void lockCurrentBlock() {
        // L1~L5: 보드에 기록 -> 완성 행 삭제 -> 보너스 -> lineCounter/Level 갱신 -> spawnNextBlock()
        // 기록 후 currentBlock = null (보드에 들어간 블록을 스냅샷이 두 번 그리지 않도록)
    }

    private void spawnNextBlock() {
        Block block = nextQueue.pop();                                             // S1
        int row = spawnPolicy.row(block);
        int col = spawnPolicy.column(block);
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
