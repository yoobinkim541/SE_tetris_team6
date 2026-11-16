package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.lobby.LinkHealth;
import tetris.feature.network.lobby.LinkStats;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.VersusRules;
import tetris.ui.versus.BattlePanel;
import tetris.ui.versus.NetDebugOverlay;
import tetris.ui.versus.P2pLobbyPanel;

/**
 * 개발자 모드 연결 상태 오버레이 (dev-net-overlay). 창 없이 패널과 구동 객체만 검증한다.
 * 실제 창(JFrame)은 헤드리스에서 만들 수 없으므로 화면 연결은 이 구동 객체 단위로 확인한다.
 */
@Timeout(30)
class NetDebugOverlayTest {
    private static final MatchSnapshot COUNTDOWN = countdown();

    /** linkStats만 흉내 내는 로비. 나머지 명령은 쓰지 않는다 */
    private static final class FakeLobby implements LobbyClient {
        Optional<LinkStats> stats = Optional.empty();
        boolean closed;

        @Override public CompletionStage<Void> host(int port) { throw new UnsupportedOperationException(); }
        @Override public CompletionStage<Void> connect(String address, int port) { throw new UnsupportedOperationException(); }
        @Override public void cancel() { }
        @Override public void selectConfig(MatchConfig config) { }
        @Override public void ready(long configRevision, boolean ready) { }
        @Override public void leave() { }
        @Override public Optional<BattleClient> battleClient() { return Optional.empty(); }
        @Override public void returnToLobby() { }
        @Override public Subscription subscribe(Consumer<LobbySnapshot> listener) {
            listener.accept(new LobbySnapshot(Role.NONE, LobbySnapshot.State.INITIAL, null, 0, false, false, null));
            return () -> { };
        }
        @Override public Optional<LinkStats> linkStats() { return stats; }
        @Override public void close() { closed = true; }
    }

    private static MatchSnapshot countdown() {
        int[][] empty = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        var view = new MatchSnapshot.PlayerView(empty, new int[][] {{1}}, 0, List.of());
        return new MatchSnapshot("overlay-match", 1, view, view, -1, MatchPhase.COUNTDOWN, null);
    }

    private static LinkStats stats() {
        return new LinkStats(Role.HOST, "192.168.0.11", LinkHealth.Status.NORMAL, 12L, 3L, 8L, 21L, 20,
                120L, 31, 30, 140L, 139L);
    }

    private static P2pLobbyPanel.Actions noActions() {
        return new P2pLobbyPanel.Actions() {
            @Override public void host(String port) { }
            @Override public void connect(String address, String port) { }
            @Override public void selectMode(BattleMode mode) { }
            @Override public void toggleReady() { }
            @Override public void cancelOrBack() { }
        };
    }

    private static int[] render(BattlePanel panel) {
        panel.setSize(960, 720);
        BufferedImage image = new BufferedImage(960, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
        }
        return image.getRGB(0, 0, 960, 720, null, 0, 960);
    }

    private static int hash(int[] pixels) {
        int hash = 1;
        for (int pixel : pixels) hash = 31 * hash + pixel;
        return hash;
    }

    @Test
    void 경기_화면은_줄을_보관하고_빈_목록이면_그리지_않는다() {
        BattlePanel panel = new BattlePanel(false, "guide", "일반 대전", BattleMode.CLASSIC, null);
        panel.setSnapshot(COUNTDOWN);
        int[] before = render(panel);

        panel.setDebugLines(List.of("[DEV] 호스트 · 192.168.0.11 · 정상", "RTT 12ms"));
        assertEquals(List.of("[DEV] 호스트 · 192.168.0.11 · 정상", "RTT 12ms"), panel.debugLines());
        assertNotEquals(hash(before), hash(render(panel)), "줄이 있으면 화면이 달라진다");

        panel.setDebugLines(List.of());
        assertEquals(List.of(), panel.debugLines());
        assertEquals(hash(before), hash(render(panel)), "빈 목록은 설정 전과 같은 화면이다");
    }

    @Test
    void 로비_화면은_줄이_있을_때만_라벨을_보인다() {
        P2pLobbyPanel panel = new P2pLobbyPanel(noActions());
        JLabel label = panel.debugLabel();
        assertFalse(label.isVisible(), "처음에는 숨김");

        panel.setDebugLines(List.of("[DEV] 연결 없음"));
        assertEquals(List.of("[DEV] 연결 없음"), panel.debugLines());
        assertTrue(label.isVisible());
        assertTrue(label.getText().contains("[DEV] 연결 없음"));

        panel.setDebugLines(List.of("[DEV] 호스트 · 192.168.0.11 · 정상", "RTT 12ms"));
        assertTrue(label.getText().contains("RTT 12ms"), "여러 줄은 HTML로 줄바꿈한다");

        panel.setDebugLines(List.of());
        assertEquals(List.of(), panel.debugLines());
        assertFalse(label.isVisible());
    }

    @Test
    void 설정이_꺼져_있으면_타이머도_화면_갱신도_없다() {
        Settings settings = new Settings();
        FakeLobby lobby = new FakeLobby();
        lobby.stats = Optional.of(stats());
        P2pLobbyPanel panel = new P2pLobbyPanel(noActions());
        NetDebugOverlay overlay = new NetDebugOverlay(settings, () -> lobby, () -> panel, () -> null);

        overlay.refresh();
        assertFalse(overlay.isRunning());
        assertTrue(panel.debugLines().isEmpty());
        overlay.close();
    }

    @Test
    void 설정이_켜지면_타이머가_돌고_첫_줄이_DEV로_시작한다() {
        Settings settings = new Settings();
        settings.setDevNetworkOverlay(true);
        FakeLobby lobby = new FakeLobby();
        lobby.stats = Optional.of(stats());
        P2pLobbyPanel panel = new P2pLobbyPanel(noActions());
        BattlePanel battle = new BattlePanel(false, "guide", "일반 대전 · P2P", BattleMode.CLASSIC, null);
        battle.setSnapshot(COUNTDOWN);
        NetDebugOverlay overlay = new NetDebugOverlay(settings, () -> lobby, () -> panel, () -> battle);

        overlay.refresh();
        assertTrue(overlay.isRunning());
        overlay.tick();

        assertEquals(4, panel.debugLines().size());
        assertTrue(panel.debugLines().get(0).startsWith("[DEV]"), panel.debugLines().get(0));
        assertEquals(panel.debugLines(), battle.debugLines(), "온라인 대전 중이면 경기 화면도 같은 줄을 받는다");

        overlay.close();
        assertFalse(overlay.isRunning(), "닫으면 타이머가 멈춘다");
        assertTrue(panel.debugLines().isEmpty());
        assertTrue(battle.debugLines().isEmpty());
    }

    @Test
    void 설정을_끄면_타이머가_멈추고_줄이_비워진다() {
        Settings settings = new Settings();
        settings.setDevNetworkOverlay(true);
        FakeLobby lobby = new FakeLobby();
        lobby.stats = Optional.of(stats());
        P2pLobbyPanel panel = new P2pLobbyPanel(noActions());
        NetDebugOverlay overlay = new NetDebugOverlay(settings, () -> lobby, () -> panel, () -> null);

        overlay.refresh();
        overlay.tick();
        assertFalse(panel.debugLines().isEmpty());

        settings.setDevNetworkOverlay(false);
        overlay.refresh();
        assertFalse(overlay.isRunning());
        assertTrue(panel.debugLines().isEmpty());
        overlay.close();
    }

    @Test
    void 연결이_없으면_한_줄만_보여_준다() {
        Settings settings = new Settings();
        settings.setDevNetworkOverlay(true);
        P2pLobbyPanel panel = new P2pLobbyPanel(noActions());
        NetDebugOverlay overlay = new NetDebugOverlay(settings, FakeLobby::new, () -> panel, () -> null);

        overlay.refresh();
        overlay.tick();

        assertEquals(List.of("[DEV] 연결 없음"), panel.debugLines());
        overlay.close();
    }

    @Test
    void 로컬_경기_화면에는_절대_설정하지_않는다() {
        Settings settings = new Settings();
        settings.setDevNetworkOverlay(true);
        FakeLobby lobby = new FakeLobby();
        lobby.stats = Optional.of(stats());
        BattlePanel local = new BattlePanel(false, "guide", "일반 대전", BattleMode.CLASSIC, null);
        local.setSnapshot(COUNTDOWN);
        // 로컬 대전: P2P 로비·경기 패널이 없다 (supplier가 null)
        NetDebugOverlay overlay = new NetDebugOverlay(settings, () -> lobby, () -> null, () -> null);

        overlay.refresh();
        overlay.tick();

        assertTrue(local.debugLines().isEmpty(), "로컬 경기 화면은 오버레이를 받지 않는다");
        overlay.close();
        assertTrue(local.debugLines().isEmpty());
    }
}
