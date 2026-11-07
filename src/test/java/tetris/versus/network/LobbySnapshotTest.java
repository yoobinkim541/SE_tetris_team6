package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.network.lobby.LobbyPhase;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;

/** 로비 화면(B)이 믿고 그릴 수 있는 LobbySnapshot 불변식 (S7-C1, R3-HOST, R3-READY) */
public class LobbySnapshotTest {
    private static final MatchConfig TIMED = MatchConfig.of(BattleMode.TIMED);

    private static LobbySnapshot lobby(PeerRole role, MatchConfig config, int revision, boolean selfReady, boolean peerReady) {
        return new LobbySnapshot(LobbyPhase.LOBBY, role, List.of(), "192.168.0.7", config, revision,
                selfReady, peerReady, List.of(), "");
    }

    @Test
    void 첫_화면은_역할_주소_설정_준비가_없다() {
        LobbySnapshot initial = LobbySnapshot.initial(List.of("192.168.0.7"));

        assertEquals(LobbyPhase.INITIAL, initial.phase());
        assertNull(initial.role());
        assertNull(initial.peerAddress());
        assertNull(initial.config());
        assertEquals(List.of("192.168.0.7"), initial.recentHosts());
        assertEquals("", initial.message());
    }

    @Test
    void 설정은_revision이_1_이상일_때만_있다() {
        assertThrows(IllegalArgumentException.class, () -> lobby(PeerRole.HOST, TIMED, 0, false, false));
        assertThrows(IllegalArgumentException.class, () -> lobby(PeerRole.HOST, null, 2, false, false));
        assertDoesNotThrow(() -> lobby(PeerRole.HOST, TIMED, 2, false, false));
        assertDoesNotThrow(() -> lobby(PeerRole.CLIENT, null, 0, false, false));
    }

    @Test
    void 설정_전에는_어느_쪽도_준비될_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> lobby(PeerRole.HOST, null, 0, true, false));
        assertThrows(IllegalArgumentException.class, () -> lobby(PeerRole.HOST, null, 0, false, true));
    }

    @Test
    void 준비는_로비에서만_표시한다() {
        assertThrows(IllegalArgumentException.class, () -> new LobbySnapshot(LobbyPhase.HOSTING, PeerRole.HOST,
                List.of("192.168.0.7"), null, TIMED, 1, true, false, List.of(), ""));
    }

    @Test
    void 단계마다_역할이_정해져_있다() {
        assertThrows(IllegalArgumentException.class, () -> new LobbySnapshot(LobbyPhase.INITIAL, PeerRole.HOST,
                List.of(), null, null, 0, false, false, List.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> new LobbySnapshot(LobbyPhase.HOSTING, PeerRole.CLIENT,
                List.of("192.168.0.7"), null, null, 0, false, false, List.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> new LobbySnapshot(LobbyPhase.CONNECTING, PeerRole.HOST,
                List.of(), "192.168.0.7", null, 0, false, false, List.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> new LobbySnapshot(LobbyPhase.LOBBY, null,
                List.of(), "192.168.0.7", null, 0, false, false, List.of(), ""));
        assertDoesNotThrow(() -> new LobbySnapshot(LobbyPhase.FAILED, null, List.of(), null, null, 0,
                false, false, List.of(), "연결이 끊어졌습니다"));
    }

    @Test
    void 목록은_사본이라_넘긴_리스트를_바꿔도_스냅샷은_그대로다() {
        List<String> local = new ArrayList<>(List.of("192.168.0.7"));
        List<String> recent = new ArrayList<>(List.of("192.168.0.9"));
        LobbySnapshot hosting = new LobbySnapshot(LobbyPhase.HOSTING, PeerRole.HOST, local, null, null, 0,
                false, false, recent, "");

        local.add("10.0.0.2");
        recent.clear();

        assertEquals(List.of("192.168.0.7"), hosting.localAddresses());
        assertEquals(List.of("192.168.0.9"), hosting.recentHosts());
        assertThrows(UnsupportedOperationException.class, () -> hosting.localAddresses().add("x"));
    }

    @Test
    void 안내_문구는_없으면_빈_문자열이고_null은_허용하지_않는다() {
        assertThrows(NullPointerException.class, () -> new LobbySnapshot(LobbyPhase.INITIAL, null, List.of(), null,
                null, 0, false, false, List.of(), null));
    }
}
