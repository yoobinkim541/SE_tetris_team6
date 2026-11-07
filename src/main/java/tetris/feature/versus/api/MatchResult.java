package tetris.feature.versus.api;

import java.util.Objects;

/**
 * 경기 결과 (R3-LOSS, R3-TIME). 대전 결과는 스코어보드에 기록하지 않는다 (R3-NOSCORE).
 *
 * @param winner 무승부면 null. 같은 판정 프레임의 양측 패배(D-02) 또는 마감 동점(D-05)만 해당
 */
public record MatchResult(PlayerId winner, EndReason reason) {
    public enum EndReason {
        /** 블록을 더 쌓을 수 없게 됨. 양쪽이 같은 프레임에 그러면 무승부 */
        TOPPED_OUT,
        /** 제한 시간 종료, 마감 전에 확정된 점수 비교 */
        TIME_UP,
        /** 경기 중 Quit 확정 = 기권. 상대 승리 후 로비로 (D-03) */
        FORFEIT
    }

    public MatchResult {
        Objects.requireNonNull(reason, "reason");
        if (winner == null && reason == EndReason.FORFEIT) {
            throw new IllegalArgumentException("a forfeit always has a winner");
        }
    }

    public static MatchResult win(PlayerId winner, EndReason reason) {
        return new MatchResult(Objects.requireNonNull(winner, "winner"), reason);
    }

    public static MatchResult draw(EndReason reason) {
        return new MatchResult(null, reason);
    }

    public boolean isDraw() {
        return winner == null;
    }
}
