package tetris.ui.versus;

import java.util.Objects;
import tetris.feature.setting.GameAction;

/** Provisional input DTO. Shared PAUSE/QUIT use ONE and are sent exactly once. */
public record PlayerCommand(PlayerId playerId, GameAction action, boolean pressed, long sequence) {
    public enum PlayerId { ONE, TWO }
    public PlayerCommand {
        Objects.requireNonNull(playerId); Objects.requireNonNull(action);
        if (sequence < 0) throw new IllegalArgumentException("negative sequence");
    }
}
