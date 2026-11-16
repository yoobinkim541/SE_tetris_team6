package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tetris.feature.network.EstablishException;
import tetris.feature.network.EstablishFailure;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.versus.api.*;
import tetris.ui.versus.*;

/** S9-B2: 접속·로비 화면 동작 (R3-MENU, R3-HOST, R3-READY) */
class P2pLobbyControllerTest {
    static class FakeLobby implements LobbyClient {
        final List<String> calls = new ArrayList<>();
        final List<Consumer<LobbySnapshot>> listeners = new ArrayList<>();
        LobbySnapshot snapshot = new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null);
        CompletableFuture<Void> pending;
        BattleClient battle;
        boolean closed;

        void publish(LobbySnapshot next) {
            snapshot = next;
            for (var l : List.copyOf(listeners)) l.accept(next);
        }
        @Override public CompletionStage<Void> host(int port) {
            calls.add("host " + port);
            pending = new CompletableFuture<>();
            publish(new LobbySnapshot(Role.HOST, State.HOSTING, null, 0, false, false, null));
            return pending;
        }
        @Override public CompletionStage<Void> connect(String address, int port) {
            calls.add("connect " + address + ":" + port);
            pending = new CompletableFuture<>();
            publish(new LobbySnapshot(Role.CLIENT, State.CONNECTING, null, 0, false, false, null));
            return pending;
        }
        @Override public void cancel() {
            calls.add("cancel");
            publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
        }
        @Override public void selectConfig(MatchConfig config) {
            calls.add("config " + config.mode());
            publish(snapshot.selectConfig(config));
        }
        @Override public void ready(long revision, boolean ready) {
            calls.add("ready " + revision + " " + ready);
            publish(snapshot.readyAtRevision(revision, ready));
        }
        @Override public void leave() {
            calls.add("leave");
            publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
        }
        @Override public Optional<BattleClient> battleClient() {
            calls.add("battleClient");
            return Optional.ofNullable(battle);
        }
        @Override public void returnToLobby() {
            calls.add("returnToLobby");
            publish(new LobbySnapshot(snapshot.role(), State.LOBBY, snapshot.config(), snapshot.configRevision(),
                    false, snapshot.peerReady(), snapshot.peerIdentity()));
        }
        @Override public Subscription subscribe(Consumer<LobbySnapshot> listener) {
            listeners.add(listener);
            listener.accept(snapshot);
            return () -> listeners.remove(listener);
        }
        @Override public void close() { closed = true; calls.add("close"); }
    }

    static class Nav implements P2pLobbyController.Navigation {
        final List<String> events = new ArrayList<>();
        @Override public void startMatch(BattleClient client, MatchConfig config, Role role) {
            events.add("start " + config.mode() + " " + role);
        }
        @Override public void connectionLost() { events.add("lost"); }
        @Override public void back() { events.add("back"); }
    }

    final FakeLobby lobby = new FakeLobby();
    final Nav nav = new Nav();
    final P2pLobbyController controller = new P2pLobbyController(lobby, nav, () -> 50123, () -> List.of("192.168.0.7"));
    final P2pLobbyPanel panel = controller.panel();

    void enterLobby(Role role) {
        lobby.pending.complete(null);
        lobby.publish(new LobbySnapshot(role, State.LOBBY, null, 0, false, false, "192.168.0.9#1"));
    }

    @Test
    void hostShowsItsAddressThenConfiguresTheDefaultModeOnConnect() {
        assertTrue(panel.hostButton().isEnabled());
        assertFalse(panel.readyButton().isEnabled());
        panel.portField().setText("50123");
        panel.hostButton().doClick();
        assertEquals(List.of("host 50123"), lobby.calls);
        assertTrue(panel.statusText().contains("192.168.0.7"), panel.statusText());
        assertTrue(panel.statusText().contains("50123"));
        assertEquals("취소", panel.backButton().getText());
        assertFalse(panel.hostButton().isEnabled(), "no second host while waiting");

        enterLobby(Role.HOST);
        assertTrue(lobby.calls.contains("config CLASSIC"), "host configures immediately so both can press start");
        assertTrue(panel.statusText().startsWith("연결 성공!"), panel.statusText());
        assertTrue(panel.peerText().contains("192.168.0.9"));
        assertFalse(panel.peerText().contains("#"));
        assertTrue(panel.modeBox().isEnabled(), "host chooses the mode");
        assertTrue(panel.readyButton().isEnabled());

        panel.modeBox().setSelectedItem(BattleMode.TIMED);
        assertTrue(lobby.calls.contains("config TIMED"));
        assertEquals(2, lobby.snapshot.configRevision());
    }

    @Test
    void clientCannotEditTheModeAndSeesTheHostChoice() {
        panel.addressField().setText(" 192.168.0.9 ");
        panel.connectButton().doClick();
        assertEquals("connect 192.168.0.9:47123", lobby.calls.getFirst());
        assertEquals("접속 중…", panel.statusText());
        enterLobby(Role.CLIENT);
        assertFalse(panel.modeBox().isEnabled());
        assertFalse(panel.readyButton().isEnabled(), "no READY before the host's config");
        lobby.publish(new LobbySnapshot(Role.CLIENT, State.LOBBY, MatchConfig.of(BattleMode.ITEM), 1, false, false, "192.168.0.9#1"));
        assertEquals(BattleMode.ITEM, panel.modeBox().getSelectedItem());
        assertFalse(panel.modeBox().isEnabled());
        panel.modeBox().setSelectedItem(BattleMode.TIMED);
        assertFalse(lobby.calls.stream().anyMatch(c -> c.startsWith("config")), "a client never sends CONFIG");
        assertTrue(panel.readyButton().isEnabled());
    }

    @Test
    void startButtonTogglesReadyAtTheCurrentRevisionAndStartsTheMatchOnce() {
        panel.hostButton().doClick();
        enterLobby(Role.HOST);
        panel.readyButton().doClick();
        assertTrue(lobby.calls.contains("ready 1 true"));
        assertEquals("준비 취소", panel.readyButton().getText());
        assertTrue(panel.readyText().contains("나: 준비 완료"));
        panel.readyButton().doClick();
        assertTrue(lobby.calls.contains("ready 1 false"));

        panel.readyButton().doClick();
        lobby.battle = new FakeBattleClient();
        var s = lobby.snapshot;
        lobby.publish(new LobbySnapshot(Role.HOST, State.STARTING, s.config(), 1, true, true, s.peerIdentity()));
        lobby.publish(new LobbySnapshot(Role.HOST, State.PLAYING, s.config(), 1, true, true, s.peerIdentity()));
        assertEquals(List.of("start CLASSIC HOST"), nav.events, "exactly one handoff");
        assertFalse(panel.readyButton().isEnabled(), "no duplicate start during the match");
    }

    @Test
    void connectionLossDuringTheMatchReturnsWithANotice() {
        panel.hostButton().doClick();
        enterLobby(Role.HOST);
        lobby.battle = new FakeBattleClient();
        var s = lobby.snapshot;
        lobby.publish(new LobbySnapshot(Role.HOST, State.STARTING, s.config(), 1, true, true, s.peerIdentity()));
        lobby.publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
        assertEquals(List.of("start CLASSIC HOST", "lost"), nav.events);
        assertEquals("상대와 연결이 끊어졌습니다.", panel.statusText());
        assertTrue(panel.hostButton().isEnabled(), "can host again");
    }

    @Test
    void cancelWhileConnectingIgnoresTheLateFailureAndBackLeavesTheScreen() {
        panel.addressField().setText("10.0.0.5");
        panel.connectButton().doClick();
        CompletableFuture<Void> late = lobby.pending;
        panel.backButton().doClick();
        assertTrue(lobby.calls.contains("cancel"));
        late.completeExceptionally(new EstablishException(EstablishFailure.TIMEOUT, "late"));
        assertEquals("취소했습니다.", panel.statusText(), "a stale failure must not overwrite the screen");
        panel.backButton().doClick();
        assertEquals(List.of("back"), nav.events);
    }

    @Test
    void failuresAndInvalidInputAreExplained() {
        panel.addressField().setText("10.0.0.5");
        panel.connectButton().doClick();
        lobby.publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
        lobby.pending.completeExceptionally(new EstablishException(EstablishFailure.REFUSED, "refused"));
        assertTrue(panel.statusText().contains("호스트가 먼저 대기"), panel.statusText());
        panel.portField().setText("70000");
        panel.hostButton().doClick();
        assertTrue(panel.statusText().contains("포트"));
        panel.portField().setText("47123");
        panel.addressField().setText("  ");
        panel.connectButton().doClick();
        assertTrue(panel.statusText().contains("IP"));
        assertEquals(1, lobby.calls.stream().filter(c -> c.startsWith("connect")).count());
    }

    @Test
    void leavingTheLobbyDisconnectsAndCloseReleasesTheLobby() {
        panel.hostButton().doClick();
        enterLobby(Role.HOST);
        assertEquals("나가기", panel.backButton().getText());
        panel.backButton().doClick();
        assertTrue(lobby.calls.contains("leave"));
        assertEquals("연결을 끊었습니다.", panel.statusText());
        controller.close();
        assertTrue(lobby.closed);
        assertTrue(lobby.listeners.isEmpty());
    }

    @Test
    void resultReturnsToTheSameLobby() {
        panel.hostButton().doClick();
        enterLobby(Role.HOST);
        lobby.battle = new FakeBattleClient();
        var s = lobby.snapshot;
        lobby.publish(new LobbySnapshot(Role.HOST, State.PLAYING, s.config(), 1, true, true, s.peerIdentity()));
        lobby.publish(new LobbySnapshot(Role.HOST, State.RESULT, s.config(), 1, false, false, s.peerIdentity()));
        controller.returnToLobby();
        assertTrue(lobby.calls.contains("returnToLobby"));
        assertEquals(State.LOBBY, controller.snapshot().state());
        assertTrue(panel.readyButton().isEnabled(), "ready again for the next match");
    }

    @Test
    void onlineKeyMapControlsOnlyTheLocalPlayer() {
        var keys = BattleKeyMap.online();
        assertTrue(keys.isOnline());
        assertTrue(keys.bindings().keySet().stream().allMatch(t -> t.playerId() == PlayerId.ONE));
        assertFalse(keys.guide().contains("P2"));
        assertFalse(new BattleKeyMap().isOnline());
    }

    /** Review I-1: 결과가 나온 뒤 상대가 나가도 결과 화면을 지우지 않는다 */
    @Test
    void peerLeavingAfterTheResultKeepsTheResultScreen() {
        panel.hostButton().doClick();
        enterLobby(Role.HOST);
        lobby.battle = new FakeBattleClient();
        var s = lobby.snapshot;
        lobby.publish(new LobbySnapshot(Role.HOST, State.PLAYING, s.config(), 1, true, true, s.peerIdentity()));
        lobby.publish(new LobbySnapshot(Role.HOST, State.RESULT, s.config(), 1, false, false, s.peerIdentity()));
        lobby.publish(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null));
        assertEquals(List.of("start CLASSIC HOST"), nav.events, "no navigation away from the result");
        assertEquals("상대가 나갔습니다.", panel.statusText());
        controller.returnToLobby();
        assertFalse(lobby.calls.contains("returnToLobby"), "nothing to return to on a closed connection");
        assertTrue(panel.hostButton().isEnabled());
    }
}
