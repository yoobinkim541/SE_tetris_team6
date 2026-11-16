package tetris.feature.network.match;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import tetris.feature.network.protocol.MatchPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchResult;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.MatchSnapshot.PlayerView;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

/**
 * P2P 클라이언트 쪽 BattleClient (S9-C1, S9-C2). 엔진을 돌리지 않고 호스트 STATE를 그대로 보여 준다.
 *
 * <ul>
 *   <li>이 화면의 입력은 {@link PlayerId#TWO}로 바꿔 INPUT으로 보낸다. 상대 보드는 조작할 수 없다</li>
 *   <li>같은 matchId의 더 새로운 revision만 받는다. 오래된·다른 경기의 STATE·RESULT는 버린다</li>
 *   <li>첫 STATE 전에는 빈 보드의 COUNTDOWN을 보여 주어 조작 키가 엔진에 들어가지 않는다</li>
 *   <li>RESULT가 FINISHED STATE보다 먼저 오면 마지막 STATE에 결과를 붙여 끝낸다. 결과는 호스트와 같다</li>
 * </ul>
 * 모든 메서드는 lobby dispatcher(앱은 EDT) 스레드에서 호출한다.
 */
public final class ClientBattleClient implements BattleClient {
    private final String matchId;
    private final Outbound outbound;
    private final Consumer<MatchSnapshot> onFinished;
    private final SnapshotListeners listeners = new SnapshotListeners();
    private MatchSnapshot current;
    private long lastRevision = -1;
    private long sequence;
    private boolean finished;
    private boolean closed;

    public ClientBattleClient(String matchId, MatchConfig config, Outbound outbound, Consumer<MatchSnapshot> onFinished) {
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        this.outbound = Objects.requireNonNull(outbound, "outbound");
        this.onFinished = Objects.requireNonNull(onFinished, "onFinished");
        Objects.requireNonNull(config, "config");
        current = waiting(matchId, config);
    }

    private static MatchSnapshot waiting(String matchId, MatchConfig config) {
        PlayerView empty = new PlayerView(new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS],
                new int[0][], 0, List.of());
        long remaining = config.mode().timed() ? config.timeLimitMillis() : -1;
        return new MatchSnapshot(matchId, 0, empty, empty, remaining, MatchPhase.COUNTDOWN, null);
    }

    public String matchId() {
        return matchId;
    }

    @Override
    public void send(PlayerCommand command) {
        Objects.requireNonNull(command, "command");
        if (closed || finished) return;
        outbound.send(MatchPayloads.inputMessage(matchId,
                new PlayerCommand(PlayerId.TWO, command.action(), command.pressed(), sequence++)));
    }

    /** 호스트가 보낸 STATE·RESULT. 형식 오류는 ProtocolException으로 알려 연결을 끊게 한다 */
    public void onRemote(Message message) throws ProtocolException {
        if (closed || finished || !matchId.equals(message.matchId())) return;
        switch (message.type()) {
            case STATE -> {
                MatchSnapshot state = MatchPayloads.readState(message);
                if (state.revision() <= lastRevision) return;
                accept(state);
            }
            case RESULT -> {
                MatchResult result = MatchPayloads.readResult(message);
                accept(new MatchSnapshot(matchId, Math.max(lastRevision, current.revision()) + 1, current.one(),
                        current.two(), current.remainingMillis(), MatchPhase.FINISHED, result));
            }
            default -> {
            }
        }
    }

    private void accept(MatchSnapshot state) {
        current = state;
        lastRevision = state.revision();
        listeners.publish(state);
        if (state.phase() == MatchPhase.FINISHED && !finished) {
            finished = true;
            onFinished.accept(state);
        }
    }

    public MatchSnapshot snapshot() {
        return current;
    }

    @Override
    public Subscription subscribe(Consumer<MatchSnapshot> listener) {
        if (closed) throw new IllegalStateException("battle closed");
        return listeners.add(listener, current);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        listeners.clear();
    }
}
