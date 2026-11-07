package tetris.feature.network.lobby;

import java.util.List;
import java.util.Objects;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.versus.api.MatchConfig;

/**
 * 로비 화면이 그리는 상태 사본. 생성 시 아래 규칙을 검사하므로 화면은 조합을 다시 확인하지 않아도 된다.
 *
 * <ul>
 *   <li>역할: INITIAL은 없음, HOSTING은 HOST, CONNECTING은 CLIENT, LOBBY·IN_MATCH는 반드시 있음, FAILED는 상관없음</li>
 *   <li>설정: configRevision이 1 이상일 때만 있다. 0은 설정 전. 설정이 바뀌면 revision이 오르고 준비가 풀린다</li>
 *   <li>준비: LOBBY에서 설정이 있을 때만 true일 수 있다</li>
 * </ul>
 *
 * @param localAddresses HOSTING일 때 상대에게 알려 줄 내 LAN 주소 후보 (R3-HOST). 그 외 빈 목록
 * @param peerAddress    CONNECTING이면 접속 대상, LOBBY·IN_MATCH면 상대 주소. 그 외 null
 * @param recentHosts    최근 접속에 성공한 주소, 최신순 (R3-RECENT, S10-C2)
 * @param message        진행·오류 안내 문구. 없으면 빈 문자열
 */
public record LobbySnapshot(LobbyPhase phase, PeerRole role, List<String> localAddresses, String peerAddress,
                            MatchConfig config, int configRevision, boolean selfReady, boolean peerReady,
                            List<String> recentHosts, String message) {
    public LobbySnapshot {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(message, "message");
        localAddresses = List.copyOf(localAddresses);
        recentHosts = List.copyOf(recentHosts);
        if (phase != LobbyPhase.HOSTING && !localAddresses.isEmpty()) {
            throw new IllegalArgumentException("local addresses only in HOSTING");
        }
        if (localAddresses.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("blank local address");
        }
        boolean needsPeer = phase == LobbyPhase.CONNECTING || phase == LobbyPhase.LOBBY
                || phase == LobbyPhase.IN_MATCH;
        if (needsPeer ? peerAddress == null || peerAddress.isBlank() : peerAddress != null) {
            throw new IllegalArgumentException("peer address inconsistent with phase");
        }
        switch (phase) {
            case INITIAL -> requireRole(role == null, phase, role);
            case HOSTING -> requireRole(role == PeerRole.HOST, phase, role);
            case CONNECTING -> requireRole(role == PeerRole.CLIENT, phase, role);
            case LOBBY, IN_MATCH -> requireRole(role != null, phase, role);
            case FAILED -> {
            }
        }
        if (configRevision < 0) throw new IllegalArgumentException("negative configRevision");
        if ((config == null) != (configRevision == 0)) {
            throw new IllegalArgumentException("config must be present exactly when configRevision > 0");
        }
        if ((selfReady || peerReady) && (phase != LobbyPhase.LOBBY || config == null)) {
            throw new IllegalArgumentException("ready only in LOBBY after a config is chosen");
        }
    }

    /** P2P 첫 화면 */
    public static LobbySnapshot initial(List<String> recentHosts) {
        return new LobbySnapshot(LobbyPhase.INITIAL, null, List.of(), null, null, 0, false, false, recentHosts, "");
    }

    /** Host selection always increments the wire revision and clears both READY votes. */
    public LobbySnapshot configured(MatchConfig next) {
        if (phase != LobbyPhase.LOBBY || role != PeerRole.HOST) {
            throw new IllegalStateException("only the host in LOBBY configures settings");
        }
        return new LobbySnapshot(phase, role, localAddresses, peerAddress,
                Objects.requireNonNull(next, "config"), Math.incrementExact(configRevision),
                false, false, recentHosts, message);
    }

    /** Local vote against the settings the consumer displayed; peer votes come from transport. */
    public LobbySnapshot readyAtRevision(int expectedConfigRevision, boolean ready) {
        if (phase != LobbyPhase.LOBBY || config == null) {
            throw new IllegalStateException("READY requires a configured LOBBY");
        }
        if (expectedConfigRevision != configRevision) {
            throw new IllegalArgumentException("stale config revision");
        }
        return new LobbySnapshot(phase, role, localAddresses, peerAddress, config, configRevision,
                ready, peerReady, recentHosts, message);
    }

    private static void requireRole(boolean valid, LobbyPhase phase, PeerRole role) {
        if (!valid) throw new IllegalArgumentException("role " + role + " is not allowed in " + phase);
    }
}
