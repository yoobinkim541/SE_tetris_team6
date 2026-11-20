package tetris.feature.raid.api;

import java.util.Objects;
import tetris.feature.setting.ControlStyle;

/**
 * 레이드 시작 설정. 보스 순서·패턴·럭키 블록·보상 카드의 모든 난수는 seed 하나에서 뽑는다 (§5)
 */
public record RaidConfig(RaidMode mode, long seed, ControlStyle controlStyle) {
    public RaidConfig {
        Objects.requireNonNull(mode, "mode");
        if (controlStyle == null) controlStyle = ControlStyle.MODERN;
    }
}
