package tetris.feature.network.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.MatchSnapshot;

/** BattleClient 구독 관리. 구독 즉시 현재 스냅샷을 한 번 전달하고, 해지한 뒤에는 부르지 않는다 */
final class SnapshotListeners {
    private final List<Consumer<MatchSnapshot>> listeners = new ArrayList<>();

    BattleClient.Subscription add(Consumer<MatchSnapshot> listener, MatchSnapshot current) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(current);
        return () -> listeners.remove(listener);
    }

    void publish(MatchSnapshot snapshot) {
        for (Consumer<MatchSnapshot> listener : List.copyOf(listeners)) {
            if (listeners.contains(listener)) listener.accept(snapshot);
        }
    }

    void clear() {
        listeners.clear();
    }
}
