package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.lobby.LinkHealth;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.relay.InternetRelay;
import tetris.feature.network.relay.RelayTransport;
import tetris.ui.versus.P2pLobbyController;
import tetris.ui.versus.P2pLobbyPanel;

/** 인터넷 대전 로비: 방 만들기·방 코드 참가·실패 안내·정리 (가짜 중계 서버) */
@Timeout(30)
class P2pInternetLobbyTest {
    /** 호스트 요청에는 방 코드, 참가 요청에는 정해 둔 응답을 바로 돌려주는 중계 */
    static final class StubRelay implements RelayTransport {
        String joinReply = "{\"op\":\"paired\"}";
        boolean unreachable;
        final List<String> requests = new ArrayList<>();
        int closedChannels;

        @Override
        public CompletableFuture<Channel> open(URI uri, java.time.Duration timeout, Listener listener) {
            if (unreachable) return CompletableFuture.failedFuture(new IOException("offline"));
            return CompletableFuture.completedFuture(new Channel() {
                @Override public CompletableFuture<Void> sendText(String text) {
                    requests.add(text);
                    listener.onText(text.contains("\"host\"") ? "{\"op\":\"room\",\"code\":\"4821\"}" : joinReply);
                    return CompletableFuture.completedFuture(null);
                }
                @Override public CompletableFuture<Void> sendBinary(byte[] bytes) {
                    return CompletableFuture.completedFuture(null);
                }
                @Override public void close() { closedChannels++; }
            });
        }
    }

    /** 접속 때 최근 주소에 남기는지까지 기록한다 */
    static final class RecordingLobby extends P2pLobbyControllerTest.FakeLobby {
        final List<LobbyClient.Subscription> health = new ArrayList<>();
        java.util.function.Consumer<LinkHealth> healthListener;

        @Override public CompletionStage<Void> connect(String address, int port, boolean rememberPeer) {
            calls.add("remember " + rememberPeer);
            return connect(address, port);
        }

        @Override public Subscription subscribeHealth(java.util.function.Consumer<LinkHealth> listener) {
            healthListener = listener;
            listener.accept(LinkHealth.NONE);
            return () -> healthListener = null;
        }
    }

    final StubRelay relayServer = new StubRelay();
    final RecordingLobby lobby = new RecordingLobby();
    final P2pLobbyControllerTest.Nav nav = new P2pLobbyControllerTest.Nav();
    final P2pLobbyController controller = new P2pLobbyController(lobby, nav, () -> 50123, () -> List.of("192.168.0.7"),
            List::of, () -> new InternetRelay(URI.create("ws://relay.test/relay"), Runnable::run, relayServer));
    final P2pLobbyPanel panel = controller.panel();

    @Test
    void 방_만들기는_빈_포트로_대기하고_방_코드를_보여_준다() {
        panel.hostInternetButton().doClick();
        assertEquals("host 0", lobby.calls.getFirst());
        assertTrue(panel.statusText().contains("4821"), panel.statusText());
        assertFalse(panel.joinInternetButton().isEnabled(), "대기 중에는 다른 접속을 시작하지 않는다");

        lobby.pending.complete(null);
        lobby.publish(new LobbySnapshot(Role.HOST, State.LOBBY, null, 0, false, false, "127.0.0.1#1"));
        assertTrue(panel.peerText().contains("인터넷 상대 (방 4821)"), panel.peerText());
        assertFalse(panel.peerText().contains("127.0.0.1"), "중계 루프백 주소는 보이지 않는다");
    }

    @Test
    void 방_코드로_참가하면_중계_루프백_포트로_접속하고_최근_주소에_남기지_않는다() {
        panel.codeField().setText(" 4821 ");
        panel.joinInternetButton().doClick();
        assertTrue(relayServer.requests.getFirst().contains("\"code\":\"4821\""));
        assertEquals("remember false", lobby.calls.getFirst());
        assertTrue(lobby.calls.get(1).startsWith("connect 127.0.0.1:"), lobby.calls.toString());
        assertEquals("방 4821에 연결 중…", panel.statusText());
    }

    @Test
    void 잘못된_방_코드는_중계에_묻지_않고_안내한다() {
        panel.codeField().setText("12a");
        panel.joinInternetButton().doClick();
        assertTrue(relayServer.requests.isEmpty());
        assertTrue(controller.isError());
        assertTrue(panel.statusText().contains("4자리"), panel.statusText());
    }

    @Test
    void 없는_방이면_안내하고_첫_화면으로_돌아간다() {
        relayServer.joinReply = "{\"op\":\"error\",\"reason\":\"NO_ROOM\"}";
        panel.codeField().setText("9999");
        panel.joinInternetButton().doClick();
        assertTrue(panel.statusText().contains("방 코드가 없거나"), panel.statusText());
        assertEquals(State.INITIAL, controller.snapshot().state());
        assertTrue(panel.joinInternetButton().isEnabled(), "다시 시도할 수 있다");
        assertFalse(lobby.calls.stream().anyMatch(c -> c.startsWith("connect")));
    }

    @Test
    void 중계_서버에_닿지_않으면_호스트_대기도_취소한다() {
        relayServer.unreachable = true;
        panel.hostInternetButton().doClick();
        assertTrue(lobby.calls.contains("cancel"), lobby.calls.toString());
        assertTrue(panel.statusText().contains("중계 서버에 연결할 수 없습니다"), panel.statusText());
        assertEquals(State.INITIAL, controller.snapshot().state());
    }

    @Test
    void 방_대기를_취소하면_중계_연결도_닫는다() {
        panel.hostInternetButton().doClick();
        panel.backButton().doClick();
        assertEquals(1, relayServer.closedChannels);
        assertTrue(panel.hostInternetButton().isEnabled());
    }

    @Test
    void 인터넷_대전_중_끊기면_인터넷_안내를_보인다() {
        panel.hostInternetButton().doClick();
        lobby.pending.complete(null);
        lobby.publish(new LobbySnapshot(Role.HOST, State.LOBBY, null, 0, false, false, "127.0.0.1#1"));
        lobby.publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
        lobby.healthListener.accept(new LinkHealth(LinkHealth.Status.LOST, -1));
        assertTrue(panel.statusText().contains("인터넷 연결을 확인"), panel.statusText());
        assertEquals(1, relayServer.closedChannels, "연결이 끝나면 중계도 닫는다");
    }
}
