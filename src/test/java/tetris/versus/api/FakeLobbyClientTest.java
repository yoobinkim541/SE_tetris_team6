package tetris.versus.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tetris.feature.versus.api.*;

/** S7-A1 로비 계약 초안: R3-P2P-LOBBY 흐름을 가짜 구현으로 고정 */
class FakeLobbyClientTest {

    /** 닫혔는지만 기록하는 최소 경기 포트 */
    private static final class StubBattle implements BattleClient {
        boolean closed;

        @Override public void send(PlayerCommand command) { }

        @Override public Subscription subscribe(Consumer<MatchSnapshot> listener) { return () -> { }; }

        @Override public void close() { closed = true; }
    }

    private final List<StubBattle> battles = new ArrayList<>();
    private final FakeLobbyClient lobby = new FakeLobbyClient(List.of("10.0.0.2"), () -> {
        StubBattle b = new StubBattle();
        battles.add(b);
        return b;
    });

    @Test
    void hostFlowReachesMatchOnlyWhenBothReady() {
        List<LobbyPhase> seen = new ArrayList<>();
        lobby.subscribe(s -> seen.add(s.phase()));
        assertEquals(List.of(LobbyPhase.IDLE), seen);

        lobby.host();
        assertNotNull(lobby.current().hostAddress());
        lobby.peerConnected();
        assertThrows(IllegalStateException.class, lobby::ready);

        lobby.configure(MatchConfig.of(VersusMode.ITEM));
        assertEquals(1, lobby.current().configRevision());
        lobby.ready();
        assertTrue(lobby.current().selfReady());
        assertEquals(LobbyPhase.CONNECTED, lobby.current().phase());
        assertThrows(IllegalStateException.class, lobby::battle);

        lobby.peerReady();
        assertEquals(LobbyPhase.IN_MATCH, lobby.current().phase());
        assertSame(battles.getFirst(), lobby.battle());
        assertEquals(List.of(LobbyPhase.IDLE, LobbyPhase.HOSTING, LobbyPhase.CONNECTED, LobbyPhase.CONNECTED,
                LobbyPhase.CONNECTED, LobbyPhase.IN_MATCH), seen);
    }

    @Test
    void reconfiguringBumpsRevisionAndClearsReady() {
        lobby.host();
        lobby.peerConnected();
        lobby.configure(MatchConfig.of(VersusMode.NORMAL));
        lobby.peerReady();
        assertTrue(lobby.current().peerReady());
        lobby.configure(MatchConfig.of(VersusMode.TIME_LIMIT));
        assertEquals(2, lobby.current().configRevision());
        assertFalse(lobby.current().peerReady());
    }

    @Test
    void clientCannotChooseModeAndRecentHostsAreMostRecentFirst() {
        lobby.connect("192.168.0.5");
        assertEquals(List.of("192.168.0.5", "10.0.0.2"), lobby.current().recentHosts());
        lobby.peerConnected();
        assertFalse(lobby.current().host());
        assertThrows(IllegalStateException.class, () -> lobby.configure(MatchConfig.of(VersusMode.NORMAL)));
        assertThrows(IllegalArgumentException.class, () -> new FakeLobbyClient(List.of(), StubBattle::new).connect(" "));
    }

    @Test
    void finishedMatchReturnsToConnectedWaitingState() {
        lobby.host();
        lobby.peerConnected();
        lobby.configure(MatchConfig.of(VersusMode.NORMAL));
        lobby.ready();
        lobby.peerReady();
        lobby.matchFinished();
        assertTrue(battles.getFirst().closed);
        assertEquals(LobbyPhase.CONNECTED, lobby.current().phase());
        assertFalse(lobby.current().selfReady());
        assertEquals(1, lobby.current().configRevision());
    }

    @Test
    void failureClosesMatchAndAllowsRetryFromFailed() {
        lobby.connect("10.0.0.2");
        lobby.fail("연결이 끊어졌습니다");
        assertEquals(LobbyPhase.FAILED, lobby.current().phase());
        assertEquals("연결이 끊어졌습니다", lobby.current().message());
        lobby.connect("10.0.0.2");
        assertEquals(List.of("10.0.0.2"), lobby.current().recentHosts());
        lobby.cancel();
        assertEquals(LobbyPhase.IDLE, lobby.current().phase());
    }

    @Test
    void invalidTransitionsAndClosedLobbyAreRejected() {
        assertThrows(IllegalStateException.class, lobby::peerConnected);
        assertThrows(IllegalStateException.class, lobby::matchFinished);
        var sub = lobby.subscribe(s -> { });
        sub.close();
        lobby.close();
        lobby.close();
        assertThrows(IllegalStateException.class, lobby::host);
        assertThrows(IllegalStateException.class, () -> lobby.subscribe(s -> { }));
    }

    @Test
    void lobbySnapshotEnforcesConfigRevisionInvariants() {
        var cfg = MatchConfig.of(VersusMode.NORMAL);
        assertThrows(IllegalArgumentException.class,
                () -> new LobbySnapshot(LobbyPhase.CONNECTED, true, null, cfg, 0, false, false, List.of(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> new LobbySnapshot(LobbyPhase.CONNECTED, true, null, null, 1, false, false, List.of(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> new LobbySnapshot(LobbyPhase.CONNECTED, true, null, null, 0, true, false, List.of(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> new LobbySnapshot(LobbyPhase.CONNECTED, true, null, null, -1, false, false, List.of(), ""));
    }
}
