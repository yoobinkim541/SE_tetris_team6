package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.relay.InternetRelay;
import tetris.feature.network.relay.RelayTransport;
import tetris.relay.LobbyCode;
import tetris.ui.versus.P2pLobbyController;
import tetris.ui.versus.P2pLobbyPanel;

/** 목록에서 공개 방은 바로, 비공개 방은 맞는 코드로만 참가한다. 네트워크는 가짜다 */
@Timeout(30)
class P2pRoomListTest {
    @Test
    void 목록의_공개_방을_고르면_그_서버의_번호로_참가한다() throws Exception {
        ScriptedRelay relay = new ScriptedRelay();
        P2pLobbyController controller = controller(relay);
        controller.joinPublic(0, "PUB1");
        assertTrue(relay.sent.stream().anyMatch(text -> text.contains("\"id\":\"PUB1\"")), relay.sent.toString());
        controller.close();
    }

    @Test
    void 비공개_방은_틀린_코드면_안내하고_맞는_코드면_참가한다() {
        ScriptedRelay relay = new ScriptedRelay();
        P2pLobbyController controller = controller(relay);
        controller.panel().codeField().setText("AAAAAA");
        relay.joinReply = "{\"op\":\"error\",\"reason\":\"NO_ROOM\"}";
        controller.panel().joinInternetButton().doClick();
        assertTrue(controller.panel().statusText().contains("방 코드가 없거나"), controller.panel().statusText());
        relay.joinReply = "{\"op\":\"paired\"}";
        controller.panel().codeField().setText("K7QM2X");
        controller.panel().joinInternetButton().doClick();
        assertTrue(relay.sent.stream().anyMatch(text -> text.contains("\"code\":\"K7QM2X\"")), relay.sent.toString());
        assertEquals(State.CONNECTING, controller.snapshot().state());
        controller.close();
    }

    @Test
    void 클릭_뒤에도_방향키와_Enter가_방을_고르고_참가한다() throws Exception {
        ScriptedRelay relay = new ScriptedRelay();
        P2pLobbyController controller = controller(relay);
        P2pLobbyPanel panel = controller.panel();
        panel.setRooms(List.of(
                new InternetRelay.ListedRoom(0, "메인", "A", "하나", "classic", false, 1),
                new InternetRelay.ListedRoom(0, "메인", "B", "둘", "item", true, 1)));
        panel.setPreferredSize(new java.awt.Dimension(640, 760));
        panel.setSize(640, 760);
        java.awt.Rectangle first = panel.hitScreenBounds("room-0");
        panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_MOVED, first.x + 8, first.y + 4));
        assertEquals("room-0", panel.selectedId());
        key(panel, KeyEvent.VK_DOWN);
        assertEquals("room-1", panel.selectedId());
        java.awt.Rectangle refresh = panel.hitScreenBounds("btn-refresh");
        panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, refresh.x + 8, refresh.y + 4));
        assertEquals("btn-refresh", panel.selectedId(), "새로고침 칸을 눌렀다");
        SwingUtilities.invokeAndWait(() -> { });
        key(panel, KeyEvent.VK_UP);
        assertEquals("room-1", panel.selectedId(), "새로고침 클릭 뒤에도 방향키가 목록으로 돌아온다");
        key(panel, KeyEvent.VK_ENTER);
        assertEquals("field-code", panel.selectedId(), "비공개 방은 코드 칸을 연다");
        controller.close();
    }

    private static P2pLobbyController controller(ScriptedRelay relay) {
        return new P2pLobbyController(new P2pLobbyControllerTest.FakeLobby(), new P2pLobbyControllerTest.Nav(),
                () -> 50123, List::of, List::of,
                () -> new InternetRelay(List.of(new InternetRelay.Route("메인", URI.create("tcp://relay"),
                        Duration.ofSeconds(1), relay, LobbyCode::normalize, LobbyCode::display)), Runnable::run));
    }

    private static MouseEvent mouse(P2pLobbyPanel panel, int id, int x, int y) {
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private static void key(P2pLobbyPanel panel, int code) {
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(panel,
                new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
    }

    private static final class ScriptedRelay implements RelayTransport {
        String joinReply = "{\"op\":\"paired\"}";
        final List<String> sent = new ArrayList<>();

        @Override
        public CompletableFuture<Channel> open(URI uri, Duration timeout, Listener listener) {
            return CompletableFuture.completedFuture(new Channel() {
                @Override public CompletableFuture<Void> sendText(String text) {
                    sent.add(text);
                    if (text.contains("\"op\":\"list\"")) {
                        listener.onText("{\"op\":\"rooms\",\"rooms\":["
                                + "{\"id\":\"A\",\"name\":\"하나\",\"mode\":\"classic\",\"private\":false,\"players\":1},"
                                + "{\"id\":\"B\",\"name\":\"둘\",\"mode\":\"item\",\"private\":true,\"players\":1}]}");
                    } else if (text.contains("\"host\"")) {
                        listener.onText("{\"op\":\"room\",\"code\":\"K7QM2X\"}");
                    } else {
                        listener.onText(joinReply);
                    }
                    return CompletableFuture.completedFuture(null);
                }
                @Override public CompletableFuture<Void> sendBinary(byte[] bytes) {
                    return CompletableFuture.completedFuture(null);
                }
                @Override public void close() {
                }
            });
        }
    }
}
