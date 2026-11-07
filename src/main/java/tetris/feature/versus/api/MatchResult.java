package tetris.feature.versus.api;

import java.util.Objects;

/**
 * 경기 결과. 대전 결과는 스코어보드에 기록하지 않는다 (R3-RESULT).
 *
 * @param winner 무승부면 null (TIME_UP 동점만 해당, D-06)
 */
public record MatchResult(PlayerId winner, EndReason reason) {
    public enum EndReason {
        /** 상대가 더 이상 블록을 쌓을 수 없게 됨 */
        TOPPED_OUT,
        /** 제한 시간 종료, 점수 비교 (R3-TIME) */
        TIME_UP,
        /** 상대가 나가거나 연결이 끊김 (P2P) */
        OPPONENT_LEFT
    }

    public MatchResult {
        Objects.requireNonNull(reason, "reason");
        if (winner == null && reason != EndReason.TIME_UP) {
            throw new IllegalArgumentException("only TIME_UP can end in a draw");
        }
    }

    public static MatchResult win(PlayerId winner, EndReason reason) {
        return new MatchResult(Objects.requireNonNull(winner, "winner"), reason);
    }

    public static MatchResult timeUpDraw() {
        return new MatchResult(null, EndReason.TIME_UP);
    }

    public boolean isDraw() {
        return winner == null;
    }
}
