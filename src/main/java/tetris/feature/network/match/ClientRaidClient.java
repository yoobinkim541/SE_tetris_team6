package tetris.feature.network.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.network.protocol.RaidPayloads;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.RaidClient;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

/**
 * P2P 레이드 클라이언트. 엔진을 돌리지 않고 호스트 RAID_STATE를 그린다.
 * 이 PC의 입력은 P2로 바꿔 보낸다. 화면에서는 오른쪽이 나다.
 */
public final class ClientRaidClient implements RaidClient {
    private final String matchId;
    private final Outbound outbound;
    private final Consumer<RaidSnapshot> onFinished;
    private final List<Consumer<RaidSnapshot>> listeners = new ArrayList<>();
    private RaidSnapshot current;
    private long lastRevision = -1;
    private long sequence;
    private boolean finished;
    private boolean closed;

    public ClientRaidClient(String matchId, MatchConfig config, Outbound outbound, Consumer<RaidSnapshot> onFinished) {
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        this.outbound = Objects.requireNonNull(outbound, "outbound");
        this.onFinished = Objects.requireNonNull(onFinished, "onFinished");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(config.raidMode(), "raidMode");
        current = waiting(matchId, config);
    }

    public String matchId() {
        return matchId;
    }

    @Override
    public void send(PlayerCommand command) {
        Objects.requireNonNull(command, "command");
        if (closed || finished) return;
        outbound.send(RaidPayloads.commandMessage(matchId,
                new PlayerCommand(PlayerId.TWO, command.action(), command.pressed(), sequence++)));
    }

    @Override
    public void ultimate(PlayerId player) {
        if (closed || finished) return;
        outbound.send(RaidPayloads.ultimateMessage(matchId, PlayerId.TWO, sequence++));
    }

    @Override
    public void pickCard(PlayerId player, int index) {
        if (closed || finished) return;
        outbound.send(RaidPayloads.pickMessage(matchId, PlayerId.TWO, index, sequence++));
    }

    public void onRemote(Message message) throws ProtocolException {
        if (closed || finished || !matchId.equals(message.matchId())) return;
        switch (message.type()) {
            case RAID_STATE -> {
                RaidSnapshot state = RaidPayloads.readState(message);
                if (state.revision() <= lastRevision) return;
                accept(state);
            }
            case RAID_RESULT -> {
                RaidSnapshot.Result result = RaidPayloads.readResult(message);
                accept(new RaidSnapshot(matchId, Math.max(lastRevision, current.revision()) + 1, current.mode(),
                        RaidPhase.FINISHED, current.boss(), current.players(), current.kills(), current.elapsedMillis(),
                        current.gauge(), null, List.of(), result, current.raceBosses(), current.raceKills(),
                        current.raceGauges()));
            }
            default -> {
            }
        }
    }

    private void accept(RaidSnapshot state) {
        current = state;
        lastRevision = state.revision();
        publish(state);
        if (state.phase() == RaidPhase.FINISHED && !finished) {
            finished = true;
            onFinished.accept(state);
        }
    }

    private void publish(RaidSnapshot state) {
        for (Consumer<RaidSnapshot> listener : List.copyOf(listeners)) {
            if (listeners.contains(listener)) listener.accept(state);
        }
    }

    public RaidSnapshot snapshot() {
        return current;
    }

    @Override
    public BattleClient.Subscription subscribe(Consumer<RaidSnapshot> listener) {
        if (closed) throw new IllegalStateException("raid closed");
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(current);
        return () -> listeners.remove(listener);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        listeners.clear();
    }

    private static RaidSnapshot waiting(String matchId, MatchConfig config) {
        var boss = new RaidSnapshot.BossView(BossId.JELLY_KING, 1, 1, 1, null, 0, 8, new int[0], -1);
        boolean race = config.raidMode() == RaidMode.RACE;
        List<RaidSnapshot.BossView> bosses = race ? List.of(boss, boss) : List.of();
        int[] kills = race ? new int[] {0, 0} : new int[0];
        int[] gauges = race ? new int[] {0, 0} : new int[0];
        return new RaidSnapshot(matchId, 0, config.raidMode(), RaidPhase.COUNTDOWN, boss,
                List.of(emptyPlayer(), emptyPlayer()), 0, 0, 0, null, List.of(), null, bosses, kills, gauges);
    }

    private static RaidSnapshot.PlayerView emptyPlayer() {
        int[][] cells = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        int[][] ghost = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        var board = new MatchSnapshot.PlayerView(cells, new int[0][], 0, List.of(),
                new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS], new int[0][], ghost, List.of());
        return new RaidSnapshot.PlayerView(board, true, new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS],
                List.of(), 0, 8, 0);
    }
}
