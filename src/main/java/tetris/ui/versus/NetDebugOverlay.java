package tetris.ui.versus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.Timer;
import tetris.feature.network.lobby.LinkStats;
import tetris.feature.setting.Settings;

/**
 * 개발자 모드 연결 상태 오버레이 구동 (dev-net-overlay). EDT 전용.
 *
 * <p>{@link Settings#isDevNetworkOverlay()}가 켜져 있고 P2P 화면이 있을 때만 {@link Timer}(500ms)를 만들고
 * {@link #tick()}에서 {@link NetDebugText#lines(Optional)} 결과를 로비 패널과(온라인 대전 중이면) 경기 패널에 넘긴다.
 * 꺼지거나 닫히면 타이머를 멈추고 두 화면의 줄을 비운다. 로컬 대전·싱글 화면은 건드리지 않는다.
 *
 * <p>창 없이 검증할 수 있도록 패널을 {@link Supplier}로 늦게 받는다 (P2P 화면은 들어갈 때 만들어진다).
 */
public final class NetDebugOverlay implements AutoCloseable {
    /** 화면 갱신 주기 */
    public static final int PERIOD_MILLIS = 500;

    private final Settings settings;
    private final Supplier<? extends tetris.feature.network.lobby.LobbyClient> lobby;
    private final Supplier<P2pLobbyPanel> lobbyPanel;
    private final Supplier<BattlePanel> battlePanel;
    private Timer timer;
    private boolean closed;

    /**
     * lobby: 지금 P2P 연결(없으면 null). lobbyPanel: P2P 로비 화면(없으면 null).
     * battlePanel: 온라인 대전 중인 경기 화면(로컬 대전·다른 화면이면 null)
     */
    public NetDebugOverlay(Settings settings,
                           Supplier<? extends tetris.feature.network.lobby.LobbyClient> lobby,
                           Supplier<P2pLobbyPanel> lobbyPanel,
                           Supplier<BattlePanel> battlePanel) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.lobby = Objects.requireNonNull(lobby, "lobby");
        this.lobbyPanel = Objects.requireNonNull(lobbyPanel, "lobbyPanel");
        this.battlePanel = Objects.requireNonNull(battlePanel, "battlePanel");
    }

    /** 설정 값과 P2P 화면 유무에 맞춰 타이머를 켜고 끈다. 켜면 곧바로 한 번 갱신한다 */
    public void refresh() {
        if (closed) return;
        if (!settings.isDevNetworkOverlay() || lobbyPanel.get() == null) {
            stopAndClear();
            return;
        }
        if (timer == null) {
            timer = new Timer(PERIOD_MILLIS, event -> tick());
            timer.setRepeats(true);
        }
        if (!timer.isRunning()) timer.start();
        tick();
    }

    /** 타이머가 돌고 있는지. 설정이 꺼져 있거나 P2P 화면이 없으면 false다 */
    public boolean isRunning() {
        return timer != null && timer.isRunning();
    }

    /** 지금 통계로 두 화면의 줄을 갱신한다 (타이머 콜백과 테스트가 같은 경로를 쓴다) */
    public void tick() {
        if (closed || !settings.isDevNetworkOverlay()) return;
        List<String> lines;
        try {
            lines = NetDebugText.lines(clockStats());
        } catch (RuntimeException ignored) {
            return; // 통계 조회가 실패해도 경기·로비 화면을 막지 않는다
        }
        BattlePanel battleView = battlePanel.get();
        if (battleView != null) battleView.setDebugLines(lines);
    }

    private Optional<LinkStats> clockStats() {
        tetris.feature.network.lobby.LobbyClient current = lobby.get();
        return current == null ? Optional.empty() : current.linkStats();
    }

    /** 타이머를 멈추고 P2P 두 화면의 줄을 비운다 */
    public void stopAndClear() {
        if (timer != null) timer.stop();
        BattlePanel battleView = battlePanel.get();
        if (battleView != null && !battleView.debugLines().isEmpty()) battleView.setDebugLines(List.of());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        stopAndClear();
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}
