package tetris.ui.versus;

import java.util.Objects;
import tetris.feature.game.Difficulty;
import tetris.feature.setting.ControlStyle;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;

/** 대전 모드별 화면 이름과 로컬 대전 설정 (R3-MODES, D-05, Q-03) */
public final class BattleModes {
    private BattleModes() {
    }

    public static String label(BattleMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case CLASSIC -> "일반 대전";
            case ITEM -> "아이템 대전";
            case TIMED -> "시간제한 대전";
        };
    }

    /** 일반 대전은 설정 난이도, 아이템은 아이템 규칙(NORMAL), 시간제한은 180초·일반 블록·NORMAL */
    public static MatchConfig localConfig(BattleMode mode, Difficulty difficulty) {
        return localConfig(mode, difficulty, ControlStyle.MODERN);
    }

    /** 조작 방식은 이 PC 설정. P2P에서는 호스트가 같은 값을 CONFIG에 싣는다 */
    public static MatchConfig localConfig(BattleMode mode, Difficulty difficulty, ControlStyle style) {
        Objects.requireNonNull(difficulty, "difficulty");
        MatchConfig config = mode == BattleMode.CLASSIC ? new MatchConfig(mode, difficulty, 0) : MatchConfig.of(mode);
        return config.withControlStyle(style == null ? ControlStyle.MODERN : style);
    }
}
