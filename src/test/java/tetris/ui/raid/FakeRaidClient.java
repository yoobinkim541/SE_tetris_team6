package tetris.ui.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import tetris.feature.raid.api.RaidClient;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;

/**
 * 스크립트된 스냅샷만 주는 레이드 클라이언트. 화면 테스트가 쓴다.
 * 콜백은 호출한 스레드에서 바로 온다 (테스트가 EDT를 기다리지 않게).
 */
public final class FakeRaidClient implements RaidClient {
    public record Pick(PlayerId player, int index) {
    }

    private final List<PlayerCommand> sent = new ArrayList<>();
    private final List<PlayerId> ultimates = new ArrayList<>();
    private final List<Pick> picks = new ArrayList<>();
    private RaidSnapshot current;
    private Consumer<RaidSnapshot> listener;
    private boolean closed;

    public FakeRaidClient(RaidSnapshot initial) {
        this.current = Objects.requireNonNull(initial, "initial");
    }

    public void publish(RaidSnapshot snapshot) {
        current = Objects.requireNonNull(snapshot, "snapshot");
        if (listener != null && !closed) listener.accept(current);
    }

    public List<PlayerCommand> sent() {
        return List.copyOf(sent);
    }

    public List<PlayerId> ultimates() {
        return List.copyOf(ultimates);
    }

    public List<Pick> picks() {
        return List.copyOf(picks);
    }

    public RaidSnapshot current() {
        return current;
    }

    @Override
    public void send(PlayerCommand command) {
        if (!closed) sent.add(command);
    }

    @Override
    public void ultimate(PlayerId player) {
        if (!closed) ultimates.add(player);
    }

    @Override
    public void pickCard(PlayerId player, int index) {
        if (!closed) picks.add(new Pick(player, index));
    }

    @Override
    public BattleClient.Subscription subscribe(Consumer<RaidSnapshot> listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
        if (!closed && current != null) listener.accept(current);
        return () -> {
            if (this.listener == listener) this.listener = null;
        };
    }

    @Override
    public void close() {
        closed = true;
        listener = null;
    }
}
