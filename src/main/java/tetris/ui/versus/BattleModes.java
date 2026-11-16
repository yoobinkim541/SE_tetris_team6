package tetris.ui.versus;

import java.util.Objects;
import tetris.feature.game.Difficulty;
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
        Objects.requireNonNull(difficulty, "difficulty");
        return mode == BattleMode.CLASSIC ? new MatchConfig(mode, difficulty, 0) : MatchConfig.of(mode);
    }
}
