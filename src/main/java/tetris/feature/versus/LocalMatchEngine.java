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
import tetris.feature.rule.WeightedBlockGenerator;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.*;

/** EDT-confined authority for one local classic match. A pulse settles both players before deciding a result. */
public final class LocalMatchEngine implements MatchEngine {
    private final MatchClock time;
    private final Function<PlayerId, BlockGenerator> generators;
    private final GameSession[] sessions = new GameSession[2];
    private final DueTimeClock[] clocks = new DueTimeClock[2];
    private final PendingAttackQueue[] pending = {new PendingAttackQueue(), new PendingAttackQueue()};
    private final AttackRows[] outgoing = {AttackRows.NONE, AttackRows.NONE};
    private final java.util.List<EnumSet<GameAction>> held = java.util.List.of(
            EnumSet.noneOf(GameAction.class), EnumSet.noneOf(GameAction.class));
    private final long[] lastSequence = {-1, -1};
    private final ArrayDeque<PlayerCommand> commands = new ArrayDeque<>();
    private long pulseTime;
    private long revision;
    private String matchId;
    private MatchPhase phase;
    private MatchResult result;
    private MatchSnapshot snapshot;
    private boolean closed;

    public LocalMatchEngine() { this(MatchClock.system(), null); }

    /** Generator injection allows reproducible games without replacing sessions or board rules. */
    public LocalMatchEngine(MatchClock time, Function<PlayerId, BlockGenerator> generators) {
        this.time = Objects.requireNonNull(time);
        this.generators = generators;
    }

    @Override public void start(MatchConfig config) {
        if (closed) throw new IllegalStateException("engine closed");
        Objects.requireNonNull(config);
        if (config.mode() != BattleMode.CLASSIC) throw new IllegalArgumentException("only CLASSIC is available in Sprint 8");
        for (var session : sessions) if (session != null) session.end();
        pulseTime = time.nowMillis(); revision = 0; matchId = UUID.randomUUID().toString();
        phase = MatchPhase.PLAYING; result = null; commands.clear(); Arrays.fill(lastSequence, -1);
        for (PlayerId player : PlayerId.values()) {
            int i = player.ordinal(); held.get(i).clear(); pending[i].clear(); outgoing[i] = AttackRows.NONE;
            clocks[i] = new DueTimeClock(() -> pulseTime);
            BlockGenerator generator = generators == null
                    ? new WeightedBlockGenerator(config.difficulty().weights(), new Random()) : generators.apply(player);
            sessions[i] = new GameSession(GameMode.CLASSIC, config.difficulty(), generator, clocks[i], new GameListener() {
                @Override public void onChanged(GameSnapshot state) { }
                @Override public void onGameOver(GameSnapshot state) { } // collected after both Spawn attempts
            }, new SpawnBoundary() {
                @Override public void onLockResolved(LockResult lock) { outgoing[i] = lock.attack(); }
                @Override public boolean deferSpawn() { return true; }
            });
            sessions[i].start();
        }
        updateSnapshot();
    }

    @Override public void submit(PlayerCommand command) {
        Objects.requireNonNull(command);
        if (closed || phase == MatchPhase.FINISHED) return;
        if (snapshot == null) throw new IllegalStateException("match not started");
        int i = command.playerId().ordinal();
        if (command.sequence() <= lastSequence[i]) return;
        lastSequence[i] = command.sequence();
        // Latch physical transitions before deferring gameplay. Otherwise a duplicate
        // press followed by release could be replayed as a fresh press next pulse.
        if (!command.pressed()) held.get(i).remove(command.action());
        else if (!command.action().isRepeatable() && !held.get(i).add(command.action())) return;
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
                if (due == Long.MAX_VALUE) break;
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
            if (sessions[i].getState() != GameState.PLAYING) continue;
            switch (action) {
                case MOVE_LEFT -> sessions[i].moveLeft();
                case MOVE_RIGHT -> sessions[i].moveRight();
                case SOFT_DROP -> sessions[i].moveDown();
                case ROTATE -> sessions[i].rotateRight();
                case HARD_DROP -> sessions[i].hardDrop();
                default -> throw new IllegalStateException("unexpected gameplay action");
            }
        }
        if (phase != MatchPhase.FINISHED) {
            commands.addAll(deferred);
            if (phase == MatchPhase.PLAYING) settleSpawnBoundaries();
            revision++; updateSnapshot();
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
        if (phase == MatchPhase.PLAYING) {
            for (var session : sessions) session.pause(); phase = MatchPhase.PAUSED;
        } else if (phase == MatchPhase.PAUSED) {
            for (var session : sessions) session.resume(); phase = MatchPhase.PLAYING;
        }
    }

    private void finish(MatchResult outcome) {
        phase = MatchPhase.FINISHED; result = outcome; commands.clear();
        for (var clock : clocks) clock.stop();
        for (var keys : held) keys.clear();
        revision++; updateSnapshot();
    }

    private void updateSnapshot() {
        snapshot = new MatchSnapshot(matchId, revision, view(0), view(1), -1, phase, result);
    }

    private MatchSnapshot.PlayerView view(int i) {
        GameSnapshot state = sessions[i].snapshot(); int[][] cells = state.cells();
        Block block = state.currentBlock();
        if (block != null) {
            int[][] shape = block.getShape();
            for (int r = 0; r < shape.length; r++) for (int c = 0; c < shape[r].length; c++) {
                int row = block.getRow() + r, col = block.getCol() + c;
                if (shape[r][c] != 0 && row >= 0 && row < 20 && col >= 0 && col < 10) cells[row][col] = block.getType().cellValue();
            }
        }
        Block next = state.nextBlock();
        int[][] nextCells = next == null ? new int[0][] : next.getShape();
        if (next != null) {
            nextCells = Arrays.stream(nextCells).map(row -> Arrays.stream(row)
                    .map(value -> value == 0 ? 0 : next.getType().cellValue()).toArray()).toArray(int[][]::new);
        }
        return new MatchSnapshot.PlayerView(cells, nextCells, state.score(), pending[i].rows());
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
