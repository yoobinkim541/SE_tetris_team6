package tetris.feature.network.protocol;

/**
 * 메시지 종류와 봉투 필드 규칙.
 *
 * <p>연결 제어(HELLO·PING·PONG·LEAVE·ERROR) payload는 {@link ControlPayloads}에 있다.
 * 로비·경기 메시지(CONFIG·READY·START·INPUT·STATE·RESULT)의 payload는 S7-A1 공통 계약
 * (MatchConfig·PlayerCommand·MatchSnapshot)이 확정되면 그 DTO로 채운다. 여기서는 봉투 규칙만 고정한다.
 */
public enum MessageType {
    HELLO(false, false),
    /** 호스트가 고른 모드 설정. 바뀔 때마다 configRevision이 올라간다 */
    CONFIG(false, true),
    /** 현재 configRevision에 대한 준비. 이전 revision의 READY는 무시한다 */
    READY(false, true),
    START(true, true),
    INPUT(true, false),
    /** 최신 값만 의미 있으므로 밀리면 합칠 수 있다. 나머지 종류는 유실 없이 순서대로 보낸다 */
    STATE(true, false),
    RESULT(true, false),
    PING(false, false),
    PONG(false, false),
    LEAVE(false, false),
    ERROR(false, false);

    private final boolean requiresMatchId;
    private final boolean requiresConfigRevision;

    MessageType(boolean requiresMatchId, boolean requiresConfigRevision) {
        this.requiresMatchId = requiresMatchId;
        this.requiresConfigRevision = requiresConfigRevision;
    }

    /** 특정 경기에 속한 메시지. 재대전 뒤 이전 matchId 메시지를 버리는 기준이 된다 */
    public boolean requiresMatchId() {
        return requiresMatchId;
    }

    /** 1 이상의 configRevision이 있어야 하는 메시지 */
    public boolean requiresConfigRevision() {
        return requiresConfigRevision;
    }
}
