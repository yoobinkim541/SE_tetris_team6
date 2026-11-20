package tetris.feature.network.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.network.protocol.RaidPayloads;
import tetris.feature.raid.RaidEngine;
import tetris.feature.raid.api.RaidClient;
import tetris.feature.raid.api.RaidConfig;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.MatchClock;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

/**
 * P2P 레이드 호스트. 이 PC만 {@link RaidEngine}을 돌리고, 상대 입력은 P2로만 받는다.
 * RAID_STATE는 대전 STATE와 같은 간격으로 보내고, 끝나면 RAID_RESULT를 한 번 보낸다.
 */
public final class HostRaidClient implements RaidClient {
    private final String matchId;
    private final RaidEngine engine;
    private final MatchClock clock;
    private final Outbound outbound;
    private final Consumer<RaidSnapshot> onFinished;
    private final List<Consumer<RaidSnapshot>> listeners = new ArrayList<>();
    private final MatchPulse.Handle pulse;
    private long localSequence;
    private long lastRemoteSequence = -1;
    private long lastSentRevision = -1;
    private long nextStateAt = Long.MIN_VALUE;
    private RaidPhase lastSentPhase;
    private int rejectedInputs;
    private int acceptedRemoteInputs;
    private boolean finished;
    private boolean closed;

    public HostRaidClient(String matchId, MatchConfig config, MatchClock clock, MatchPulse pulse, Outbound outbound,
                          Consumer<RaidSnapshot> onFinished) {
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.outbound = Objects.requireNonNull(outbound, "outbound");
        this.onFinished = Objects.requireNonNull(onFinished, "onFinished");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(config.raidMode(), "raidMode");
        Objects.requireNonNull(pulse, "pulse");
        long seed = mix(config.raidMode().ordinal(), clock.nowMillis(), matchId);
        this.engine = new RaidEngine(new RaidConfig(config.raidMode(), seed, config.controlStyle()), clock);
        sendState(engine.snapshot(), clock.nowMillis());
        this.pulse = pulse.start(VersusRules.MATCH_PULSE_MILLIS, this::tick);
    }

    public String matchId() {
        return matchId;
    }

    public int rejectedInputs() {
        return rejectedInputs;
    }

    public int acceptedRemoteInputs() {
        return acceptedRemoteInputs;
    }

    @Override
    public void send(PlayerCommand command) {
        Objects.requireNonNull(command, "command");
        if (closed || finished) return;
        submit(new PlayerCommand(PlayerId.ONE, command.action(), command.pressed(), localSequence++));
    }

    @Override
    public void ultimate(PlayerId player) {
        if (closed || finished || player != PlayerId.ONE) return;
        engine.ultimate(PlayerId.ONE);
        tick();
    }

    @Override
    public void pickCard(PlayerId player, int index) {
        if (closed || finished || player != PlayerId.ONE) return;
        engine.pickCard(PlayerId.ONE, index);
        tick();
    }

    public void onRemote(Message message) throws ProtocolException {
        if (closed || finished || message.type() != MessageType.RAID_INPUT) return;
        if (!matchId.equals(message.matchId())) {
            rejectedInputs++;
            return;
        }
        RaidPayloads.RaidInput input = RaidPayloads.readInput(message);
        if (input.player() != PlayerId.TWO || input.sequence() <= lastRemoteSequence) {
            rejectedInputs++;
            return;
        }
        lastRemoteSequence = input.sequence();
        acceptedRemoteInputs++;
        switch (input.kind()) {
            case COMMAND -> submit(input.command());
            case ULTIMATE -> {
                engine.ultimate(PlayerId.TWO);
                tick();
            }
            case PICK -> {
                engine.pickCard(PlayerId.TWO, input.index());
                tick();
            }
        }
    }

    private void submit(PlayerCommand command) {
        engine.submit(command);
        if (command.pressed() && (command.action() == GameAction.PAUSE || command.action() == GameAction.QUIT)) tick();
    }

    private void tick() {
        if (closed || finished) return;
        long now = clock.nowMillis();
        engine.advance(now);
        RaidSnapshot snapshot = engine.snapshot();
        publish(snapshot);
        if (closed) return;
        boolean phaseChanged = snapshot.phase() != lastSentPhase;
        if (snapshot.revision() != lastSentRevision && (phaseChanged || now >= nextStateAt)) sendState(snapshot, now);
        if (snapshot.phase() == RaidPhase.FINISHED) finish(snapshot);
    }

    private void sendState(RaidSnapshot snapshot, long now) {
        outbound.send(RaidPayloads.stateMessage(withMatchId(snapshot)));
        lastSentRevision = snapshot.revision();
        lastSentPhase = snapshot.phase();
        if (nextStateAt == Long.MIN_VALUE) nextStateAt = now + HostBattleClient.STATE_INTERVAL_MILLIS;
        else if (now >= nextStateAt) nextStateAt = Math.max(nextStateAt + HostBattleClient.STATE_INTERVAL_MILLIS, now);
    }

    private void finish(RaidSnapshot snapshot) {
        finished = true;
        pulse.close();
        RaidSnapshot wired = withMatchId(snapshot);
        if (lastSentRevision != snapshot.revision()) sendState(snapshot, clock.nowMillis());
        if (wired.result() != null) outbound.send(RaidPayloads.resultMessage(matchId, wired.result()));
        onFinished.accept(wired);
    }

    private void publish(RaidSnapshot snapshot) {
        RaidSnapshot wired = withMatchId(snapshot);
        for (Consumer<RaidSnapshot> listener : List.copyOf(listeners)) {
            if (listeners.contains(listener)) listener.accept(wired);
        }
    }

    private RaidSnapshot withMatchId(RaidSnapshot snapshot) {
        if (matchId.equals(snapshot.raidId())) return snapshot;
        return new RaidSnapshot(matchId, snapshot.revision(), snapshot.mode(), snapshot.phase(), snapshot.boss(),
                snapshot.players(), snapshot.kills(), snapshot.elapsedMillis(), snapshot.gauge(), snapshot.offer(),
                snapshot.events(), snapshot.result(), snapshot.raceBosses(), snapshot.raceKills(), snapshot.raceGauges());
    }

    public RaidSnapshot snapshot() {
        return withMatchId(engine.snapshot());
    }

    @Override
    public BattleClient.Subscription subscribe(Consumer<RaidSnapshot> listener) {
        if (closed) throw new IllegalStateException("raid closed");
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(snapshot());
        return () -> listeners.remove(listener);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        pulse.close();
        listeners.clear();
        engine.close();
    }

    private static long mix(int mode, long now, String matchId) {
        long seed = now ^ (mode * 0x9E3779B97F4A7C15L) ^ matchId.hashCode();
        return seed == 0 ? 1 : seed;
    }
}
