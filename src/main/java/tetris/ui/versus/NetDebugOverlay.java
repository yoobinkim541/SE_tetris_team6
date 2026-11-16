package tetris.ui.versus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.Timer;
import tetris.feature.network.lobby.LinkStats;
import tetris.feature.network.lobby.LobbyClient;

/**
 * 개발자 모드 연결 상태 오버레이 구동 (dev-net-overlay). EDT 전용.
 *
 * <p>개발자 모드가 켜져 있고 P2P 화면이 있을 때만 {@link Timer}(500ms)를 만들고 {@link #tick()}에서
 * {@link NetDebugText#lines(Optional)} 결과를 로비 측정 패널과(온라인 대전 중이면) 경기 패널에 넘긴다.
 * 꺼지거나 닫히면 타이머를 멈추고 두 화면의 줄을 비운다. 로컬 대전·싱글 화면은 건드리지 않는다.
 *
 * <p>창 없이 검증할 수 있도록 화면과 연결을 {@link Supplier}로 늦게 받는다 (P2P 화면은 들어갈 때 만들어진다).
 * 설정 토글과 {@code --dev}를 합친 판단은 호출자({@code DevMode::enabled})가 넘긴다.
 */
public final class NetDebugOverlay implements AutoCloseable {
    /** 화면 갱신 주기 */
    public static final int PERIOD_MILLIS = 500;

    private final BooleanSupplier enabled;
    private final Supplier<? extends LobbyClient> lobby;
    private final Supplier<DevRttPanel> lobbyPanel;
    private final Supplier<BattlePanel> battlePanel;
    private Timer timer;
    private boolean closed;

    /**
     * enabled: 개발자 모드 여부(--dev 또는 설정 토글). lobby: 지금 P2P 연결(없으면 null).
     * lobbyPanel: P2P 로비 측정 패널(로비 화면이 없으면 null). battlePanel: 온라인 대전 중인 경기 화면
     * (로컬 대전·다른 화면이면 null)
     */
    public NetDebugOverlay(BooleanSupplier enabled,
                           Supplier<? extends LobbyClient> lobby,
                           Supplier<DevRttPanel> lobbyPanel,
                           Supplier<BattlePanel> battlePanel) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.lobby = Objects.requireNonNull(lobby, "lobby");
        this.lobbyPanel = Objects.requireNonNull(lobbyPanel, "lobbyPanel");
        this.battlePanel = Objects.requireNonNull(battlePanel, "battlePanel");
    }

    /** 개발자 모드와 P2P 화면 유무에 맞춰 타이머를 켜고 끈다. 켜면 곧바로 한 번 갱신한다 */
    public void refresh() {
        if (closed) return;
        if (!enabled.getAsBoolean() || lobbyPanel.get() == null) {
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

    /** 타이머가 돌고 있는지. 개발자 모드가 꺼져 있거나 P2P 화면이 없으면 false다 */
    public boolean isRunning() {
        return timer != null && timer.isRunning();
    }

    /** 지금 통계로 두 화면의 줄을 갱신한다 (타이머 콜백과 테스트가 같은 경로를 쓴다) */
    public void tick() {
        if (closed || !enabled.getAsBoolean()) return;
        List<String> lines;
        try {
            lines = NetDebugText.lines(clockStats());
        } catch (RuntimeException ignored) {
            return; // 통계 조회가 실패해도 경기·로비 화면을 막지 않는다
        }
        DevRttPanel lobbyView = lobbyPanel.get();
        if (lobbyView != null) lobbyView.setDebugLines(lines);
        BattlePanel battleView = battlePanel.get();
        if (battleView != null) battleView.setDebugLines(lines);
    }

    private Optional<LinkStats> clockStats() {
        LobbyClient current = lobby.get();
        return current == null ? Optional.empty() : current.linkStats();
    }

    /** 타이머를 멈추고 P2P 두 화면의 줄을 비운다 */
    public void stopAndClear() {
        if (timer != null) timer.stop();
        DevRttPanel lobbyView = lobbyPanel.get();
        if (lobbyView != null && !lobbyView.debugLines().isEmpty()) lobbyView.setDebugLines(List.of());
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
