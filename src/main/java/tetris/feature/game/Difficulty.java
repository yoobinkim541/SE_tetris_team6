package tetris.feature.game;

import java.util.EnumMap;
import java.util.Map;
import tetris.component.block.BlockType;

/**
 * 난이도 (DIF-1~3, DIF-7). 난이도별 값은 이 enum 한 곳에서만 계산한다.
 * I 블록 가중치는 EASY +20%, HARD -20%이고 나머지 6종은 항상 10이다.
 * 레벨당 낙하 간격 감소폭은 EASY -20%, HARD +20% 비율을 유지한다.
 */
public enum Difficulty {
    EASY(12, 80, 400),
    NORMAL(10, 100, 300),
    HARD(8, 120, 250);

    private static final int OTHER_WEIGHT = 10;

    private final int iWeight;
    private final int levelStepMillis;
    private final int minIntervalMillis;

    Difficulty(int iWeight, int levelStepMillis, int minIntervalMillis) {
        this.iWeight = iWeight;
        this.levelStepMillis = levelStepMillis;
        this.minIntervalMillis = minIntervalMillis;
    }

    public int iWeight() {
        return iWeight;
    }

    /** Level이 하나 오를 때 낙하 간격이 줄어드는 폭 (ms) */
    public int levelStepMillis() {
        return levelStepMillis;
    }

    /** 해당 난이도의 최소 낙하 간격 (최고 레벨 속도 하한선, ms) */
    public int minIntervalMillis() {
        return minIntervalMillis;
    }

    /** 블록 종류별 가중치. WeightedBlockGenerator의 입력 (GEN-4) */
    public Map<BlockType, Integer> weights() {
        Map<BlockType, Integer> weights = new EnumMap<>(BlockType.class);
        for (BlockType type : BlockType.ordinary()) weights.put(type, OTHER_WEIGHT);
        weights.put(BlockType.I, iWeight);
        return weights;
    }
}
