package tetris.feature.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import tetris.feature.rule.BasicRotationPolicy;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.rule.ImmediateLockPolicy;
import tetris.feature.rule.ItemBlockGenerator;
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

    private final SpawnPolicy spawnPolicy = new SpawnPolicy();
    private final RotationPolicy rotationPolicy = new BasicRotationPolicy();
    private final LockPolicy lockPolicy = new ImmediateLockPolicy();
    private final LevelPolicy levelPolicy;
    private final ScoringPolicy scoringPolicy = new ScoringPolicy();
    private final GameClock clock;
    private final GameListener listener;
    private final GameMode mode;
    private final Difficulty difficulty;

    /** 일반 모드·NORMAL 난이도 */
    public GameSession(BlockGenerator generator, GameClock clock, GameListener listener) {
        this(GameMode.CLASSIC, Difficulty.NORMAL, generator, clock, listener);
    }

    /** 모드와 난이도는 한 판이 끝날 때까지 바뀌지 않는다 (DIF-5, MOD-2) */
    public GameSession(GameMode mode, Difficulty difficulty, BlockGenerator generator, GameClock clock, GameListener listener) {
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
        quitPending = false;
        stateBeforeQuit = null;
        pendingClear = null;
        pausedDuringClearing = false;
        crushing = false;
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
        stateBeforeQuit = null;
        pendingClear = null;
        pausedDuringClearing = false;
        state = GameState.ENDED;
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
        if (currentBlock.isWeight()) return; // ITW-1: 무게추는 회전하지 않는다
        if (rotationPolicy.rotate(board, currentBlock)) listener.onChanged(snapshot());
    }

    /** 내려갈 수 있는 만큼 한 번에 내리고 +칸수 x Level (SCR-2) 후 즉시 Lock. 점수는 Level이 바뀔 수 있는 Lock 전에 더한다 */
    public void hardDrop() {
        if (state != GameState.PLAYING) return;

        if (currentBlock.isWeight()) {
            crushToBottom(); // ITW-6, ITW-7: 점수 없음
        } else {
            int distance = board.getDropDistance(currentBlock);
            currentBlock.setPosition(currentBlock.getRow() + distance, currentBlock.getCol());
            score += scoringPolicy.hardDropScore(distance, levelPolicy.getLevel());
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
        if (state != GameState.PLAYING) return;
        descendOrLock();
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
            clock.start(currentFallIntervalMillis);
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

    public GameSnapshot snapshot() {
        return new GameSnapshot(
                board.copyCells(),
                currentBlock == null ? null : currentBlock.copy(),
                nextQueue == null ? null : nextQueue.peek(0).copy(),
                score,
                levelPolicy.getLevel(),
                lines,
                state,
                mode,
                difficulty,
                state == GameState.CLEARING ? pendingClear.flashCells() : List.of(), // ANI-5: CLEARING이 아니면 빈 목록
                mode == GameMode.ITEM ? 10 - lines % 10 : 0,
                modifiers.effects());
    }

    // ── 명세 4장 파이프라인 (내부) ────────────
    // 좌우 이동. 실패하면 아무것도 바꾸지 않고 알리지도 않는다 (§3.1)
    private void shift(int deltaCol) {
        if (crushing) return; // ITW-4: 분쇄 모드의 무게추는 좌우로 움직이지 않는다
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
    private record ClearPlan(List<Integer> rows, Set<Cell> cells) {
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
        int clearedCells = applyClear(plan);          // L4
        awardClearScore(plan, clearedCells);          // L5
        updateCounters(plan);                         // L6
        modifiers.finishLock();                       // L8: 이번에 획득한 D는 유지
        spawnNextBlock();
    }

    /** L1: 현재 블록 칸을 보드에 기록 */
    private Block placeCurrentBlock() {
        if (!board.tryPlaceBlock(currentBlock))
            throw new IllegalStateException("current block is not at a placeable position");

        Block placed = currentBlock;
        currentBlock = null; // 보드에 들어간 블록을 스냅샷이 두 번 그리지 않도록
        return placed;
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
        return new ClearPlan(new ArrayList<>(rows), cells);
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
        Block block = nextQueue.pop();                                             // S1
        int row = spawnPolicy.getSpawnRow(block);
        int col = spawnPolicy.getSpawnColumn(block);

        if (!board.canPlace(block, row, col)) {                                    // S2
            gameOver(); // SPN-4: 블록을 놓지 않고 카운터·Level·타이머도 바꾸지 않는다
            return;
        }

        block.setPosition(row, col);
        currentBlock = block;
        crushing = false;
        levelPolicy.onBlockSpawned();                                              // S3
        modifiers.onSpawn();
        currentFallIntervalMillis = (int) Math.round(levelPolicy.fallIntervalMillis() * modifiers.fallMultiplier());
        clock.start(currentFallIntervalMillis);                                    // S4
    }

    private void gameOver() {
        state = GameState.GAME_OVER;
        clock.stop();
        listener.onGameOver(snapshot());
    }
}
