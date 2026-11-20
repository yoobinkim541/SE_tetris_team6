package tetris.feature.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;

/** 보스 패턴 가중 선택. 기본 40/40, 시그니처는 2레벨부터 20%. 같은 패턴 3연속은 제외한다 */
public final class PatternPicker {
    private PatternPicker() {
    }

    public static PatternId[] roster(BossId boss) {
        return switch (boss) {
            case JELLY_KING -> new PatternId[] {PatternId.CLOUD, PatternId.SUMMON_BLOCK, PatternId.SPLIT};
            case STONE_GOLEM -> new PatternId[] {PatternId.ROCK_THROW, PatternId.PILLAR, PatternId.EARTHQUAKE};
            case NIGHT_WITCH -> new PatternId[] {PatternId.CURSE_BLOCKS, PatternId.SEAL_PREVIEW, PatternId.REVERSE};
            case MECHA_DRAGON -> new PatternId[] {PatternId.OVERLOAD, PatternId.CHAIN, PatternId.FIRE_BREATH};
            case GOLDEN_TETRO -> new PatternId[0];
        };
    }

    public static PatternId pick(BossId boss, int level, boolean splitDone, PatternId previous, PatternId beforePrevious, Random random) {
        PatternId[] roster = roster(boss);
        if (roster.length == 0) return null;
        boolean banned = previous != null && previous == beforePrevious;
        List<PatternId> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < roster.length; i++) {
            if (i == 2 && level < 2) continue;
            if (roster[i] == PatternId.SPLIT && splitDone) continue;
            if (banned && roster[i] == previous) continue;
            int weight = i == 2 ? 20 : 40;
            options.add(roster[i]);
            weights.add(weight);
            total += weight;
        }
        if (options.isEmpty()) {
            options.add(previous == null ? roster[0] : previous);
            weights.add(40);
            total = 40;
        }
        int roll = random.nextInt(total);
        int cursor = 0;
        for (int i = 0; i < options.size(); i++) {
            cursor += weights.get(i);
            if (roll < cursor) return options.get(i);
        }
        return options.get(options.size() - 1);
    }
}
