package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;

/** Consumer examples only: the fake does not verify a socket implementation. */
class LobbyContractTest {
    private static final MatchConfig STANDARD = MatchConfig.of(BattleMode.CLASSIC);

    @Test void configRevisionInvalidatesBothReadyVotesAndRejectsStaleReady() {
        LobbySnapshot original = new LobbySnapshot(Role.HOST, State.LOBBY, STANDARD, 3,
                true, true, "peer-1");
        LobbySnapshot changed = original.selectConfig(MatchConfig.of(BattleMode.TIMED));
        assertEquals(4, changed.configRevision());
        assertFalse(changed.localReady());
        assertFalse(changed.peerReady());
        assertTrue(original.localReady());
        assertThrows(IllegalArgumentException.class, () -> changed.readyAtRevision(3, true));
        assertTrue(changed.readyAtRevision(4, true).localReady());
    }

    @Test void invalidReadySnapshotsAndClientConfigSelectionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LobbySnapshot(Role.HOST,
                State.LOBBY, STANDARD, 0, true, false, "peer"));
        assertThrows(IllegalArgumentException.class, () -> new LobbySnapshot(Role.HOST,
                State.LOBBY, null, 0, true, false, "peer"));
        assertThrows(IllegalArgumentException.class, () -> new LobbySnapshot(Role.NONE,
                State.INITIAL, null, -1, false, false, null));
        LobbySnapshot client = new LobbySnapshot(Role.CLIENT, State.LOBBY, STANDARD, 1,
                false, false, "host");
        assertThrows(IllegalStateException.class, () -> client.selectConfig(STANDARD));
    }

    @Test void consumerReceivesInitialSnapshotAndCanUnsubscribeAndCancelPendingWork() {
        FakeLobby port = new FakeLobby();
        List<LobbySnapshot> observed = new ArrayList<>();
        LobbyClient.Subscription subscription = port.subscribe(observed::add);
        assertEquals(State.INITIAL, observed.get(0).state());
        CompletionStage<Void> attempt = port.connect("localhost", 1234);
        assertEquals(State.CONNECTING, observed.get(1).state());
        port.cancel();
        assertTrue(attempt.toCompletableFuture().isCancelled());
        assertEquals(State.INITIAL, observed.get(2).state());
        subscription.close();
        subscription.close();
        port.host(1234);
        assertEquals(3, observed.size());
        CompletionStage<Void> pending = port.pending;
        port.close();
        port.close();
        assertTrue(pending.toCompletableFuture().isCancelled());
    }

    private static final class FakeLobby implements LobbyClient {
        private final List<Consumer<LobbySnapshot>> listeners = new ArrayList<>();
        private LobbySnapshot snapshot = initial();
        private CompletableFuture<Void> pending;
        private boolean closed;
        private static LobbySnapshot initial() {
            return new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null);
        }
        private CompletionStage<Void> begin(Role role, State state) {
            if (closed) throw new IllegalStateException("closed");
            pending = new CompletableFuture<>();
            publish(new LobbySnapshot(role, state, null, 0, false, false, null));
            return pending;
        }
        private void publish(LobbySnapshot next) {
            snapshot = next;
            List.copyOf(listeners).forEach(listener -> listener.accept(next));
        }
        public CompletionStage<Void> host(int port) { return begin(Role.HOST, State.HOSTING); }
        public CompletionStage<Void> connect(String address, int port) {
            return begin(Role.CLIENT, State.CONNECTING);
        }
        public void cancel() {
            if (pending != null) pending.cancel(false);
            publish(initial());
        }
        public void selectConfig(MatchConfig config) { publish(snapshot.selectConfig(config)); }
        public void ready(long revision, boolean ready) {
            publish(snapshot.readyAtRevision(revision, ready));
        }
        public void leave() { cancel(); }
        public Optional<BattleClient> battleClient() { return Optional.empty(); }
        public Subscription subscribe(Consumer<LobbySnapshot> listener) {
            if (closed) throw new IllegalStateException("closed");
            listeners.add(listener);
            listener.accept(snapshot);
            return () -> listeners.remove(listener);
        }
        public void close() {
            if (closed) return;
            closed = true;
            if (pending != null) pending.cancel(false);
            listeners.clear();
        }
    }
}
