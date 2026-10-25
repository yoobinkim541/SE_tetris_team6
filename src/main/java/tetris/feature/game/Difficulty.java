package tetris.feature.game;

import java.util.EnumMap;
import java.util.Map;
import tetris.component.block.BlockType;

/**
 * 난이도 (DIF-1~3, DIF-7). 난이도별 값은 이 enum 한 곳에서만 계산한다.
 * I 블록 가중치는 EASY +20%, HARD -20%이고 나머지 6종은 항상 10이다.
 * 레벨당 낙하 간격 감소폭은 EASY -20%, HARD +20%다.
 */
public enum Difficulty {
    EASY(12, 160),
    NORMAL(10, 200),
    HARD(8, 240);

    private static final int OTHER_WEIGHT = 10;

    private final int iWeight;
    private final int levelStepMillis;

    Difficulty(int iWeight, int levelStepMillis) {
        this.iWeight = iWeight;
        this.levelStepMillis = levelStepMillis;
    }

    public int iWeight() {
        return iWeight;
    }

    /** Level이 하나 오를 때 낙하 간격이 줄어드는 폭 (ms) */
    public int levelStepMillis() {
        return levelStepMillis;
    }

    /** 블록 종류별 가중치. WeightedBlockGenerator의 입력 (GEN-4) */
    public Map<BlockType, Integer> weights() {
        Map<BlockType, Integer> weights = new EnumMap<>(BlockType.class);
        for (BlockType type : BlockType.values()) weights.put(type, OTHER_WEIGHT);
        weights.put(BlockType.I, iWeight);
        return weights;
    }
}
