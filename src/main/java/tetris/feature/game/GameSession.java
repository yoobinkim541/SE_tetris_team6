package tetris.feature.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import tetris.component.block.Block;
import tetris.component.block.WeightBlock;
import tetris.feature.data.Board;
import tetris.feature.item.Cell;
import tetris.feature.item.GameModifiers;
import tetris.feature.item.ItemResult;
import tetris.feature.item.ItemType;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.rule.BasicRotationPolicy;
import tetris.feature.rule.ImmediateLockPolicy;
import tetris.feature.rule.ItemBlockGenerator;
import tetris.feature.rule.LevelPolicy;
import tetris.feature.rule.LockDelayPolicy;
import tetris.feature.rule.LockPolicy;
import tetris.feature.rule.NextBlockQueue;
import tetris.feature.setting.ControlStyle;
import tetris.feature.rule.RotationPolicy;
import tetris.feature.rule.ScoringPolicy;
import tetris.feature.rule.SpawnPolicy;
import tetris.feature.rule.SrsRotationPolicy;
import tetris.feature.rule.TSpinDetector;
import tetris.feature.rule.TSpinType;
import tetris.feature.versus.AttackRowExtractor;
import tetris.feature.versus.api.AttackRows;
import tetris.feature.versus.api.LockResult;
import tetris.feature.versus.api.SpawnBoundary;

/**
 * 게임 한 판의 흐름과 상태기계 (명세 4, 8장). Swing EDT 단일 스레드에서만 사용한다.
 * 기존 move/*, drop/*, clear/*, playsetting/*, timer/* 클래스의 역할을 이 클래스의 메서드로 흡수한다.
 * 현재 블록은 보드 배열에 그리지 않고 currentBlock으로만 들고 있다가 Lock할 때 보드에 기록한다.
 */
public class GameSession {
    private static final int CLASSIC_PREVIEW_COUNT = 1; // NXT-1
    private static final int MODERN_PREVIEW_COUNT = 3;   // NXT-2
    private static final int CLEAR_ANIMATION_MILLIS = 150; // ANI-1
    private static final int CELL_CLEAR_SCORE = 10;        // 아이템이 지운 고정 칸당 점수 (ITB-4)

    private final Board board = new Board();
    private final BlockGenerator generator;
    private NextBlockQueue nextQueue; // start()마다 새로 만들어 생성기 순서가 새 게임의 첫 블록부터 이어지게 한다
    private Block currentBlock;
    private GameState state = GameState.LOADING;
    private long score;
    private int lines; // 누적 삭제 줄 (표시용)
    private int currentFallIntervalMillis = 1000;
    private boolean quitPending;                  // Quit 확인창이 떠 있는 동안 true
    private GameState stateBeforeQuit;            // 확인창을 띄우기 전 상태 (No 선택 시 복귀용)
    private ClearPlan pendingClear;               // CLEARING 동안 150 ms 뒤에 지울 대상
    private boolean pausedDuringClearing;         // CLEARING에서 Pause했는지. Resume 때 CLEARING으로 돌아간다
    private GameModifiers modifiers = new GameModifiers(); // 지속 효과 (D, S). start()마다 새로 만든다
    private boolean crushing;                     // 무게추가 첫 하강에 실패해 분쇄 모드가 됐는지 (ITW-4)
    private boolean lockDelayActive;              // 착지해 Lock 유예 타이머가 도는 중인지 (LCK-6)
    private int lockResets;                       // 이번 블록이 유예 타이머를 다시 센 횟수 (최대 LockPolicy.maxResets)
    private boolean lastActionIsRotate;           // 마지막으로 성공한 조작이 회전인지 (T-스핀 판정용, ROT-7)
    private int lastKickIndex = -1;               // 마지막 회전에 쓰인 SRS 킥 번호. 회전한 적 없으면 -1 (TSP-3)
    private TSpinType lastTSpin = TSpinType.NONE; // 가장 최근 Lock의 T-스핀 판정. 다음 Lock 때 바뀐다 (TSP-4)
    private Block held;                           // 홀드 칸. 비어 있으면 null (HLD-1)
    private boolean holdUsed;                     // 이번 블록에서 홀드를 이미 썼는지. Lock 뒤 Spawn에서 풀린다
    private boolean bufferRotate;                 // CLEARING 중 버퍼된 회전 (BUF-1)
    private boolean bufferRotateCcw;
    private boolean bufferHold;
    private boolean bufferHardDrop;
    private boolean applyingBuffer;

    private final SpawnPolicy spawnPolicy = new SpawnPolicy();
    private final RotationPolicy rotationPolicy;
    private final int previewCount;
    private final boolean modernFeatures;
    private final LockPolicy lockPolicy;
    private final LevelPolicy levelPolicy;
    private final ScoringPolicy scoringPolicy = new ScoringPolicy();
    private final GameClock clock;
    private final GameListener listener;
    private final GameMode mode;
    private final Difficulty difficulty;
    private SpawnBoundary spawnBoundary;
    private boolean spawnPending;
    private boolean attackApplied;
    private int[] lastClearedRows = new int[0];
    private java.util.List<Cell> lastPlacedCells = java.util.List.of();

    /** 일반 모드·NORMAL 난이도 */
    public GameSession(BlockGenerator generator, GameClock clock, GameListener listener) {
        this(GameMode.CLASSIC, Difficulty.NORMAL, generator, clock, listener);
    }

    /** 모드와 난이도는 한 판이 끝날 때까지 바뀌지 않는다 (DIF-5, MOD-2) */
    public GameSession(GameMode mode, Difficulty difficulty, BlockGenerator generator, GameClock clock, GameListener listener) {
        this(mode, difficulty, generator, clock, listener, SpawnBoundary.IMMEDIATE);
    }

    /** Lock 정책을 직접 고른다. 1차 규칙(하강 실패 즉시 Lock)을 재현할 때 ImmediateLockPolicy를 넘긴다 */
    public GameSession(GameMode mode, Difficulty difficulty, BlockGenerator generator, GameClock clock,
                       GameListener listener, LockPolicy lockPolicy) {
        this(mode, difficulty, generator, clock, listener, SpawnBoundary.IMMEDIATE, lockPolicy);
    }

    /** Versus hook: settlement is observable before the next block is spawned. */
    public GameSession(GameMode mode, Difficulty difficulty, BlockGenerator generator, GameClock clock,
                       GameListener listener, SpawnBoundary spawnBoundary) {
        this(mode, difficulty, generator, clock, listener, spawnBoundary, new LockDelayPolicy());
    }

    public GameSession(GameMode mode, Difficulty difficulty, BlockGenerator generator, GameClock clock,
                       GameListener listener, SpawnBoundary spawnBoundary, LockPolicy lockPolicy) {
        this(mode, difficulty, generator, clock, listener, spawnBoundary, lockPolicy,
                new SrsRotationPolicy(), CLASSIC_PREVIEW_COUNT, false);
    }

    /**
     * 조작 방식 (CTL-1). CLASSIC은 즉시 Lock·킥 없음·Next 1. MODERN은 Lock Delay·SRS·Next 3과
     * 고스트·홀드·반시계·입력 버퍼를 켠다. 한 판이 시작될 때 정하고 도중에는 바꾸지 않는다.
     */
    public GameSession(GameMode mode, Difficulty difficulty, BlockGenerator generator, GameClock clock,
                       GameListener listener, ControlStyle style) {
        this(mode, difficulty, generator, clock, listener, SpawnBoundary.IMMEDIATE, style);
    }

    /** 대전 엔진용. 호스트가 고른 조작 방식을 Spawn 경계와 함께 넘긴다 */
    public GameSession(GameMode mode, Difficulty difficulty, BlockGenerator generator, GameClock clock,
                       GameListener listener, SpawnBoundary spawnBoundary, ControlStyle style) {
        this(mode, difficulty, generator, clock, listener, spawnBoundary, lockPolicyOf(style),
                style == ControlStyle.CLASSIC ? new BasicRotationPolicy() : new SrsRotationPolicy(),
                style == ControlStyle.MODERN ? MODERN_PREVIEW_COUNT : CLASSIC_PREVIEW_COUNT,
                style == ControlStyle.MODERN);
    }

    private GameSession(GameMode mode, Difficulty difficulty, BlockGenerator generator, GameClock clock,
                       GameListener listener, SpawnBoundary spawnBoundary, LockPolicy lockPolicy,
                       RotationPolicy rotationPolicy, int previewCount, boolean modernFeatures) {
        if (mode == null) throw new IllegalArgumentException("mode cannot be null");
        if (difficulty == null) throw new IllegalArgumentException("difficulty cannot be null");
        if (generator == null) throw new IllegalArgumentException("generator cannot be null");
        if (clock == null) throw new IllegalArgumentException("clock cannot be null");
        if (listener == null) throw new IllegalArgumentException("listener cannot be null");
        this.mode = mode;
        this.difficulty = mode == GameMode.ITEM ? Difficulty.NORMAL : difficulty;
        this.levelPolicy = new LevelPolicy(this.difficulty);
        this.generator = mode == GameMode.ITEM && !(generator instanceof ItemBlockGenerator)
                ? new ItemBlockGenerator(generator, WeightBlock::new, new Random()) : generator;
        this.clock = clock;
        this.listener = listener;
        this.spawnBoundary = Objects.requireNonNull(spawnBoundary, "spawnBoundary");
        this.lockPolicy = Objects.requireNonNull(lockPolicy, "lockPolicy");
        this.rotationPolicy = Objects.requireNonNull(rotationPolicy, "rotationPolicy");
        this.previewCount = previewCount;
        this.modernFeatures = modernFeatures;
    }

    private static LockPolicy lockPolicyOf(ControlStyle style) {
        if (style == null) throw new IllegalArgumentException("style cannot be null");
        return style == ControlStyle.CLASSIC ? new ImmediateLockPolicy() : new LockDelayPolicy();
    }

    // ── 수명주기 ─────────────────────────────
    /** PIPE-4 */
    public void start() {
        state = GameState.LOADING;
        board.reset();
        score = 0;
        lines = 0;
        levelPolicy.reset();
        nextQueue = new NextBlockQueue(generator, previewCount);
        currentBlock = null;
        quitPending = false;
        spawnPending = false;
        attackApplied = false;
        stateBeforeQuit = null;
        pendingClear = null;
        pausedDuringClearing = false;
        crushing = false;
        resetLockState();
        held = null;
        holdUsed = false;
        clearInputBuffer();
        lastTSpin = TSpinType.NONE;
        modifiers = new GameModifiers();

        state = GameState.PLAYING; // Spawn 충돌 시 PLAYING -> GAME_OVER 전이가 되도록 먼저 전환
        spawnNextBlock();
        if (state == GameState.PLAYING) listener.onChanged(snapshot());
    }

    /** PAUSED -> LOADING -> 새 세션 (RST-1). 점수는 저장하지 않는다. Pause 메뉴에서만 가능 */
    public void restart() {
        if (state != GameState.PAUSED || quitPending) return;
        start();
    }

    /** -> ENDED. 타이머 정지, 세션 폐기. 이미 ENDED이면 무시 */
    public void end() {
        if (state == GameState.ENDED) return;
        clock.stop();
        currentBlock = null;
        quitPending = false;
        spawnPending = false;
        attackApplied = false;
        stateBeforeQuit = null;
        pendingClear = null;
        pausedDuringClearing = false;
        state = GameState.ENDED;
    }

    // ── 조작 (PLAYING에서만 유효, 실패는 무시) ──
    public void moveLeft() {
        if (state != GameState.PLAYING || spawnPending) return;
        shift(-1);
    }

    public void moveRight() {
        if (state != GameState.PLAYING || spawnPending) return;
        shift(1);
    }

    /** Soft Drop: 자동 낙하와 같은 하강 시도. 낙하 타이머는 리셋하지 않는다 (TMR-4). 착지 유예 중에는 Lock을 앞당기지 않는다 (LCK-6) */
    public void moveDown() {
        if (state != GameState.PLAYING || spawnPending) return;
        descendOrLock(false);
    }

    /** 실패하면 모양·위치·회전 상태를 바꾸지 않는다 (ROT-3) */
    public void rotateRight() {
        if (bufferRotation(false)) return;
        if (state != GameState.PLAYING || spawnPending || currentBlock == null) return;
        if (currentBlock.isWeight()) return; // ITW-1: 무게추는 회전하지 않는다
        applyRotation(false);
    }

    /** 반시계 90°. 클래식에서는 무시한다 (ROT-9) */
    public void rotateLeft() {
        if (!modernFeatures) return;
        if (bufferRotation(true)) return;
        if (state != GameState.PLAYING || spawnPending || currentBlock == null) return;
        if (currentBlock.isWeight()) return;
        applyRotation(true);
    }

    /**
     * 홀드 (HLD-1, HLD-2). 블록당 한 번, Lock 뒤 다음 Spawn에서 다시 쓸 수 있다.
     * 바꾸면 홀드 칸의 블록을 스폰 위치·초기 회전으로 꺼낸다. 무게추는 홀드할 수 없다.
     * 아이템 칸은 블록에 붙은 채로 같이 들어간다.
     */
    public void hold() {
        if (!modernFeatures) return;
        if (state == GameState.CLEARING) {
            bufferHold = true;
            return;
        }
        if (state != GameState.PLAYING || spawnPending || holdUsed || currentBlock == null) return;
        if (currentBlock.isWeight()) return;
        Block incoming = held;
        held = normalized(currentBlock);
        holdUsed = true;
        currentBlock = null;
        resetLockState();
        if (incoming == null) spawnPiece(nextQueue.pop(), false);
        else spawnPiece(incoming, false);
        if (state == GameState.PLAYING) listener.onChanged(snapshot());
    }

    /** 내려갈 수 있는 만큼 한 번에 내리고 +칸수 x Level (SCR-2) 후 즉시 Lock. 점수는 Level이 바뀔 수 있는 Lock 전에 더한다 */
    public void hardDrop() {
        if (state == GameState.CLEARING && modernFeatures) {
            bufferHardDrop = true;
            return;
        }
        if (state != GameState.PLAYING || spawnPending || currentBlock == null) return;

        if (currentBlock.isWeight()) {
            crushToBottom(); // ITW-6, ITW-7: 점수 없음
        } else {
            int distance = board.getDropDistance(currentBlock);
            currentBlock.setPosition(currentBlock.getRow() + distance, currentBlock.getCol());
            score += scoringPolicy.hardDropScore(distance, levelPolicy.getLevel());
            if (distance > 0) lastActionIsRotate = false; // 내려가지 않았다면 직전 회전이 그대로 마지막 행동이다
        }

        lockCurrentBlock();
        if (state == GameState.PLAYING) listener.onChanged(snapshot());
    }

    /** 자동 낙하 1회 = 하강 시도 (§3.1). PLAYING이 아니면 무시 (TMR-2) */
    public void tick() {
        if (state == GameState.CLEARING) {
            finishClearing(); // 150 ms가 지났다 (ANI-3)
            return;
        }
        if (state != GameState.PLAYING || spawnPending) return;
        descendOrLock(true);
    }

    // ── Pause / Quit ─────────────────────────
    /** PLAYING 또는 CLEARING -> PAUSED, 타이머 정지 (PAU-1) */
    public void pause() {
        if (state != GameState.PLAYING && state != GameState.CLEARING) return;
        pausedDuringClearing = state == GameState.CLEARING;
        clock.stop();
        state = GameState.PAUSED;
        listener.onChanged(snapshot());
    }

    /**
     * PAUSED -> PLAYING, 타이머를 전체 간격으로 재시작 (TMR-3). Quit 확인창이 떠 있으면 무시.
     * CLEARING에서 Pause했다면 CLEARING으로 돌아가 150 ms를 처음부터 다시 센다 (ANI-2)
     */
    public void resume() {
        if (state != GameState.PAUSED || quitPending) return;
        if (pausedDuringClearing) {
            state = GameState.CLEARING;
            clock.start(CLEAR_ANIMATION_MILLIS);
        } else {
            state = GameState.PLAYING;
            if (!spawnPending) clock.start(lockDelayActive ? lockPolicy.lockDelayMillis() : currentFallIntervalMillis);
        }
        pausedDuringClearing = false;
        listener.onChanged(snapshot());
    }

    /** Pause 키: PLAYING·CLEARING이면 pause(), PAUSED면 resume(). 그 외 상태는 무시 */
    public void togglePause() {
        if (state == GameState.PLAYING || state == GameState.CLEARING) pause();
        else if (state == GameState.PAUSED) resume();
    }

    /** Quit 키: 확인창 동안 게임 정지 (QIT-1). PLAYING·CLEARING·PAUSED에서만 유효 */
    public void requestQuit() {
        if (quitPending) return;
        if (state != GameState.PLAYING && state != GameState.CLEARING && state != GameState.PAUSED) return;
        stateBeforeQuit = state;
        quitPending = true;
        pause();
    }

    /** Quit 확인 Yes: 세션 폐기 -> ENDED (점수는 저장하지 않음) */
    public void confirmQuit() {
        if (!quitPending) return;
        end();
    }

    /** Quit 확인 No: Quit 키를 누르기 전 상태로 복귀. PLAYING·CLEARING이었다면 타이머를 다시 시작 (TMR-3) */
    public void cancelQuit() {
        if (!quitPending) return;
        quitPending = false;
        GameState previous = stateBeforeQuit;
        stateBeforeQuit = null;
        if (previous == GameState.PLAYING || previous == GameState.CLEARING) resume();
    }

    /** 창 포커스 이탈: PLAYING이면 자동 Pause (PAU-3) */
    public void onFocusLost() {
        pause();
    }

    // ── 조회 ─────────────────────────────────
    public GameState getState() {
        return state;
    }

    public GameMode getMode() {
        return mode;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public boolean isSpawnPending() { return spawnPending; }

    /**
     * 마지막으로 성공한 조작이 회전이면 true (ROT-7). 회전 성공 시 true, 좌우 이동·한 칸 이상 하강
     * (자동 낙하, Soft Drop, 실제로 내려간 Hard Drop) 성공 시 false. Lock 뒤 다음 Spawn 전까지는 Lock된 블록의 값을 유지한다.
     */
    public boolean isLastActionRotate() { return lastActionIsRotate; }

    /** 착지해 Lock 유예 타이머가 도는 중인지 (LCK-6) */
    public boolean isLockDelayActive() { return lockDelayActive; }

    /** 가장 최근 Lock이 T-스핀이었는지 (TSP-4). 새 게임은 NONE에서 시작하고 Lock할 때마다 다시 판정한다 */
    public TSpinType getLastTSpin() { return lastTSpin; }

    /** Compatibility hook; an in-flight lock keeps its original boundary. */
    public void setSpawnBoundary(SpawnBoundary boundary) {
        if (boundary == null) throw new IllegalArgumentException("spawnBoundary");
        if (pendingClear != null || spawnPending) {
            throw new IllegalStateException("lock settlement is in progress");
        }
        spawnBoundary = boundary;
    }

    /** Apply received attacks only after lock settlement and before the next Spawn. */
    public void applyPendingAttack(AttackRows attack) {
        if (!spawnPending || attackApplied || state != GameState.PLAYING || quitPending) {
            throw new IllegalStateException("attacks require a settled Spawn boundary");
        }
        boolean overflow = board.insertAttackRows(attack);
        attackApplied = true;
        if (overflow) gameOver();
    }

    /** Engine resumes after applying its pending rows; repeated/paused/ended calls do nothing. */
    public void resumeSpawn() {
        if (!spawnPending || state != GameState.PLAYING || quitPending) return;
        spawnPending = false;
        attackApplied = false;
        spawnNextBlock();
        if (state == GameState.PLAYING) listener.onChanged(snapshot());
    }

    public GameSnapshot snapshot() {
        Block next = nextQueue == null ? null : nextQueue.peek(0).copy();
        return new GameSnapshot(
                board.copyCells(),
                currentBlock == null ? null : currentBlock.copy(),
                next,
                score,
                levelPolicy.getLevel(),
                lines,
                state,
                mode,
                difficulty,
                state == GameState.CLEARING ? pendingClear.flashCells() : List.of(), // ANI-5: CLEARING이 아니면 빈 목록
                mode == GameMode.ITEM ? 10 - lines % 10 : 0,
                modifiers.effects(),
                board.copyItemCells(),
                lastTSpin,
                held == null ? null : held.copy(),
                peekNext(),
                ghostOf(currentBlock));
    }

    /** CLEARING 중 회전은 다음 블록에 한 번만 적용한다. 마지막 방향이 이긴다 (BUF-1) */
    private boolean bufferRotation(boolean counterClockwise) {
        if (!modernFeatures || state != GameState.CLEARING) return false;
        bufferRotate = true;
        bufferRotateCcw = counterClockwise;
        return true;
    }

    private void applyRotation(boolean counterClockwise) {
        int kick = counterClockwise
                ? rotationPolicy.tryRotateCounterClockwise(board, currentBlock)
                : rotationPolicy.tryRotate(board, currentBlock);
        if (kick < 0) return;
        lastActionIsRotate = true;
        lastKickIndex = kick;
        onMovedOrRotated();
        listener.onChanged(snapshot());
    }

    private void clearInputBuffer() {
        bufferRotate = false;
        bufferRotateCcw = false;
        bufferHold = false;
        bufferHardDrop = false;
    }

    /** 적용 순서는 홀드 → 회전 → 하드 드롭. 각각 최대 한 번 (BUF-1) */
    private void applyBufferedInputs() {
        if (!modernFeatures || applyingBuffer) return;
        boolean rotate = bufferRotate;
        boolean counterClockwise = bufferRotateCcw;
        boolean hold = bufferHold;
        boolean drop = bufferHardDrop;
        clearInputBuffer();
        if (!rotate && !hold && !drop) return;
        applyingBuffer = true;
        try {
            if (state != GameState.PLAYING || currentBlock == null) return;
            if (hold) hold();
            if (state != GameState.PLAYING || currentBlock == null) return;
            if (rotate) applyRotation(counterClockwise);
            if (drop && state == GameState.PLAYING && currentBlock != null) hardDrop();
        } finally {
            applyingBuffer = false;
        }
    }

    private List<Block> peekNext() {
        if (nextQueue == null) return List.of();
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < nextQueue.previewCount(); i++) blocks.add(nextQueue.peek(i).copy());
        return blocks;
    }

    /** 모던 착지 미리보기. 이미 그 자리이거나 무게추면 그리지 않는다 (GST-1) */
    private Block ghostOf(Block block) {
        if (!modernFeatures || block == null || block.isWeight()) return null;
        int distance = board.getDropDistance(block);
        if (distance <= 0) return null;
        Block ghost = block.copy();
        ghost.setPosition(block.getRow() + distance, block.getCol());
        return ghost;
    }

    /** 홀드 칸에는 초기 회전으로 넣는다. 아이템 칸도 같이 돌아간다 (HLD-2) */
    private static Block normalized(Block block) {
        Block copy = block.copy();
        while (copy.getRotation() != 0) copy.rotateCounterClockwise();
        return copy;
    }

    // ── 명세 4장 파이프라인 (내부) ────────────
    // 좌우 이동. 실패하면 아무것도 바꾸지 않고 알리지도 않는다 (§3.1)
    private void shift(int deltaCol) {
        if (crushing) return; // ITW-4: 분쇄 모드의 무게추는 좌우로 움직이지 않는다
        int row = currentBlock.getRow();
        int col = currentBlock.getCol() + deltaCol;
        if (!board.canPlace(currentBlock, row, col)) return;

        currentBlock.setPosition(row, col);
        lastActionIsRotate = false;
        onMovedOrRotated();
        listener.onChanged(snapshot());
    }

    /**
     * Tick과 Soft Drop 공통. Lock Delay가 없으면 하강 실패 즉시 Lock (LCK-2).
     * Lock Delay가 있으면 바닥에 닿는 순간 유예 타이머를 시작하고, 유예가 끝나 Tick이 왔을 때도 못 내려가면 Lock한다 (LCK-6).
     * timerFired: 타이머(Tick)가 부른 것인지. Soft Drop은 유예 중 Lock을 앞당기지 않는다.
     */
    private void descendOrLock(boolean timerFired) {
        boolean descended = tryDescend();
        if (descended) lastActionIsRotate = false;

        if (!usesLockDelay()) {
            if (!descended) lockCurrentBlock();
        } else if (descended) {
            if (isGrounded()) startLockDelay(); // 방금 바닥에 닿았다
        } else if (!lockDelayActive) {
            startLockDelay();                   // 닿아 있었지만 유예가 아직 시작되지 않았다 (예: 닿은 채로 Spawn)
        } else if (timerFired) {
            lockCurrentBlock();                 // 유예 시간이 다 지났다
        }
        if (state == GameState.PLAYING) listener.onChanged(snapshot());
    }

    // 무게추는 분쇄 규칙(ITW)을 그대로 따르도록 1차처럼 즉시 Lock한다
    private boolean usesLockDelay() {
        return lockPolicy.hasDelay() && !currentBlock.isWeight();
    }

    private boolean isGrounded() {
        return !board.canPlace(currentBlock, currentBlock.getRow() + 1, currentBlock.getCol());
    }

    private void startLockDelay() {
        lockDelayActive = true;
        clock.start(lockPolicy.lockDelayMillis());
    }

    /**
     * 좌우 이동·회전 성공 직후 (LCK-6). 유예 중이면 남은 횟수가 있을 때만 타이머를 처음부터 다시 센다.
     * 이동·회전으로 바닥에서 벗어나면 유예를 끝내고 낙하 타이머로 돌아가며, 공중에서 움직여 바닥에 닿으면 유예를 시작한다.
     */
    private void onMovedOrRotated() {
        if (!usesLockDelay()) return;
        boolean grounded = isGrounded();
        if (!lockDelayActive) {
            if (grounded) startLockDelay();
            return;
        }
        if (!grounded) {
            lockDelayActive = false;
            lockResets++;
            clock.start(currentFallIntervalMillis);
        } else if (lockResets < lockPolicy.maxResets()) {
            lockResets++;
            clock.start(lockPolicy.lockDelayMillis());
        }
    }

    private void resetLockState() {
        lockDelayActive = false;
        lockResets = 0;
        lastActionIsRotate = false;
        lastKickIndex = -1;
    }

    /** 성공하면 한 칸 내리고 +Level (SCR-1). 실패하면 아무것도 바꾸지 않는다 (SCR-4) */
    private boolean tryDescend() {
        if (currentBlock.isWeight()) return tryDescendWeight();

        int row = currentBlock.getRow() + 1;
        int col = currentBlock.getCol();
        if (!board.canPlace(currentBlock, row, col)) return false;

        currentBlock.setPosition(row, col);
        score += scoringPolicy.softDropScore(levelPolicy.getLevel());
        return true;
    }

    /**
     * 무게추의 하강 시도 (ITW-3~5, ITW-7). 점수는 없다.
     * 첫 실패 순간부터 분쇄 모드가 되고, 그 순간에도 바로 한 칸 내려가며 겹치는 고정 칸을 지운다.
     * 바닥에 닿아 더 내려갈 수 없으면 false (이후 Lock으로 고정된다).
     */
    private boolean tryDescendWeight() {
        int row = currentBlock.getRow() + 1;
        int col = currentBlock.getCol();
        if (!crushing) {
            if (board.canPlace(currentBlock, row, col)) {
                currentBlock.setPosition(row, col);
                return true;
            }
            crushing = true;
        }

        if (!board.canPlaceThrough(currentBlock, row, col)) return false;
        board.clearUnder(currentBlock, row, col);
        currentBlock.setPosition(row, col);
        return true;
    }

    /** 무게추 Hard Drop: 분쇄 여부와 관계없이 바닥까지 내려가며 지나는 칸을 모두 지운다 (ITW-6) */
    private void crushToBottom() {
        crushing = true;
        while (board.canPlaceThrough(currentBlock, currentBlock.getRow() + 1, currentBlock.getCol())) {
            currentBlock.setPosition(currentBlock.getRow() + 1, currentBlock.getCol());
            board.clearUnder(currentBlock, currentBlock.getRow(), currentBlock.getCol());
        }
    }

    /** 한 번의 Lock에서 지워질 줄 R (§5 L2). 아이템이 지우는 칸 C는 아이템 구현 때 여기에 더한다 */
    private record ClearPlan(List<Integer> rows, Set<Cell> cells, AttackRows attack) {
        // Existing scoring audit constructs the single-player plan reflectively.
        private ClearPlan(List<Integer> rows, Set<Cell> cells) {
            this(rows, cells, AttackRows.NONE);
        }
        boolean isEmpty() {
            return rows.isEmpty() && cells.isEmpty();
        }

        int rowCount() {
            return rows.size();
        }

        /** 흰색으로 깜빡일 보이는 칸 (ANI-1). 지울 줄의 10칸과 아이템이 지울 칸. 숨은 줄은 그리지 않는다 */
        List<Cell> flashCells() {
            List<Cell> flash = new ArrayList<>();
            for (int row : rows) {
                if (row < 0) continue;
                for (int col = 0; col < Board.WIDTH; col++) flash.add(new Cell(row, col));
            }
            for (Cell cell : cells) {
                if (cell.row() >= 0) flash.add(cell);
            }
            return flash;
        }
    }

    /** §5 LOCK. Level은 L6과 다음 Spawn의 S3에서만 바뀐다 (PIPE-1) */
    private void lockCurrentBlock() {
        lockDelayActive = false;
        lastTSpin = TSpinDetector.detect(board, currentBlock, lastActionIsRotate, lastKickIndex); // 보드에 기록하기 전 (TSP-2)
        modifiers.beginLock();
        Block placed = placeCurrentBlock();           // L1
        ClearPlan plan = collectClearTargets(placed); // L2
        if (plan.isEmpty()) {                         // ANI-4: 지울 대상이 없으면 CLEARING을 거치지 않는다
            finishLock(plan);
            return;
        }

        pendingClear = plan;                          // L3: 150 ms 뒤 tick()에서 finishClearing()이 이어서 수행
        state = GameState.CLEARING;
        clock.start(CLEAR_ANIMATION_MILLIS);
        listener.onChanged(snapshot());
    }

    /** CLEARING의 150 ms가 지난 뒤 L4부터 이어서 수행하고 Spawn한다 */
    private void finishClearing() {
        ClearPlan plan = pendingClear;
        pendingClear = null;
        state = GameState.PLAYING; // Spawn 충돌이면 PLAYING -> GAME_OVER 전이가 되도록 먼저 전환
        finishLock(plan);
        if (state == GameState.PLAYING) listener.onChanged(snapshot());
    }

    /** L4~L6 후 Spawn */
    private void finishLock(ClearPlan plan) {
        lastClearedRows = plan.rows().stream().filter(row -> row >= 0 && row < Board.HEIGHT).mapToInt(Integer::intValue).toArray();
        int clearedCells = applyClear(plan);          // L4
        awardClearScore(plan, clearedCells);          // L5
        updateCounters(plan);                         // L6
        modifiers.finishLock();                       // L8: 이번에 획득한 D는 유지
        if (spawnBoundary == SpawnBoundary.IMMEDIATE) {
            spawnNextBlock(); // preserve the existing single-player path
            return;
        }
        spawnPending = true;
        attackApplied = false;
        clock.stop();
        spawnBoundary.onLockResolved(new LockResult(plan.rowCount(), plan.attack()));
        if (spawnPending && state == GameState.PLAYING && !spawnBoundary.deferSpawn()) resumeSpawn();
    }

    /** L1: 현재 블록 칸을 보드에 기록 */
    private Block placeCurrentBlock() {
        if (!board.tryPlaceBlock(currentBlock))
            throw new IllegalStateException("current block is not at a placeable position");

        java.util.ArrayList<Cell> placed = new java.util.ArrayList<>();
        int[][] shape = currentBlock.getShape();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) placed.add(new Cell(currentBlock.getRow() + r, currentBlock.getCol() + c));
            }
        }
        lastPlacedCells = java.util.List.copyOf(placed);
        Block placedBlock = currentBlock;
        currentBlock = null; // 보드에 들어간 블록을 스냅샷이 두 번 그리지 않도록
        return placedBlock;
    }

    /**
     * L2: 지울 대상 수집. 보드는 아직 바꾸지 않는다.
     * R = 완성 행 ∪ 아이템이 지우려는 행(같은 행은 한 번만), C = 아이템이 지우려는 칸 − R에 속한 칸 (PIPE-5, ITL-3)
     */
    private ClearPlan collectClearTargets(Block placed) {
        Set<Integer> rows = new TreeSet<>(board.findFullRows());
        Set<Cell> cells = new HashSet<>();
        collectItemTargets(placed, rows, cells);

        rows.removeIf(row -> row < -Board.MARGIN || row >= Board.HEIGHT); // 보드 밖 행은 무시
        cells.removeIf(cell -> rows.contains(cell.row()));
        List<Integer> deletedRows = new ArrayList<>(rows);
        AttackRows attack = spawnBoundary == SpawnBoundary.IMMEDIATE ? AttackRows.NONE
                : AttackRowExtractor.extract(board.copyCells(), deletedRows, placed);
        return new ClearPlan(deletedRows, cells, attack);
    }

    // Lock한 블록의 아이템 칸마다 효과를 불러 지울 행·칸을 모은다. 아이템 칸의 보드 좌표 = 블록 위치 + 격자 위치
    private void collectItemTargets(Block placed, Set<Integer> rows, Set<Cell> cells) {
        int[][] shape = placed.getShape();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 0) continue;

                ItemType item = placed.itemCellAt(r, c).orElse(null);
                if (item == null) continue;

                ItemResult result = item.effect().onLock(board, placed.getRow() + r, placed.getCol() + c, modifiers);
                rows.addAll(result.rows());
                cells.addAll(result.cells());
            }
        }
    }

    /** L4: C의 칸을 비우고(위 칸은 내려오지 않음) R의 행을 삭제해 위 행을 내린다 (CLR-2). 실제로 지운 C의 칸 수를 돌려준다 */
    private int applyClear(ClearPlan plan) {
        int clearedCells = board.clearCells(plan.cells());
        board.removeRows(plan.rows());
        return clearedCells;
    }

    /** L5: 줄 삭제 점수 + 아이템이 지운 고정 칸당 10점 (§8.6) */
    private void awardClearScore(ClearPlan plan, int clearedCells) {
        score += scoringPolicy.lineClearBonus(plan.rowCount()) * modifiers.scoreMultiplier();
        score += (long) clearedCells * CELL_CLEAR_SCORE;
    }

    /** L6: 누적 줄과 Level */
    private void updateCounters(ClearPlan plan) {
        int previousMilestone = lines / 10;
        lines += plan.rowCount();
        levelPolicy.onLinesCleared(plan.rowCount());
        // L7: 현재 Next는 유지하고, 곧 채워질 새 Next 한 개에만 예약한다.
        if (mode == GameMode.ITEM && lines / 10 > previousMilestone) {
            ((ItemBlockGenerator) generator).requestItem();
        }
    }

    private void spawnNextBlock() {
        spawnPiece(nextQueue.pop(), true);                                         // S1
        if (state == GameState.PLAYING) applyBufferedInputs();
    }

    /** freshPiece면 홀드 사용 횟수를 되돌린다. 홀드에서 꺼낸 블록은 이미 쓴 것으로 남긴다 */
    private void spawnPiece(Block block, boolean freshPiece) {
        int row = spawnPolicy.getSpawnRow(block);
        int col = spawnPolicy.getSpawnColumn(block);
        if (!board.canPlace(block, row, col)) {                                    // S2
            gameOver(); // SPN-4: 블록을 놓지 않고 카운터·Level·타이머도 바꾸지 않는다
            return;
        }
        block.setPosition(row, col);
        currentBlock = block;
        crushing = false;
        if (freshPiece) holdUsed = false;
        resetLockState();
        levelPolicy.onBlockSpawned();                                              // S3
        modifiers.onSpawn();
        currentFallIntervalMillis = (int) Math.round(levelPolicy.fallIntervalMillis() * modifiers.fallMultiplier());
        if (usesLockDelay() && isGrounded()) startLockDelay();                     // 쌓인 블록에 닿은 채로 나왔다 (LCK-6)
        else clock.start(currentFallIntervalMillis);                               // S4
    }

    private void gameOver() {
        spawnPending = false;
        attackApplied = false;
        state = GameState.GAME_OVER;
        clock.stop();
        listener.onGameOver(snapshot());
    }

    // ── 레이드 훅. 싱글·대전은 호출하지 않는다 ──

    /** 직전 Lock에서 지운 보이는 행. 지우지 않았으면 빈 배열 */
    public int[] lastClearedRows() {
        return lastClearedRows.clone();
    }

    /** 직전 Lock에서 보드에 기록한 칸. 숨은 줄 좌표가 포함될 수 있다 */
    public java.util.List<Cell> lastPlacedCells() {
        return lastPlacedCells;
    }

    /** 다음 Spawn 조각부터 강제 교체. 저주 블록이 S/Z를 넣을 때 쓴다 */
    public void forceUpcoming(Block... pieces) {
        if (nextQueue == null) throw new IllegalStateException("not started");
        if (pieces == null) throw new IllegalArgumentException("pieces");
        nextQueue.replaceFront(java.util.List.of(pieces));
    }

    /** index번째 대기 블록 종류. 큐 밖이면 null */
    public tetris.component.block.BlockType peekUpcomingType(int index) {
        if (nextQueue == null || index < 0) return null;
        try {
            return nextQueue.peek(index).getType();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** NEXT 미리보기를 늘린다. 최대 cap */
    public void expandPreview(int additional, int cap) {
        if (nextQueue == null) return;
        nextQueue.expandPreview(additional, cap);
    }

    public int previewCount() {
        return nextQueue == null ? previewCount : nextQueue.previewCount();
    }

    /** 다음 Spawn의 낙하 배율. 1.0이면 기존과 같다 */
    public void setGravityMultiplier(double multiplier) {
        modifiers.setGravityMultiplier(multiplier);
    }

    public double gravityMultiplier() {
        return modifiers.gravityMultiplier();
    }

    /**
     * 빈 고정 칸에 값을 쓴다. 떨어지는 블록이 차지한 칸·벽·범위 밖은 무시한다.
     * 기둥·덩어리 소환이 회색 칸(공격 칸 값)을 심을 때 쓴다.
     */
    public boolean writeFixedCell(int row, int col, int value) {
        if (row < 0 || row >= Board.HEIGHT || col < 0 || col >= Board.WIDTH) return false;
        if (value == Board.EMPTY) return false;
        if (!board.isEmpty(row, col)) return false;
        if (currentBlock != null && covers(currentBlock, row, col)) return false;
        try {
            var method = Board.class.getDeclaredMethod("setCell", int.class, int.class, int.class, ItemType.class);
            method.setAccessible(true);
            method.invoke(board, row, col, value, null);
            return true;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("고정 칸을 쓸 수 없습니다", ex);
        }
    }

    /** 보이는 행을 지우고 위 칸을 내린다. 떨어지는 블록이 겹치면 위로 올린다 */
    public void removeVisibleRows(int... rows) {
        if (rows == null || rows.length == 0) return;
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (int row : rows) list.add(row);
        board.removeRows(list);
        liftCurrentIfColliding();
    }

    /**
     * 상단 초과로 쓰러진 참가자를 빈 보드로 되돌리고 다음 조각을 꺼낸다.
     * 점수·줄·생성기 순서는 유지한다. GAME_OVER가 아니면 무시한다.
     */
    public void reviveFromTopOut() {
        if (state != GameState.GAME_OVER) return;
        board.reset();
        currentBlock = null;
        quitPending = false;
        spawnPending = false;
        attackApplied = false;
        pendingClear = null;
        pausedDuringClearing = false;
        crushing = false;
        resetLockState();
        held = null;
        holdUsed = false;
        clearInputBuffer();
        lastTSpin = TSpinType.NONE;
        lastClearedRows = new int[0];
        lastPlacedCells = java.util.List.of();
        modifiers = new GameModifiers();
        state = GameState.PLAYING;
        spawnNextBlock();
        if (state == GameState.PLAYING) listener.onChanged(snapshot());
    }

    private static boolean covers(Block block, int row, int col) {
        int[][] shape = block.getShape();
        int localRow = row - block.getRow();
        int localCol = col - block.getCol();
        return localRow >= 0 && localRow < shape.length && localCol >= 0 && localCol < shape[localRow].length
                && shape[localRow][localCol] != 0;
    }

    private void liftCurrentIfColliding() {
        if (currentBlock == null) return;
        int guard = 0;
        while (guard++ < Board.HEIGHT + Board.MARGIN
                && !board.canPlace(currentBlock, currentBlock.getRow(), currentBlock.getCol())) {
            currentBlock.setPosition(currentBlock.getRow() - 1, currentBlock.getCol());
        }
    }
}
