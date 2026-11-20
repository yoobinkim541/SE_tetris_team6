package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tetris.feature.network.EstablishException;
import tetris.feature.network.EstablishFailure;
import tetris.feature.network.lobby.LinkHealth;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.ui.versus.P2pLobbyController;
import tetris.ui.versus.P2pLobbyPanel;

/** challenge: P2P 화면의 인터넷 방 만들기·코드로 입장. LAN 흐름의 안내는 그대로다 */
class P2pInternetLobbyTest {
    /** 인터넷 방과 연결 상태까지 흉내 내는 가짜 로비 */
    static final class OnlineLobby extends P2pLobbyControllerTest.FakeLobby {
        final List<Consumer<Optional<String>>> codeListeners = new ArrayList<>();
        final List<Consumer<LinkHealth>> healthListeners = new ArrayList<>();
        Optional<String> code = Optional.empty();

        void code(String next) {
            code = Optional.ofNullable(next);
            for (var l : List.copyOf(codeListeners)) l.accept(code);
        }

        void health(LinkHealth next) {
            for (var l : List.copyOf(healthListeners)) l.accept(next);
        }

        @Override public CompletionStage<Void> hostOnline() {
            calls.add("hostOnline");
            pending = new CompletableFuture<>();
            publish(new LobbySnapshot(Role.HOST, State.HOSTING, null, 0, false, false, null));
            return pending;
        }

        @Override public CompletionStage<Void> joinOnline(String c) {
            calls.add("joinOnline " + c);
            pending = new CompletableFuture<>();
            publish(new LobbySnapshot(Role.CLIENT, State.CONNECTING, null, 0, false, false, null));
            return pending;
        }

        @Override public Optional<String> roomCode() {
            return code;
        }

        @Override public Subscription subscribeRoomCode(Consumer<Optional<String>> listener) {
            codeListeners.add(listener);
            listener.accept(code);
            return () -> codeListeners.remove(listener);
        }

        @Override public Subscription subscribeHealth(Consumer<LinkHealth> listener) {
            healthListeners.add(listener);
            listener.accept(LinkHealth.NONE);
            return () -> healthListeners.remove(listener);
        }

        /** 실제 로비처럼 실패하면 INITIAL을 먼저 알리고 단계를 실패시킨다 */
        void fail(EstablishFailure reason) {
            code(null);
            publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
            pending.completeExceptionally(new EstablishException(reason, "test"));
        }
    }

    private final OnlineLobby lobby = new OnlineLobby();
    private final P2pLobbyControllerTest.Nav nav = new P2pLobbyControllerTest.Nav();
    private final P2pLobbyController controller =
            new P2pLobbyController(lobby, nav, () -> 50123, () -> List.of("192.168.0.7"));
    private final P2pLobbyPanel panel = controller.panel();

    @Test
    void 인터넷_방을_만들면_코드를_받기_전과_후에_안내가_바뀐다() {
        assertTrue(panel.onlineHostButton().isEnabled());
        panel.onlineHostButton().doClick();

        assertEquals(List.of("hostOnline"), lobby.calls);
        assertEquals("인터넷 방 만드는 중…", panel.statusText());
        assertFalse(panel.onlineHostButton().isEnabled(), "대기 중에는 다시 만들 수 없다");
        assertFalse(panel.joinCodeButton().isEnabled());
        assertFalse(panel.codeField().isEnabled());
        assertFalse(panel.hostButton().isEnabled(), "LAN 대기도 막는다");
        assertEquals("취소", panel.backButton().getText());

        lobby.code("K7QM2X");
        assertTrue(panel.statusText().contains("K7Q-M2X"), panel.statusText());
        assertFalse(panel.statusText().contains("192.168.0.7"), "인터넷 방은 내 LAN IP를 보이지 않는다");

        lobby.code(null);
        lobby.pending.complete(null);
        lobby.publish(new LobbySnapshot(Role.HOST, State.LOBBY, null, 0, false, false, "인터넷 · K7Q-M2X#1"));
        assertTrue(panel.statusText().startsWith("연결 성공!"), panel.statusText());
        assertTrue(panel.peerText().contains("인터넷 · K7Q-M2X"), panel.peerText());
    }

    @Test
    void 코드로_입장은_정규화한_코드를_넘기고_입력이_틀리면_보내지_않는다() {
        panel.codeField().setText("   ");
        panel.joinCodeButton().doClick();
        assertTrue(panel.statusText().contains("코드"), panel.statusText());
        assertTrue(panel.isError());

        panel.codeField().setText("K7Q-M2");
        panel.joinCodeButton().doClick();
        assertTrue(panel.statusText().contains("6자리"), panel.statusText());
        assertTrue(lobby.calls.isEmpty(), lobby.calls.toString());

        panel.codeField().setText(" k7q-m2x ");
        panel.joinCodeButton().doClick();
        assertEquals(List.of("joinOnline K7QM2X"), lobby.calls);
        assertEquals("인터넷 방에 들어가는 중…", panel.statusText());
    }

    @Test
    void 중계_실패는_이유마다_다른_안내를_보인다() {
        Map<EstablishFailure, String> expected = Map.of(
                EstablishFailure.RELAY_UNAVAILABLE, "중계 서버",
                EstablishFailure.NO_ROOM, "코드의 방이 없습니다",
                EstablishFailure.ROOM_FULL, "이미 대전 중",
                EstablishFailure.SLOW_DOWN, "잠시 후",
                EstablishFailure.SERVER_FULL, "혼잡",
                EstablishFailure.ROOM_EXPIRED, "만료",
                EstablishFailure.TIMEOUT, "응답이 없습니다");
        for (var entry : expected.entrySet()) {
            panel.codeField().setText("K7QM2X");
            panel.joinCodeButton().doClick();
            lobby.fail(entry.getKey());
            String text = panel.statusText();
            assertTrue(text.contains(entry.getValue()), entry.getKey() + " → " + text);
            assertFalse(text.contains("같은 LAN"), "인터넷 방 안내에 LAN 문구가 섞이면 안 된다: " + text);
            assertTrue(panel.isError());
        }
    }

    @Test
    void 인터넷_방에서_끊기면_LAN_점검_안내를_보이지_않는다() {
        panel.onlineHostButton().doClick();
        lobby.pending.complete(null);
        lobby.publish(new LobbySnapshot(Role.HOST, State.LOBBY, null, 0, false, false, "인터넷 · K7Q-M2X#1"));
        lobby.publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
        lobby.health(new LinkHealth(LinkHealth.Status.LOST, 40));

        assertTrue(panel.statusText().contains("끊어졌습니다"), panel.statusText());
        assertTrue(panel.statusText().contains("인터넷"), panel.statusText());
        assertFalse(panel.statusText().contains("같은 LAN"), panel.statusText());
    }

    @Test
    void 인터넷_방을_취소한_뒤_LAN_접속_실패는_원래_LAN_안내다() {
        panel.onlineHostButton().doClick();
        panel.backButton().doClick();
        assertTrue(lobby.calls.contains("cancel"));

        panel.addressField().setText("10.0.0.5");
        panel.connectButton().doClick();
        lobby.publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
        lobby.pending.completeExceptionally(new EstablishException(EstablishFailure.TIMEOUT, "lan"));
        assertEquals("응답이 없습니다. 같은 LAN인지, 방화벽 허용을 확인하세요.", panel.statusText());
    }

    @Test
    void 화면을_닫으면_코드_구독도_해지한다() {
        controller.close();
        assertTrue(lobby.codeListeners.isEmpty());
    }
}
