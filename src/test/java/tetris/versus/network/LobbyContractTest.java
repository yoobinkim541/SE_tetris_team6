package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.lobby.LobbyPhase;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;

/** Consumer examples only: the fake does not verify a socket implementation. */
class LobbyContractTest {
    private static final MatchConfig STANDARD = MatchConfig.of(BattleMode.CLASSIC);

    @Test void configRevisionInvalidatesBothReadyVotesAndRejectsStaleReady() {
        LobbySnapshot original = lobby(PeerRole.HOST, STANDARD, 3, true, true);
        LobbySnapshot changed = original.configured(MatchConfig.of(BattleMode.TIMED));
        assertEquals(4, changed.configRevision());
        assertFalse(changed.selfReady());
        assertFalse(changed.peerReady());
        assertTrue(original.selfReady());
        assertThrows(IllegalArgumentException.class, () -> changed.readyAtRevision(3, true));
        assertTrue(changed.readyAtRevision(4, true).selfReady());
    }

    @Test void invalidReadySnapshotsAndClientConfigSelectionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> lobby(PeerRole.HOST, STANDARD, 0, true, false));
        assertThrows(IllegalArgumentException.class, () -> lobby(PeerRole.HOST, null, 0, true, false));
        assertThrows(IllegalArgumentException.class, () -> lobby(PeerRole.HOST, null, -1, false, false));
        LobbySnapshot client = lobby(PeerRole.CLIENT, STANDARD, 1, false, false);
        assertThrows(IllegalStateException.class, () -> client.configured(STANDARD));
    }

    private static LobbySnapshot lobby(PeerRole role, MatchConfig config, int revision,
            boolean selfReady, boolean peerReady) {
        return new LobbySnapshot(LobbyPhase.LOBBY, role, List.of(), "peer", config,
                revision, selfReady, peerReady, List.of(), "");
    }

    @Test void revisionOverflowCannotWrapOrInvalidateTheOriginalSnapshot() {
        LobbySnapshot original = lobby(PeerRole.HOST, STANDARD, Integer.MAX_VALUE, true, true);
        assertThrows(ArithmeticException.class, () -> original.configured(STANDARD));
        assertEquals(Integer.MAX_VALUE, original.configRevision());
        assertTrue(original.selfReady());
    }

    @Test void readyRequiresConfiguredConnectedLobbyAndAcceptsOnlyTheDisplayedRevision() {
        assertThrows(IllegalStateException.class,
                () -> lobby(PeerRole.HOST, null, 0, false, false).readyAtRevision(0, true));
        LobbySnapshot ready = lobby(PeerRole.CLIENT, STANDARD, 2, false, false).readyAtRevision(2, true);
        assertTrue(ready.selfReady());
        assertFalse(ready.readyAtRevision(2, false).selfReady());
    }

    @Test void consumerReceivesInitialSnapshotAndCanUnsubscribeAndCancelPendingWork() {
        FakeLobby port = new FakeLobby();
        List<LobbySnapshot> observed = new ArrayList<>();
        BattleClient.Subscription subscription = port.subscribe(observed::add);
        assertEquals(LobbyPhase.INITIAL, observed.get(0).phase());
        port.connect("localhost");
        CompletableFuture<Void> attempt = port.pending;
        assertEquals(LobbyPhase.CONNECTING, observed.get(1).phase());
        port.cancel();
        assertTrue(attempt.isCancelled());
        assertEquals(LobbyPhase.INITIAL, observed.get(2).phase());
        subscription.close();
        subscription.close();
        port.host();
        assertEquals(3, observed.size());
        CompletableFuture<Void> pending = port.pending;
        port.close();
        port.close();
        assertTrue(pending.isCancelled());
        assertThrows(IllegalStateException.class, port::host);
        assertThrows(IllegalStateException.class, () -> port.subscribe(observed::add));
    }

    private static final class FakeLobby implements LobbyClient {
        private final List<Consumer<LobbySnapshot>> listeners = new ArrayList<>();
        private LobbySnapshot snapshot = LobbySnapshot.initial(List.of());
        private CompletableFuture<Void> pending;
        private boolean closed;
        private void begin(PeerRole role, LobbyPhase phase, String address) {
            checkOpen();
            pending = new CompletableFuture<>();
            publish(new LobbySnapshot(phase, role, List.of(), address, null, 0,
                    false, false, List.of(), ""));
        }
        private void checkOpen() {
            if (closed) throw new IllegalStateException("closed");
        }
        private void publish(LobbySnapshot next) {
            snapshot = next;
            List.copyOf(listeners).forEach(listener -> listener.accept(next));
        }
        public void host() { begin(PeerRole.HOST, LobbyPhase.HOSTING, null); }
        public void connect(String address) { begin(PeerRole.CLIENT, LobbyPhase.CONNECTING, address); }
        public void cancel() {
            checkOpen();
            if (pending != null) pending.cancel(false);
            publish(LobbySnapshot.initial(List.of()));
        }
        public void configure(MatchConfig config) { checkOpen(); publish(snapshot.configured(config)); }
        public void ready(int revision) { checkOpen(); publish(snapshot.readyAtRevision(revision, true)); }
        public BattleClient battle() { throw new IllegalStateException("not in match"); }
        public BattleClient.Subscription subscribe(Consumer<LobbySnapshot> listener) {
            checkOpen();
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
