package tetris.feature.rule;

import java.util.Random;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.IBlock;
import tetris.component.block.JBlock;
import tetris.component.block.LBlock;
import tetris.component.block.OBlock;
import tetris.component.block.SBlock;
import tetris.component.block.TBlock;
import tetris.component.block.ZBlock;

/** 7종을 각각 1/7 확률로 독립 선택 (기존 timer/RandomSelect 대체) */
public class RandomBlockGenerator implements BlockGenerator {
    private static final BlockType[] TYPES = BlockType.values();

    private final Random random;

    public RandomBlockGenerator() {
        this(new Random()); // 기본 Random 사용
    }

    public RandomBlockGenerator(Random random) {
        // 테스트용: 시드 고정 Random 주입
        if (random == null) throw new IllegalArgumentException("random cannot be null");
        this.random = random;
    }

    @Override
    public Block next() {
        return create(TYPES[random.nextInt(TYPES.length)]);
    }

    // 종류에 맞는 새 블록 (0° 모양, rotation 0). 호출마다 새 인스턴스
    private static Block create(BlockType type) {
        return switch (type) {
            case I -> new IBlock();
            case J -> new JBlock();
            case L -> new LBlock();
            case O -> new OBlock();
            case S -> new SBlock();
            case T -> new TBlock();
            case Z -> new ZBlock();
        };
    }
}
