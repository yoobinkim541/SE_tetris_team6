package tetris.feature.network.match;

import java.util.Objects;
import java.util.function.Consumer;
import tetris.feature.network.protocol.MatchPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.MatchClock;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

/**
 * P2P 호스트 쪽 BattleClient (S9-C1, S9-C2). 이 PC만 엔진을 계산한다 (호스트 권위).
 *
 * <ul>
 *   <li>이 화면의 입력은 항상 {@link PlayerId#ONE}, 상대 INPUT은 {@link PlayerId#TWO}만 받는다. 다른 playerId·이전 matchId는 버린다.
 *       순번 중복·역순은 엔진이 버린다</li>
 *   <li>Pause는 누가 요청하든 받은 순서대로 바로 적용하고 STATE로 알린다. 승인 대기 없음 (D-03)</li>
 *   <li>STATE는 최신 값만 의미 있으므로 최대 {@link #STATE_INTERVAL_MILLIS}마다 한 번 보낸다. 단계가 바뀌면 바로 보내고,
 *       경기가 끝나면 마지막 STATE 뒤에 RESULT를 한 번 보낸다. 남은 시간은 호스트 엔진 기준이다</li>
 * </ul>
 * 모든 메서드는 lobby dispatcher(앱은 EDT) 스레드에서 호출한다. 네트워크 송신은 대기열에 넣기만 해서 기다리지 않는다.
 */
public final class HostBattleClient implements BattleClient {
    /** STATE 송신 상한 30 Hz (network-protocol.md) */
    public static final long STATE_INTERVAL_MILLIS = 33;

    private final String matchId;
    private final LocalMatchEngine engine;
    private final MatchClock clock;
    private final Outbound outbound;
    private final Consumer<MatchSnapshot> onFinished;
    private final SnapshotListeners listeners = new SnapshotListeners();
    private final MatchPulse.Handle pulse;
    private long localSequence;
    private long lastRemoteSequence = -1;
    private long lastSentRevision = -1;
    private long nextStateAt = Long.MIN_VALUE;
    private MatchPhase lastSentPhase;
    private int rejectedInputs;
    private int acceptedRemoteInputs;
    private boolean finished;
    private boolean closed;

    public HostBattleClient(String matchId, MatchConfig config, LocalMatchEngine engine, MatchClock clock,
                            MatchPulse pulse, Outbound outbound, Consumer<MatchSnapshot> onFinished) {
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.outbound = Objects.requireNonNull(outbound, "outbound");
        this.onFinished = Objects.requireNonNull(onFinished, "onFinished");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(pulse, "pulse");
        engine.start(config, matchId);
        sendState(engine.snapshot(), clock.nowMillis());
        this.pulse = pulse.start(VersusRules.MATCH_PULSE_MILLIS, this::tick);
    }

    public String matchId() {
        return matchId;
    }

    /** 위조 playerId·다른 matchId로 버린 INPUT 수 (검증용) */
    public int rejectedInputs() {
        return rejectedInputs;
    }

    /** 엔진에 넘긴 상대 INPUT 수 (검증용). 비동기 수신을 기다리는 테스트가 처리 완료를 확인한다 */
    public int acceptedRemoteInputs() {
        return acceptedRemoteInputs;
    }

    @Override
    public void send(PlayerCommand command) {
        Objects.requireNonNull(command, "command");
        if (closed || finished) return;
        submit(new PlayerCommand(PlayerId.ONE, command.action(), command.pressed(), localSequence++));
    }

    /** 상대가 보낸 경기 메시지. INPUT만 의미가 있다 */
    public void onRemote(Message message) throws ProtocolException {
        if (closed || finished || message.type() != MessageType.INPUT) return;
        if (!matchId.equals(message.matchId())) {
            rejectedInputs++;
            return;
        }
        PlayerCommand command = MatchPayloads.readInput(message);
        if (command.playerId() != PlayerId.TWO || command.sequence() <= lastRemoteSequence) {
            rejectedInputs++;
            return;
        }
        lastRemoteSequence = command.sequence();
        acceptedRemoteInputs++;
        submit(command);
    }

    private void submit(PlayerCommand command) {
        engine.submit(command);
        // Pause·Quit은 다음 pulse를 기다리지 않고 바로 적용해 양쪽 화면에 알린다
        if (command.pressed() && (command.action() == GameAction.PAUSE || command.action() == GameAction.QUIT)) tick();
    }

    private void tick() {
        if (closed || finished) return;
        long now = clock.nowMillis();
        engine.advance(now);
        MatchSnapshot snapshot = engine.snapshot();
        listeners.publish(snapshot);
        if (closed) return;
        boolean phaseChanged = snapshot.phase() != lastSentPhase;
        if (snapshot.revision() != lastSentRevision
                && (phaseChanged || now >= nextStateAt)) {
            sendState(snapshot, now);
        }
        if (snapshot.phase() == MatchPhase.FINISHED) finish(snapshot);
    }

    private void sendState(MatchSnapshot snapshot, long now) {
        outbound.send(MatchPayloads.stateMessage(snapshot));
        lastSentRevision = snapshot.revision();
        lastSentPhase = snapshot.phase();
        // 주기 기준으로 다음 송신 시각을 잡는다. 16 ms pulse에서 "마지막 송신 뒤 33 ms"로 세면 48 ms마다(21 Hz)가 된다
        if (nextStateAt == Long.MIN_VALUE) {
            nextStateAt = now + STATE_INTERVAL_MILLIS;
        } else if (now >= nextStateAt) {
            nextStateAt = Math.max(nextStateAt + STATE_INTERVAL_MILLIS, now);
        }
    }

    private void finish(MatchSnapshot snapshot) {
        finished = true;
        pulse.close();
        if (lastSentRevision != snapshot.revision()) sendState(snapshot, clock.nowMillis());
        outbound.send(MatchPayloads.resultMessage(matchId, snapshot.result()));
        onFinished.accept(snapshot);
    }

    @Override
    public Subscription subscribe(java.util.function.Consumer<MatchSnapshot> listener) {
        if (closed) throw new IllegalStateException("battle closed");
        return listeners.add(listener, engine.snapshot());
    }

    public MatchSnapshot snapshot() {
        return engine.snapshot();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        pulse.close();
        listeners.clear();
        engine.close();
    }
}
