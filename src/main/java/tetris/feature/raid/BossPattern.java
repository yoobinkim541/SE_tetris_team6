package tetris.feature.raid;

import tetris.feature.raid.api.PatternId;

/** 보스 패턴 하나. 아이템 효과처럼 상태를 갖지 않고, 지속은 엔진이 센다 */
public interface BossPattern {
    PatternId id();

    void apply(PatternContext context);
}
