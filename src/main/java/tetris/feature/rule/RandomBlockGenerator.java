package tetris.feature.rule;

import java.util.Random;
import tetris.component.block.Block;

/** 7종을 각각 1/7 확률로 독립 선택 (기존 timer/RandomSelect 대체) */
public class RandomBlockGenerator implements BlockGenerator {
    public RandomBlockGenerator() {
        // 기본 Random 사용
    }

    public RandomBlockGenerator(Random random) {
        // 테스트용: 시드 고정 Random 주입
    }

    @Override
    public Block next() {
        throw new UnsupportedOperationException("TODO");
    }
}
