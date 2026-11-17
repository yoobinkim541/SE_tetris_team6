package tetris.ui.versus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tetris.feature.network.lobby.LinkHealth;
import tetris.feature.network.lobby.LinkStats;
import tetris.feature.network.lobby.LobbySnapshot.Role;

/**
 * 개발자 모드 연결 상태 표시 문구 (dev-net-overlay). 화면 없이 문자열만 만든다.
 *
 * <p>연결이 없으면 한 줄("[DEV] 연결 없음"), 있으면 역할·상대·상태 / RTT / 수신 간격·빈도 / PING·PONG 네 줄이다.
 * RTT 표본이 없으면 숫자 대신 "-"와 "(응답 없음)"을 쓴다. 화면 그리기는 이 문자열만 그린다.
 */
public final class NetDebugText {
    private static final String NONE = "[DEV] 연결 없음";

    private NetDebugText() {
    }

    /** 연결 통계를 화면에 그릴 줄 단위 문자열로 바꾼다 */
    public static List<String> lines(Optional<LinkStats> stats) {
        Objects.requireNonNull(stats, "stats");
        if (stats.isEmpty()) return List.of(NONE);
        LinkStats value = stats.get();
        return List.of(
                "[DEV] " + role(value.role()) + " · " + value.peerAddress() + " · " + status(value.status()),
                rtt(value),
                "마지막 수신 " + value.millisSinceLastFrame() + "ms 전 · 수신 " + value.framesLastSecond() + "/s"
                        + " · STATE " + value.statesLastSecond() + "/s",
                "PING " + value.pingsSent() + " / PONG " + value.pongsReceived());
    }

    private static String role(Role role) {
        return switch (role) {
            case HOST -> "호스트";
            case CLIENT -> "클라이언트";
            case NONE -> "연결";
        };
    }

    private static String status(LinkHealth.Status status) {
        return switch (status) {
            case NORMAL -> "정상";
            case LAGGING -> "지연";
            case LOST -> "끊김";
            case PEER_LEFT -> "상대 나감";
            case NONE -> "확인 중";
        };
    }

    /** 표본이 없으면 0이나 음수를 보여 주지 않고 "-"와 이유를 쓴다 */
    private static String rtt(LinkStats stats) {
        if (stats.rttSamples() == 0 || stats.lastRttMillis() < 0) return "RTT - (응답 없음)";
        return "RTT " + stats.lastRttMillis() + "ms (최소 " + stats.minRttMillis()
                + " / 평균 " + stats.avgRttMillis() + " / 최대 " + stats.maxRttMillis()
                + ", " + stats.rttSamples() + "회)";
    }
}
