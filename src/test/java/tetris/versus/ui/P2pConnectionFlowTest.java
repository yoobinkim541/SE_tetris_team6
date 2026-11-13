package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tetris.feature.network.lobby.LinkHealth;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.versus.api.*;
import tetris.ui.versus.*;

/** S10-B2: 지연·끊김 안내, 최근 IP, 결과 뒤 로비 복귀와 다시 대전 (R3-LAG, R3-DISCONNECT, R3-RECENT, R3-REMATCH) */
class P2pConnectionFlowTest {
    /** 연결 상태까지 알리는 가짜 로비. 실제 로비처럼 INITIAL 스냅샷을 먼저, 종료 상태를 나중에 보낸다 */
    static final class HealthLobby extends P2pLobbyControllerTest.FakeLobby {
        final List<Consumer<LinkHealth>> health = new ArrayList<>();
        LinkHealth current = LinkHealth.NONE;

        void health(LinkHealth next) {
            current = next;
            for (var l : List.copyOf(health)) l.accept(next);
        }

        void drop(LinkHealth.Status status) {
            publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
            health(new LinkHealth(status, 40));
        }

        @Override public Subscription subscribeHealth(Consumer<LinkHealth> listener) {
            health.add(listener);
            listener.accept(current);
            return () -> health.remove(listener);
        }
    }

    static final class Nav extends P2pLobbyControllerTest.Nav {
        final List<LinkHealth> healths = new ArrayList<>();
        @Override public void healthChanged(LinkHealth health) { healths.add(health); }
    }

    private final HealthLobby lobby = new HealthLobby();
    private final Nav nav = new Nav();

    private P2pLobbyController controller(List<String> saved) {
        return new P2pLobbyController(lobby, nav, () -> 50123, () -> List.of("192.168.0.7"), () -> saved);
    }

    private void connectAsClient(P2pLobbyController c, String address) {
        c.connect(address, "50123");
        lobby.publish(new LobbySnapshot(Role.CLIENT, State.LOBBY, null, 0, false, false, address + "#1"));
        lobby.health(new LinkHealth(LinkHealth.Status.NORMAL, -1));
    }

    private void playToResult(P2pLobbyController c) {
        var config = MatchConfig.of(BattleMode.CLASSIC);
        lobby.publish(new LobbySnapshot(Role.CLIENT, State.LOBBY, config, 1, false, false, "10.0.0.2#1"));
        lobby.battle = new FakeBattleClient();
        lobby.publish(new LobbySnapshot(Role.CLIENT, State.PLAYING, config, 1, false, false, "10.0.0.2#1"));
        lobby.publish(new LobbySnapshot(Role.CLIENT, State.RESULT, config, 1, false, false, "10.0.0.2#1"));
    }

    @Test
    void savedRecentPeersAreOfferedAndPickingOneFillsTheAddress() {
        var c = controller(List.of("10.0.0.2", "10.0.0.3", "10.0.0.2", " "));
        assertEquals(List.of("10.0.0.2", "10.0.0.3"), c.recentPeers(), "중복·빈 값 제외, 순서 유지");
        var box = c.panel().recentBox();
        assertEquals(2, box.getItemCount());
        assertTrue(box.isEnabled());
        box.setSelectedItem("10.0.0.3");
        assertEquals("10.0.0.3", c.panel().addressField().getText());
    }

    @Test
    void unreadableRecentFileStillAllowsTypingAnAddress() {
        var c = new P2pLobbyController(lobby, nav, () -> -1, List::of, () -> { throw new IllegalStateException("io"); });
        assertTrue(c.recentPeers().isEmpty());
        assertFalse(c.panel().recentBox().isEnabled());
        assertTrue(c.panel().addressField().isEnabled());
    }

    @Test
    void onlyASuccessfulClientConnectMovesTheAddressToTheFrontAndCapsAtFive() {
        var c = controller(List.of("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5"));
        c.connect("10.0.0.9", "50123");
        lobby.pending.completeExceptionally(new RuntimeException("refused"));
        lobby.publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
        assertEquals("10.0.0.1", c.recentPeers().getFirst(), "실패한 주소는 남기지 않는다");
        connectAsClient(c, "10.0.0.4");
        assertEquals(List.of("10.0.0.4", "10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.5"), c.recentPeers());
        c.cancelOrBack();
        connectAsClient(c, "10.0.0.8");
        assertEquals(5, c.recentPeers().size());
        assertEquals("10.0.0.8", c.recentPeers().getFirst());
        assertFalse(c.recentPeers().contains("10.0.0.5"), "가장 오래된 주소가 빠진다");
    }

    @Test
    void lagIsShownAndClearedWithoutLeavingTheScreen() {
        var c = controller(List.of());
        connectAsClient(c, "10.0.0.2");
        lobby.health(new LinkHealth(LinkHealth.Status.LAGGING, 260));
        assertTrue(c.panel().statusText().contains("네트워크 지연 중 (응답 260ms)"), c.panel().statusText());
        assertEquals(LinkHealth.Status.LAGGING, nav.healths.getLast().status());
        assertEquals("네트워크 지연 중 (응답 260ms)", P2pLobbyController.lagText(nav.healths.getLast()));
        lobby.health(new LinkHealth(LinkHealth.Status.NORMAL, 90));
        assertFalse(c.panel().statusText().contains("지연"), c.panel().statusText());
        assertEquals("", P2pLobbyController.lagText(nav.healths.getLast()));
        assertTrue(nav.events.isEmpty() || !nav.events.contains("lost"));
    }

    @Test
    void lossDuringTheMatchReturnsToTheFirstScreenWithAnErrorAndFocus() {
        var c = controller(List.of());
        connectAsClient(c, "10.0.0.2");
        var config = MatchConfig.of(BattleMode.ITEM);
        lobby.battle = new FakeBattleClient();
        lobby.publish(new LobbySnapshot(Role.CLIENT, State.PLAYING, config, 1, false, false, "10.0.0.2#1"));
        lobby.drop(LinkHealth.Status.LOST);
        assertEquals(List.of("start", "lost"), nav.events.stream().map(e -> e.split(" ")[0]).toList());
        assertTrue(c.isError());
        assertTrue(c.panel().isError());
        assertTrue(c.panel().statusText().startsWith("상대와 연결이 끊어졌습니다."), c.panel().statusText());
        assertTrue(c.panel().hostButton().isEnabled(), "P2P 첫 화면이라 다시 대기·접속할 수 있다");
        assertSame(c.panel().addressField(), c.panel().focusTarget());
    }

    @Test
    void aPeerLeavingNormallyIsNotReportedAsAnError() {
        var c = controller(List.of());
        connectAsClient(c, "10.0.0.2");
        lobby.drop(LinkHealth.Status.PEER_LEFT);
        assertEquals("상대가 나갔습니다.", c.panel().statusText());
        assertFalse(c.isError());
    }

    @Test
    void resultReturnsToTheLobbyAndShowsThatOldVotesWereCleared() {
        var c = controller(List.of());
        connectAsClient(c, "10.0.0.2");
        playToResult(c);
        c.returnToLobby();
        assertEquals(State.LOBBY, c.snapshot().state());
        assertFalse(c.snapshot().localReady());
        assertTrue(c.panel().statusText().contains("이전 판 준비는 해제되었습니다"), c.panel().statusText());
        assertTrue(c.panel().readyText().startsWith("나: 대기"), c.panel().readyText());
        assertSame(c.panel().readyButton(), c.panel().focusTarget());
    }

    @Test
    void rematchReturnsToTheSameConnectionAndVotesOnce() {
        var c = controller(List.of());
        connectAsClient(c, "10.0.0.2");
        playToResult(c);
        lobby.calls.clear();
        c.rematch();
        assertEquals(List.of("returnToLobby", "ready 1 true"), lobby.calls, "같은 연결, 새로 접속하지 않음");
        assertTrue(c.snapshot().localReady());
        assertTrue(c.panel().statusText().startsWith("다시 대전을 신청했습니다"), c.panel().statusText());
        c.rematch();
        assertEquals(List.of("returnToLobby", "ready 1 true"), lobby.calls, "로비에서 다시 눌러도 준비를 끄지 않는다");
    }

    @Test
    void rematchAfterThePeerLeftStaysOnTheFirstScreen() {
        var c = controller(List.of());
        connectAsClient(c, "10.0.0.2");
        playToResult(c);
        lobby.drop(LinkHealth.Status.PEER_LEFT);
        lobby.calls.clear();
        c.rematch();
        assertTrue(lobby.calls.isEmpty(), lobby.calls.toString());
        assertEquals(State.INITIAL, c.snapshot().state());
        assertEquals("상대가 나갔습니다.", c.panel().statusText());
    }

    @Test
    void closingStopsHealthUpdates() {
        var c = controller(List.of());
        c.close();
        assertTrue(lobby.health.isEmpty());
    }

    @Test
    void onlineResultOffersRematchOnEnterAndLobbyOnL() {
        var calls = new ArrayList<String>();
        var panel = new BattleResultPanel("로비로", () -> calls.add("lobby"), "다시 대전", () -> calls.add("rematch"),
                () -> calls.add("main"));
        assertEquals("Enter: 다시 대전   L: 로비로   Esc: 메인 메뉴", panel.getHintText());
        var inputs = panel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        for (int key : new int[]{java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.VK_L, java.awt.event.KeyEvent.VK_ESCAPE}) {
            Object name = inputs.get(javax.swing.KeyStroke.getKeyStroke(key, 0));
            panel.getActionMap().get(name).actionPerformed(null);
        }
        assertEquals(List.of("rematch", "lobby", "main"), calls);
        assertThrows(IllegalArgumentException.class,
                () -> new BattleResultPanel("로비로", () -> { }, "다시 대전", null, () -> { }));
    }

    @Test
    void battleScreenShowsTheConnectionNotice() {
        var panel = new BattlePanel(false, BattleKeyMap.online().guide(), "일반 대전", BattleMode.CLASSIC, PlayerId.TWO);
        panel.setConnectionNotice("네트워크 지연 중 (응답 260ms)");
        assertEquals("네트워크 지연 중 (응답 260ms)", panel.connectionNotice());
        panel.setSnapshot(FakeBattleClient.sample());
        panel.setSize(960, 720);
        var image = new java.awt.image.BufferedImage(960, 720, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        panel.setConnectionNotice("");
        assertEquals("", panel.connectionNotice());
    }
}
