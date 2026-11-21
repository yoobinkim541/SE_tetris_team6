package tetris.feature.raid;

import java.util.Random;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;

/**
 * 다음 패턴을 고르는 자리. null을 돌려주면 가중 랜덤을 쓴다.
 * 테스트가 한 패턴을 고정할 때 넘긴다. 시드 난수는 건드리지 않아도 된다.
 */
@FunctionalInterface
public interface PatternSource {
    PatternId pick(BossId boss, int level, PatternId previous, PatternId beforePrevious, Random random);
}
