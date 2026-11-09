package tetris.ui.versus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import tetris.feature.network.EstablishException;
import tetris.feature.network.LanAddresses;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;

/**
 * P2P 로비 화면의 동작 (S9-B2). LobbyClient 구독과 화면 이동을 맡는다. EDT 전용.
 *
 * <ul>
 *   <li>취소·뒤로 뒤 늦게 끝난 접속 결과는 세대 번호로 무시한다</li>
 *   <li>호스트가 로비에 들어오면 고른 모드(기본 일반)를 바로 설정해 양쪽이 "게임 시작"을 누를 수 있게 한다.
 *       클라이언트는 모드를 바꿀 수 없다</li>
 *   <li>START 뒤 경기 화면으로 한 번만 넘어간다. 경기 중 연결이 끊기면 안내와 함께 로비 화면으로 돌아온다</li>
 * </ul>
 */
public final class P2pLobbyController implements P2pLobbyPanel.Actions, AutoCloseable {
    /** 경기 화면 이동 */
    public interface Navigation {
        /** 경기 시작. local은 이 PC가 조작하는 쪽 설명(P1/P2) */
        void startMatch(BattleClient client, MatchConfig config, Role role);
        /** 경기 중 연결이 끊겼다. 경기 화면을 닫고 로비 화면을 보여 줘야 한다 */
        void connectionLost();
        /** INITIAL에서 뒤로: P2P 화면을 떠난다 */
        void back();
    }

    private final LobbyClient lobby;
    private final P2pLobbyPanel panel;
    private final Navigation navigation;
    private final IntSupplier localPort;
    private final Supplier<List<String>> lanAddresses;
    private final LobbyClient.Subscription subscription;
    private LobbySnapshot current;
    private BattleMode selectedMode = BattleMode.CLASSIC;
    private String message = "";
    private long attempt;
    private boolean inMatch;
    private boolean closed;

    public P2pLobbyController(LobbyClient lobby, Navigation navigation) {
        this(lobby, navigation, () -> -1, LanAddresses::current);
    }

    /** localPort: 호스트가 실제로 연 포트(0으로 열었을 때 표시용). lanAddresses: 이 PC의 LAN 주소 후보 */
    public P2pLobbyController(LobbyClient lobby, Navigation navigation, IntSupplier localPort,
                              Supplier<List<String>> lanAddresses) {
        this.lobby = Objects.requireNonNull(lobby, "lobby");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.localPort = Objects.requireNonNull(localPort, "localPort");
        this.lanAddresses = Objects.requireNonNull(lanAddresses, "lanAddresses");
        this.panel = new P2pLobbyPanel(this);
        this.subscription = lobby.subscribe(this::onSnapshot);
    }

    public P2pLobbyPanel panel() {
        return panel;
    }

    public LobbySnapshot snapshot() {
        return current;
    }

    // ── 화면 동작 ─────────────────────────────

    @Override
    public void host(String portText) {
        if (closed || current.state() != State.INITIAL) return;
        Integer port = parsePort(portText);
        if (port == null) return;
        message = "";
        track(() -> lobby.host(port), "호스트 대기");
    }

    @Override
    public void connect(String address, String portText) {
        if (closed || current.state() != State.INITIAL) return;
        Integer port = parsePort(portText);
        if (port == null) return;
        String host = address == null ? "" : address.trim();
        if (host.isEmpty()) {
            showMessage("상대 IP를 입력하세요. 예: 192.168.0.10");
            return;
        }
        message = "";
        track(() -> lobby.connect(host, port), "접속");
    }

    @Override
    public void selectMode(BattleMode mode) {
        selectedMode = Objects.requireNonNull(mode);
        if (closed || current.role() != Role.HOST || current.state() != State.LOBBY) return;
        if (current.config() != null && current.config().mode() == mode) return;
        lobby.selectConfig(MatchConfig.of(mode));
    }

    @Override
    public void toggleReady() {
        if (closed || current.state() != State.LOBBY || current.config() == null) return;
        try {
            lobby.ready(current.configRevision(), !current.localReady());
        } catch (IllegalStateException | IllegalArgumentException stale) {
            // 설정이 막 바뀌었다. 새 스냅샷의 revision으로 다시 누르면 된다
        }
    }

    @Override
    public void cancelOrBack() {
        if (closed) return;
        switch (current.state()) {
            case INITIAL -> navigation.back();
            case HOSTING, CONNECTING -> {
                attempt++;
                message = "취소했습니다.";
                lobby.cancel();
                render();
            }
            default -> {
                attempt++;
                message = "연결을 끊었습니다.";
                lobby.leave();
                render();
            }
        }
    }

    /** 결과 화면의 "로비로": 같은 연결의 로비로 돌아간다 (F-07) */
    public void returnToLobby() {
        if (closed) return;
        if (current.state() == State.RESULT) lobby.returnToLobby();
        inMatch = false;
        render();
    }

    // ── 로비 상태 ─────────────────────────────

    private void onSnapshot(LobbySnapshot snapshot) {
        if (closed) return;
        LobbySnapshot previous = current;
        current = snapshot;
        State state = snapshot.state();
        if (state == State.LOBBY && previous != null && previous.state() != State.LOBBY
                && previous.state() != State.RESULT) {
            message = "연결 성공!";
        }
        if (snapshot.role() == Role.HOST && state == State.LOBBY && snapshot.config() == null) {
            lobby.selectConfig(MatchConfig.of(selectedMode));
            return; // 설정이 들어간 스냅샷이 곧 다시 온다
        }
        if ((state == State.STARTING || state == State.PLAYING) && !inMatch) {
            var battle = lobby.battleClient();
            if (battle.isPresent() && !inMatch) {
                inMatch = true;
                message = "";
                navigation.startMatch(battle.get(), snapshot.config(), snapshot.role());
            }
        }
        if (inMatch && state == State.INITIAL) {
            inMatch = false;
            message = "상대와 연결이 끊어졌습니다.";
            render();
            navigation.connectionLost();
            return;
        }
        if (state == State.INITIAL && previous != null && previous.state() == State.LOBBY && message.isEmpty()) {
            message = "상대가 나갔습니다.";
        }
        render();
    }

    private void track(Supplier<CompletionStage<Void>> start, String what) {
        long mine = ++attempt;
        CompletionStage<Void> stage;
        try {
            stage = start.get();
        } catch (RuntimeException e) {
            showMessage(what + " 실패: " + e.getMessage());
            return;
        }
        render();
        stage.whenComplete((ignored, error) -> {
            if (closed || mine != attempt || error == null) return;
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (cause instanceof CancellationException) return;
            showMessage(failureText(cause));
        });
    }

    static String failureText(Throwable cause) {
        if (cause instanceof EstablishException e) {
            return switch (e.reason()) {
                case INVALID_ADDRESS -> "IP 주소 형식이 올바르지 않습니다. 예: 192.168.0.10";
                case PORT_IN_USE -> "이 포트는 이미 사용 중입니다. 다른 포트를 입력하세요.";
                case REFUSED -> "상대가 대기 중이 아닙니다. 호스트가 먼저 대기해야 합니다.";
                case TIMEOUT -> "응답이 없습니다. 같은 LAN인지, 방화벽 허용을 확인하세요.";
                case PROTOCOL_ERROR -> "상대 프로그램 버전이 다르거나 응답이 올바르지 않습니다.";
                case IO_ERROR -> "연결 중 오류가 났습니다. 다시 시도하세요.";
            };
        }
        return "연결 실패: " + cause.getMessage();
    }

    private Integer parsePort(String text) {
        try {
            int port = Integer.parseInt(text == null ? "" : text.trim());
            if (port >= 0 && port <= 65535) return port;
        } catch (NumberFormatException ignored) {
            // 아래 안내
        }
        showMessage("포트는 0~65535 숫자여야 합니다.");
        return null;
    }

    private void showMessage(String text) {
        message = text;
        render();
    }

    private void render() {
        if (current == null) return;
        panel.render(current, status());
    }

    private String status() {
        return switch (current.state()) {
            case INITIAL -> message.isEmpty() ? "호스트로 대기하거나 상대 IP로 접속하세요." : message;
            case HOSTING -> "상대 접속 대기 중 · 내 IP " + String.join(" / ", lanAddresses.get())
                    + " · 포트 " + (localPort.getAsInt() > 0 ? localPort.getAsInt() : panel.portField().getText().trim());
            case CONNECTING -> "접속 중…";
            case LOBBY -> (message.isEmpty() ? "" : message + " ")
                    + (current.role() == Role.HOST ? "모드를 고르고 게임 시작을 누르세요." : "호스트가 모드를 고릅니다. 게임 시작을 누르세요.");
            case STARTING, PLAYING -> "경기 진행 중";
            case RESULT -> "경기 종료";
        };
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        attempt++;
        subscription.close();
        lobby.close();
    }
}
