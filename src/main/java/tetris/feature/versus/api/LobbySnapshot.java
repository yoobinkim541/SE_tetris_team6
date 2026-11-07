package tetris.feature.versus.api;

import java.util.List;
import java.util.Objects;

/**
 * 로비 화면이 읽는 상태 (R3-P2P-LOBBY). 계약 v1 초안 — S8-C2에서 C와 함께 확정한다.
 *
 * @param hostAddress    HOSTING이면 표시할 내 IP, CONNECTING 이후면 상대 주소. 그 외 null
 * @param config         호스트가 고른 설정. 아직 없으면 null
 * @param configRevision 설정이 바뀔 때마다 증가. 0은 설정 전. 이전 revision의 준비는 무효
 * @param recentHosts    최근 접속한 IP, 최신순 (R3-P2P-RECENT)
 * @param message        진행·오류 안내 문구. 없으면 빈 문자열
 */
public record LobbySnapshot(LobbyPhase phase, boolean host, String hostAddress, MatchConfig config,
                            int configRevision, boolean selfReady, boolean peerReady,
                            List<String> recentHosts, String message) {
    public LobbySnapshot {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(message, "message");
        recentHosts = List.copyOf(recentHosts);
        if (configRevision < 0) throw new IllegalArgumentException("negative configRevision");
        if ((config == null) != (configRevision == 0)) {
            throw new IllegalArgumentException("config must be present exactly when configRevision > 0");
        }
        if ((selfReady || peerReady) && config == null) {
            throw new IllegalArgumentException("cannot be ready before a config is chosen");
        }
    }

    public static LobbySnapshot idle(List<String> recentHosts) {
        return new LobbySnapshot(LobbyPhase.IDLE, false, null, null, 0, false, false, recentHosts, "");
    }
}
