package tetris.feature.versus.api;

import java.util.Objects;
import tetris.feature.game.Difficulty;

/**
 * 경기 시작 전에 정하는 설정 (R3-MODES, R3-TIME). 로컬은 시작 메뉴, P2P는 호스트만 고른다 (R3-READY).
 *
 * @param timeLimitMillis TIMED면 1 이상, 다른 모드는 0
 */
public record MatchConfig(BattleMode mode, Difficulty difficulty, long timeLimitMillis) {
    public MatchConfig {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(difficulty, "difficulty");
        if (mode.timed() && timeLimitMillis <= 0) {
            throw new IllegalArgumentException("TIMED requires a positive time limit");
        }
        if (!mode.timed() && timeLimitMillis != 0) {
            throw new IllegalArgumentException(mode + " must not have a time limit");
        }
    }

    /** NORMAL 난이도와 기본 제한 시간(180초, D-05)을 쓰는 설정 */
    public static MatchConfig of(BattleMode mode) {
        return new MatchConfig(mode, Difficulty.NORMAL,
                mode.timed() ? VersusRules.DEFAULT_TIME_LIMIT_MILLIS : 0);
    }
}
