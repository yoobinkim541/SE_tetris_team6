package tetris.feature.game;

import java.util.EnumMap;
import java.util.Map;
import tetris.component.block.BlockType;

/**
 * 난이도 (DIF-1~3, DIF-7). 난이도별 값은 이 enum 한 곳에서만 계산한다.
 * I 블록 실제 확률은 NORMAL의 EASY 1.2배, HARD 0.8배이다.
 * 합계 210의 정수 가중치로 정규화 오차 없이 표현하며 나머지 6종은 균등하다.
 * 레벨당 낙하 간격 감소폭은 EASY -20%, HARD +20% 비율을 유지한다.
 */
public enum Difficulty {
    EASY(36, 80, 400),
    NORMAL(30, 100, 300),
    HARD(24, 120, 250);

    private static final int TOTAL_WEIGHT = 210;

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
        int otherWeight = (TOTAL_WEIGHT - iWeight) / 6;
        for (BlockType type : BlockType.ordinary()) weights.put(type, otherWeight);
        weights.put(BlockType.I, iWeight);
        return weights;
    }
}
