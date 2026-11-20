package tetris.feature.versus.api;

import java.util.Objects;
import tetris.feature.game.Difficulty;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.setting.ControlStyle;

/**
 * 경기 시작 전에 정하는 설정 (R3-MODES, R3-TIME, CTL-1). 로컬은 시작 메뉴, P2P는 호스트만 고른다 (R3-READY).
 * 조작 방식도 호스트 설정이다. 클라이언트는 STATE의 홀드·고스트·넥스트만 그린다.
 *
 * @param timeLimitMillis TIMED면 1 이상, 다른 모드는 0
 * @param controlStyle    호스트가 고른 조작 방식. 없으면 모던
 * @param raidMode        레이드 로비면 협동 또는 레이스. 대전이면 null
 */
public record MatchConfig(BattleMode mode, Difficulty difficulty, long timeLimitMillis, ControlStyle controlStyle,
                          RaidMode raidMode) {
    public MatchConfig {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(difficulty, "difficulty");
        if (raidMode == null && mode.timed() && timeLimitMillis <= 0) {
            throw new IllegalArgumentException("TIMED requires a positive time limit");
        }
        if (raidMode == null && !mode.timed() && timeLimitMillis != 0) {
            throw new IllegalArgumentException(mode + " must not have a time limit");
        }
        if (raidMode != null && timeLimitMillis != 0) {
            throw new IllegalArgumentException("raid must not have a battle time limit");
        }
        if (controlStyle == null) controlStyle = ControlStyle.MODERN;
    }

    /** 레이드가 아닌 대전. 조작 방식을 지정하지 않으면 모던 */
    public MatchConfig(BattleMode mode, Difficulty difficulty, long timeLimitMillis, ControlStyle controlStyle) {
        this(mode, difficulty, timeLimitMillis, controlStyle, null);
    }

    /** 조작 방식을 지정하지 않으면 모던 */
    public MatchConfig(BattleMode mode, Difficulty difficulty, long timeLimitMillis) {
        this(mode, difficulty, timeLimitMillis, ControlStyle.MODERN, null);
    }

    /** NORMAL 난이도와 기본 제한 시간(180초, D-05)을 쓰는 대전 설정 */
    public static MatchConfig of(BattleMode mode) {
        return new MatchConfig(mode, Difficulty.NORMAL,
                mode.timed() ? VersusRules.DEFAULT_TIME_LIMIT_MILLIS : 0);
    }

    /** 호스트 로비의 레이드. 보스는 항상 NORMAL, 제한 시간은 레이드 규칙이 가진다 */
    public static MatchConfig raid(RaidMode raidMode, ControlStyle style) {
        Objects.requireNonNull(raidMode, "raidMode");
        return new MatchConfig(BattleMode.CLASSIC, Difficulty.NORMAL, 0, style, raidMode);
    }

    public boolean raid() {
        return raidMode != null;
    }

    public MatchConfig withControlStyle(ControlStyle style) {
        return new MatchConfig(mode, difficulty, timeLimitMillis, style, raidMode);
    }
}
