package tetris.feature.network.lobby;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 로비 스냅샷·연결 상태 구독 목록. dispatcher 스레드에서만 쓴다.
 *
 * <p>구독 즉시 현재 값을 한 번 전달한다. 구독자 콜백 안에서 다시 발행해도(예: STARTING을 받고 battleClient()로
 * PLAYING) 바깥 전달을 끝낸 뒤 다음 값을 보내므로 모든 구독자가 같은 순서로 받는다. 해지·{@link #clear()} 뒤에는
 * 이미 진행 중인 전달에서도 부르지 않는다.
 */
final class OrderedListeners<T> {
    private static final class Entry<T> {
        final Consumer<T> listener;
        boolean active = true;

        Entry(Consumer<T> listener) {
            this.listener = listener;
        }
    }

    private final List<Entry<T>> entries = new ArrayList<>();
    private final ArrayDeque<T> pending = new ArrayDeque<>();
    private boolean publishing;

    LobbyClient.Subscription add(Consumer<T> listener, T current) {
        Objects.requireNonNull(listener, "listener");
        Entry<T> entry = new Entry<>(listener);
        entries.add(entry);
        listener.accept(current);
        return () -> {
            if (entry.active) {
                entry.active = false;
                entries.remove(entry);
            }
        };
    }

    void publish(T value) {
        pending.addLast(value);
        if (publishing) return;
        publishing = true;
        try {
            while (!pending.isEmpty()) {
                T next = pending.removeFirst();
                for (Entry<T> entry : List.copyOf(entries)) {
                    if (entry.active) entry.listener.accept(next);
                }
            }
        } finally {
            publishing = false;
            pending.clear();
        }
    }

    /** 모든 구독을 해지한다. 진행 중인 전달도 남은 구독자를 부르지 않는다 */
    void clear() {
        for (Entry<T> entry : entries) entry.active = false;
        entries.clear();
    }
}
