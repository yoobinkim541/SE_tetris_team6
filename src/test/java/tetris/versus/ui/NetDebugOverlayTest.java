package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.network.lobby.LinkHealth;
import tetris.feature.network.lobby.LinkStats;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.VersusRules;
import tetris.ui.versus.BattlePanel;
import tetris.ui.versus.DevRttPanel;
import tetris.ui.versus.NetDebugOverlay;
import tetris.ui.versus.NetDebugText;

/**
 * 개발자 모드 연결 상태 오버레이 (dev-net-overlay). 창 없이 패널과 구동 객체만 검증한다.
 * 타이머는 만들기만 하고 콜백을 기다리지 않는다: 테스트는 {@link NetDebugOverlay#tick()}을 직접 부른다.
 */
@Timeout(30)
class NetDebugOverlayTest {
    @TempDir Path directory;
    private static final MatchSnapshot COUNTDOWN = countdown();

    /** linkStats만 흉내 내는 로비. 나머지 명령은 쓰지 않는다 */
    private static final class FakeLobby implements LobbyClient {
        Optional<LinkStats> stats = Optional.empty();

        @Override public CompletionStage<Void> host(int port) { throw new UnsupportedOperationException(); }
        @Override public CompletionStage<Void> connect(String address, int port) { throw new UnsupportedOperationException(); }
        @Override public void cancel() { }
        @Override public void selectConfig(MatchConfig config) { }
        @Override public void ready(long configRevision, boolean ready) { }
        @Override public void leave() { }
        @Override public Optional<BattleClient> battleClient() { return Optional.empty(); }
        @Override public void returnToLobby() { }
        @Override public Subscription subscribe(Consumer<LobbySnapshot> listener) {
            return () -> { };
        }
        @Override public Optional<LinkStats> linkStats() { return stats; }
        @Override public void close() { }
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

    private DevRttPanel lobbyPanel(FakeLobby lobby) {
        return new DevRttPanel(lobby, directory);
    }

    private static BattlePanel battlePanel() {
        var panel = new BattlePanel(false, "guide", "일반 대전 · P2P", BattleMode.CLASSIC, null);
        panel.setSnapshot(COUNTDOWN);
        return panel;
    }

    @Test
    void 꺼져_있으면_타이머가_없고_두_화면_줄이_비어_있다() {
        FakeLobby lobby = new FakeLobby();
        lobby.stats = Optional.of(stats());
        DevRttPanel lobbyView = lobbyPanel(lobby);
        BattlePanel battleView = battlePanel();
        NetDebugOverlay overlay = new NetDebugOverlay(() -> false, () -> lobby, () -> lobbyView, () -> battleView);

        overlay.refresh();

        assertFalse(overlay.isRunning(), "개발자 모드가 꺼져 있으면 타이머를 만들지 않는다");
        assertTrue(lobbyView.debugLines().isEmpty());
        assertTrue(battleView.debugLines().isEmpty());
        overlay.close();
        lobbyView.close();
    }

    @Test
    void 켜면_tick마다_로비와_대전_화면에_같은_4줄이_들어간다() {
        FakeLobby lobby = new FakeLobby();
        lobby.stats = Optional.of(stats());
        DevRttPanel lobbyView = lobbyPanel(lobby);
        BattlePanel battleView = battlePanel();
        NetDebugOverlay overlay = new NetDebugOverlay(() -> true, () -> lobby, () -> lobbyView, () -> battleView);

        overlay.refresh();
        assertTrue(overlay.isRunning());
        overlay.tick();

        List<String> expected = NetDebugText.lines(Optional.of(stats()));
        assertEquals(4, expected.size());
        assertEquals(expected, lobbyView.debugLines());
        assertEquals(expected, battleView.debugLines(), "온라인 대전 화면도 로비와 같은 줄을 받는다");

        overlay.close();
        assertFalse(overlay.isRunning(), "닫으면 타이머가 멈춘다");
        assertTrue(lobbyView.debugLines().isEmpty());
        assertTrue(battleView.debugLines().isEmpty());
        lobbyView.close();
    }

    @Test
    void 연결이_없으면_DEV_연결_없음_한_줄() {
        FakeLobby lobby = new FakeLobby();
        DevRttPanel lobbyView = lobbyPanel(lobby);
        NetDebugOverlay overlay = new NetDebugOverlay(() -> true, () -> lobby, () -> lobbyView, () -> null);

        overlay.refresh();
        overlay.tick();

        assertEquals(List.of("[DEV] 연결 없음"), lobbyView.debugLines());
        overlay.close();
        lobbyView.close();
    }

    @Test
    void 끄면_refresh에서_타이머가_멈추고_줄이_지워진다() {
        FakeLobby lobby = new FakeLobby();
        lobby.stats = Optional.of(stats());
        DevRttPanel lobbyView = lobbyPanel(lobby);
        boolean[] enabled = {true};
        NetDebugOverlay overlay = new NetDebugOverlay(() -> enabled[0], () -> lobby, () -> lobbyView, () -> null);

        overlay.refresh();
        overlay.tick();
        assertFalse(lobbyView.debugLines().isEmpty());

        enabled[0] = false;
        overlay.refresh();
        assertFalse(overlay.isRunning(), "끄면 타이머가 멈춘다");
        assertTrue(lobbyView.debugLines().isEmpty(), "끄면 줄이 바로 사라진다");

        enabled[0] = true;
        overlay.refresh();
        assertTrue(overlay.isRunning(), "다시 켜면 타이머가 돈다");
        overlay.close();
        lobbyView.close();
    }

    @Test
    void 대전_화면_공급자가_null이면_로비만_갱신한다() {
        FakeLobby lobby = new FakeLobby();
        lobby.stats = Optional.of(stats());
        DevRttPanel lobbyView = lobbyPanel(lobby);
        NetDebugOverlay overlay = new NetDebugOverlay(() -> true, () -> lobby, () -> lobbyView, () -> null);

        overlay.refresh();
        overlay.tick();

        assertEquals(NetDebugText.lines(Optional.of(stats())), lobbyView.debugLines());
        overlay.stopAndClear();
        assertTrue(lobbyView.debugLines().isEmpty(), "null 공급자여도 멈출 때 로비 줄은 지운다");
        overlay.close();
        lobbyView.close();
    }

    @Test
    void 로비_패널이_없으면_타이머를_시작하지_않는다() {
        FakeLobby lobby = new FakeLobby();
        NetDebugOverlay overlay = new NetDebugOverlay(() -> true, () -> lobby, () -> null, () -> null);

        overlay.refresh();
        assertFalse(overlay.isRunning(), "P2P 화면이 없으면 타이머를 만들지 않는다");
        overlay.close();
    }

    /* ── DevRttPanel: RTT 통계 아래 연결 상태 줄 ───────────────── */

    @Test
    void 로비_측정_패널은_줄이_있을_때만_라벨을_보인다() {
        DevRttPanel panel = lobbyPanel(new FakeLobby());
        JLabel label = panel.debugLabel();
        assertFalse(label.isVisible(), "처음에는 숨김");

        panel.setDebugLines(List.of("[DEV] 연결 없음"));
        assertEquals(List.of("[DEV] 연결 없음"), panel.debugLines());
        assertTrue(label.isVisible());
        assertTrue(label.getText().contains("[DEV] 연결 없음"), label.getText());

        panel.setDebugLines(NetDebugText.lines(Optional.of(stats())));
        assertTrue(label.getText().contains("RTT 12ms"), "여러 줄은 HTML로 줄바꿈한다: " + label.getText());

        panel.setDebugLines(List.of());
        assertTrue(panel.debugLines().isEmpty());
        assertFalse(label.isVisible());
        panel.close();
    }

    @Test
    void 경기_화면은_줄을_보관하고_빈_목록이면_그리지_않는다() {
        BattlePanel panel = battlePanel();
        int[] before = render(panel);

        panel.setDebugLines(List.of("[DEV] 호스트 · 192.168.0.11 · 정상", "RTT 12ms"));
        assertEquals(List.of("[DEV] 호스트 · 192.168.0.11 · 정상", "RTT 12ms"), panel.debugLines());
        assertNotEquals(hash(before), hash(render(panel)), "줄이 있으면 화면이 달라진다");

        panel.setDebugLines(List.of());
        assertEquals(hash(before), hash(render(panel)), "빈 목록은 설정 전과 같은 화면이다");
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
}
