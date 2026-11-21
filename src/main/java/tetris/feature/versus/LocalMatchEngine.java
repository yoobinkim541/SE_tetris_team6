package tetris.feature.versus;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import tetris.component.block.Block;
import tetris.feature.game.*;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.rule.BagBlockGenerator;
import tetris.feature.item.Cell;
import tetris.feature.item.ItemType;
import tetris.feature.setting.AutoRepeat;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.*;

/**
 * EDT-confined authority for one match (CLASSIC, ITEM, TIMED). A pulse settles both players before deciding a result.
 * TIMED: the deadline freezes while PAUSED, inputs stamped at or after it are ignored, due work at the deadline
 * is not executed, and a top-out decided in the same pulse wins over TIME_UP (D-05, D-06).
 */
public final class LocalMatchEngine implements MatchEngine {
    private final MatchClock time;
    private final Function<PlayerId, BlockGenerator> generators;
    private final GameSession[] sessions = new GameSession[2];
    private final DueTimeClock[] clocks = new DueTimeClock[2];
    private final PendingAttackQueue[] pending = {new PendingAttackQueue(), new PendingAttackQueue()};
    private final AttackRows[] outgoing = {AttackRows.NONE, AttackRows.NONE};
    private final java.util.List<EnumSet<GameAction>> held = java.util.List.of(
            EnumSet.noneOf(GameAction.class), EnumSet.noneOf(GameAction.class));
    private final AutoRepeat[] repeats = {new AutoRepeat(), new AutoRepeat()};
    private final long[] lastSequence = {-1, -1};
    private final ArrayDeque<PlayerCommand> commands = new ArrayDeque<>();
    private long pulseTime;
    private long revision;
    private String matchId;
    private MatchPhase phase;
    private MatchResult result;
    private MatchSnapshot snapshot;
    private boolean closed;
    private boolean timed;
    private long deadline = Long.MAX_VALUE;
    private long pausedAt;
    private boolean modern;
    private final int[] tSpinLocks = new int[2];

    public LocalMatchEngine() { this(MatchClock.system(), null); }

    /** Generator injection allows reproducible games without replacing sessions or board rules. */
    public LocalMatchEngine(MatchClock time, Function<PlayerId, BlockGenerator> generators) {
        this.time = Objects.requireNonNull(time);
        this.generators = generators;
    }

    @Override public void start(MatchConfig config) {
        start(config, UUID.randomUUID().toString());
    }

    /** P2P host: the lobby already announced matchId in START, so the engine must publish under the same id. */
    public void start(MatchConfig config, String matchId) {
        if (closed) throw new IllegalStateException("engine closed");
        Objects.requireNonNull(config);
        Objects.requireNonNull(matchId);
        if (matchId.isBlank()) throw new IllegalArgumentException("blank matchId");
        for (var session : sessions) if (session != null) session.end();
        pulseTime = time.nowMillis(); revision = 0; this.matchId = matchId;
        phase = MatchPhase.PLAYING; result = null; commands.clear(); Arrays.fill(lastSequence, -1);
        timed = config.mode().timed();
        deadline = timed ? pulseTime + config.timeLimitMillis() : Long.MAX_VALUE;
        modern = config.controlStyle().modern();
        GameMode sessionMode = config.mode().itemsEnabled() ? GameMode.ITEM : GameMode.CLASSIC;
        for (PlayerId player : PlayerId.values()) {
            int i = player.ordinal(); held.get(i).clear(); pending[i].clear(); outgoing[i] = AttackRows.NONE;
            tSpinLocks[i] = 0;
            clocks[i] = new DueTimeClock(() -> pulseTime);
            BlockGenerator generator = generators == null
                    ? new BagBlockGenerator(config.difficulty().weights(), new Random()) : generators.apply(player);
            sessions[i] = new GameSession(sessionMode, config.difficulty(), generator, clocks[i], new GameListener() {
                @Override public void onChanged(GameSnapshot state) { }
                @Override public void onGameOver(GameSnapshot state) { } // collected after both Spawn attempts
            }, new SpawnBoundary() {
                @Override public void onLockResolved(LockResult lock) {
                    outgoing[i] = lock.attack();
                    if (sessions[i].getLastTSpin().isTSpin()) tSpinLocks[i]++;
                }
                @Override public boolean deferSpawn() { return true; }
            }, config.controlStyle());
            sessions[i].start();
            repeats[i].clear();
        }
        updateSnapshot();
    }

    @Override public void submit(PlayerCommand command) {
        Objects.requireNonNull(command);
        if (closed || phase == MatchPhase.FINISHED) return;
        if (snapshot == null) throw new IllegalStateException("match not started");
        int i = command.playerId().ordinal();
        if (command.sequence() <= lastSequence[i]) return;
        // D-05: anything that reaches the authority at or after the deadline is ignored.
        if (timed && phase == MatchPhase.PLAYING && time.nowMillis() >= deadline) {
            if (!command.pressed()) {
                held.get(i).remove(command.action());
                repeats[i].release(command.action());
            }
            return;
        }
        lastSequence[i] = command.sequence();
        // Latch physical transitions before deferring gameplay. Otherwise a duplicate
        // press followed by release could be replayed as a fresh press next pulse.
        // 모던은 누르고 있는 동안의 추가 press를 버리고 엔진 타이머가 반복한다 (DAS-1).
        if (!command.pressed()) {
            held.get(i).remove(command.action());
            repeats[i].release(command.action());
        } else if (modern && command.action().isRepeatable()) {
            if (!held.get(i).add(command.action())) return;
            repeats[i].press(command.action(), time.nowMillis());
        } else if (!command.action().isRepeatable() && !held.get(i).add(command.action())) return;
        commands.addLast(command);
    }

    @Override public void advance(long nowMillis) {
        if (closed || phase == MatchPhase.FINISHED) return;
        if (snapshot == null) throw new IllegalStateException("match not started");
        if (nowMillis < pulseTime) throw new IllegalArgumentException("match time must be monotonic");
        // Execute due work in time order. start()/stop() inside tick retain their timing semantics.
        if (phase == MatchPhase.PLAYING) {
            while (true) {
                long due = Long.MAX_VALUE;
                for (var clock : clocks) if (clock.isDue(nowMillis)) due = Math.min(due, clock.dueAt());
                if (due == Long.MAX_VALUE || due >= deadline) break;
                pulseTime = due;
                for (int i = 0; i < 2; i++) if (clocks[i].isDue(due)) {
                    long fired = clocks[i].dueAt(); sessions[i].tick(); clocks[i].onTicked(fired);
                }
            }
        }
        pulseTime = nowMillis;
        var deferred = new ArrayDeque<PlayerCommand>();
        boolean[] blocked = new boolean[2];
        while (!commands.isEmpty()) {
            PlayerCommand command = commands.removeFirst(); int i = command.playerId().ordinal();
            GameAction action = command.action();
            if (!command.pressed()) {
                if (blocked[i]) deferred.addLast(command);
                continue;
            }
            if (action == GameAction.PAUSE || action == GameAction.QUIT) {
                deferred.clear(); Arrays.fill(blocked, false);
                if (action == GameAction.QUIT) {
                    finish(MatchResult.win(command.playerId().opponent(), MatchResult.EndReason.FORFEIT)); break;
                }
                togglePause();
                continue;
            }
            if (phase != MatchPhase.PLAYING) continue;
            if (blocked[i] || sessions[i].isSpawnPending()) {
                blocked[i] = true; deferred.addLast(command); continue;
            }
            if (sessions[i].getState() == GameState.CLEARING && modern && isBuffered(action)) {
                apply(i, action);
                continue;
            }
            if (sessions[i].getState() != GameState.PLAYING) continue;
            apply(i, action);
        }
        if (phase != MatchPhase.FINISHED) {
            commands.addAll(deferred);
            if (phase == MatchPhase.PLAYING) settleSpawnBoundaries();
            if (modern && phase == MatchPhase.PLAYING) repeatHeldKeys();
            // D-06: a top-out settled in this pulse already finished the match before the deadline check.
            if (phase == MatchPhase.PLAYING && pulseTime >= deadline) finishTimeUp();
            if (phase != MatchPhase.FINISHED) { revision++; updateSnapshot(); }
        }
    }

    private void settleSpawnBoundaries() {
        // Publish both attacks before consuming either queue; simultaneous attacks never cancel.
        for (int i = 0; i < 2; i++) {
            pending[1 - i].append(outgoing[i]); outgoing[i] = AttackRows.NONE;
        }
        for (int i = 0; i < 2; i++) if (sessions[i].isSpawnPending() && sessions[i].getState() == GameState.PLAYING) {
            sessions[i].applyPendingAttack(pending[i].drain());
        }
        for (var session : sessions) session.resumeSpawn();
        boolean oneLost = sessions[0].getState() == GameState.GAME_OVER;
        boolean twoLost = sessions[1].getState() == GameState.GAME_OVER;
        if (oneLost || twoLost) finish(oneLost && twoLost ? MatchResult.draw(MatchResult.EndReason.TOPPED_OUT)
                : MatchResult.win(oneLost ? PlayerId.TWO : PlayerId.ONE, MatchResult.EndReason.TOPPED_OUT));
    }

    private void togglePause() {
        // Keep the shared-key latch until release; drop gameplay held keys at the pause boundary.
        for (var keys : held) keys.removeIf(a -> a != GameAction.PAUSE && a != GameAction.QUIT);
        for (var repeat : repeats) repeat.clear();
        if (phase == MatchPhase.PLAYING) {
            if (pulseTime >= deadline) return; // the match ends in this pulse; nothing left to freeze
            for (var session : sessions) session.pause(); phase = MatchPhase.PAUSED; pausedAt = pulseTime;
        } else if (phase == MatchPhase.PAUSED) {
            for (var session : sessions) session.resume(); phase = MatchPhase.PLAYING;
            if (timed) deadline += pulseTime - pausedAt;
        }
    }

    /** Only scores settled before the deadline exist; an unfinished CLEARING has not added its bonus yet. */
    private void finishTimeUp() {
        long one = sessions[0].snapshot().score(), two = sessions[1].snapshot().score();
        finish(one == two ? MatchResult.draw(MatchResult.EndReason.TIME_UP)
                : MatchResult.win(one > two ? PlayerId.ONE : PlayerId.TWO, MatchResult.EndReason.TIME_UP));
    }

    private long remainingMillis() {
        if (!timed) return -1;
        long reference = phase == MatchPhase.PAUSED ? pausedAt : pulseTime;
        return Math.max(0, deadline - reference);
    }

    private void finish(MatchResult outcome) {
        phase = MatchPhase.FINISHED; result = outcome; commands.clear();
        for (var clock : clocks) clock.stop();
        for (var keys : held) keys.clear();
        for (var repeat : repeats) repeat.clear();
        revision++; updateSnapshot();
    }

    private void updateSnapshot() {
        snapshot = new MatchSnapshot(matchId, revision, view(0), view(1), remainingMillis(), phase, result);
    }

    private MatchSnapshot.PlayerView view(int i) {
        GameSnapshot state = sessions[i].snapshot(); int[][] cells = state.cells();
        int[][] items = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        for (var locked : state.lockedItems().entrySet()) {
            Cell cell = locked.getKey();
            if (cells[cell.row()][cell.col()] != 0) items[cell.row()][cell.col()] = locked.getValue().symbol();
        }
        Block block = state.currentBlock();
        if (block != null) {
            int[][] shape = block.getShape();
            for (int r = 0; r < shape.length; r++) for (int c = 0; c < shape[r].length; c++) {
                int row = block.getRow() + r, col = block.getCol() + c;
                if (shape[r][c] != 0 && row >= 0 && row < 20 && col >= 0 && col < 10) {
                    cells[row][col] = block.getType().cellValue();
                    ItemType item = block.itemCellAt(r, c).orElse(null);
                    items[row][col] = item == null ? 0 : item.symbol();
                }
            }
        }
        java.util.List<int[][]> previews = new java.util.ArrayList<>();
        for (Block preview : state.nextBlocks()) previews.add(shapeCells(preview));
        int[][] nextCells = previews.isEmpty() ? shapeCells(state.nextBlock()) : previews.get(0);
        return new MatchSnapshot.PlayerView(cells, nextCells, state.score(), pending[i].rows(), items,
                shapeCells(state.holdBlock()), ghostCells(state.ghostBlock()), previews, tSpinLocks[i]);
    }

    private void apply(int i, GameAction action) {
        switch (action) {
            case MOVE_LEFT -> sessions[i].moveLeft();
            case MOVE_RIGHT -> sessions[i].moveRight();
            case SOFT_DROP -> sessions[i].moveDown();
            case ROTATE -> sessions[i].rotateRight();
            case ROTATE_CCW -> sessions[i].rotateLeft();
            case HARD_DROP -> sessions[i].hardDrop();
            case HOLD -> sessions[i].hold();
            default -> throw new IllegalStateException("unexpected gameplay action");
        }
    }

    private static boolean isBuffered(GameAction action) {
        return action == GameAction.ROTATE || action == GameAction.ROTATE_CCW
                || action == GameAction.HOLD || action == GameAction.HARD_DROP;
    }

    private void repeatHeldKeys() {
        for (int i = 0; i < 2; i++) {
            boolean ready = sessions[i].getState() == GameState.PLAYING && !sessions[i].isSpawnPending();
            for (GameAction action : repeats[i].poll(pulseTime)) {
                if (ready) apply(i, action);
            }
        }
    }

    private static int[][] shapeCells(Block block) {
        if (block == null) return new int[0][];
        int[][] shape = block.getShape();
        int[][] cells = new int[shape.length][];
        for (int r = 0; r < shape.length; r++) {
            cells[r] = new int[shape[r].length];
            for (int c = 0; c < shape[r].length; c++) cells[r][c] = shape[r][c] == 0 ? 0 : block.getType().cellValue();
        }
        return cells;
    }

    private static int[][] ghostCells(Block ghost) {
        int[][] grid = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        if (ghost == null) return grid;
        int[][] shape = ghost.getShape();
        for (int r = 0; r < shape.length; r++) for (int c = 0; c < shape[r].length; c++) {
            int row = ghost.getRow() + r, col = ghost.getCol() + c;
            if (shape[r][c] != 0 && row >= 0 && row < VersusRules.BOARD_ROWS && col >= 0 && col < VersusRules.BOARD_COLS)
                grid[row][col] = ghost.getType().cellValue();
        }
        return grid;
    }

    @Override public MatchSnapshot snapshot() {
        if (snapshot == null) throw new IllegalStateException("match not started");
        return snapshot;
    }

    @Override public void close() {
        if (closed) return; closed = true; commands.clear();
        for (var session : sessions) if (session != null) session.end();
        for (var keys : held) keys.clear();
    }
}
