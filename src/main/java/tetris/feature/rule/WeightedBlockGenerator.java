package tetris.feature.rule;

import java.util.EnumSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.IBlock;
import tetris.component.block.JBlock;
import tetris.component.block.LBlock;
import tetris.component.block.OBlock;
import tetris.component.block.SBlock;
import tetris.component.block.TBlock;
import tetris.component.block.ZBlock;

/**
 * 블록 종류별 가중치에 비례하는 확률로 다음 블록을 만든다. 난이도별 I 블록 빈도 차이는 넘겨받은 가중치로 정해진다.
 * 실제 게임과 분포 테스트가 같은 이 클래스를 써야 테스트 결과가 게임에도 그대로 성립한다.
 * 7종을 똑같은 확률로 뽑으려면 가중치를 모두 같게 주면 된다.
 */
public class WeightedBlockGenerator implements BlockGenerator {
    // 일반 블록 7종. 무게추처럼 이 생성기로 만들면 안 되는 종류가 BlockType에 추가돼도 여기서 막는다
    private static final Set<BlockType> SUPPORTED = EnumSet.of(
            BlockType.I, BlockType.J, BlockType.L, BlockType.O, BlockType.S, BlockType.T, BlockType.Z);

    private final WeightedRandom<BlockType> picker;

    /**
     * weights는 일반 블록 7종을 키로 하는 가중치다(빠진 종류는 나오지 않는다). 가중치 규칙은 WeightedRandom과 같다.
     * 다른 종류가 키에 있거나 규칙을 어기면 IllegalArgumentException.
     */
    public WeightedBlockGenerator(Map<BlockType, Integer> weights, Random random) {
        if (weights == null) throw new IllegalArgumentException("weights cannot be null");
        for (BlockType type : weights.keySet()) {
            if (!SUPPORTED.contains(type)) throw new IllegalArgumentException("unsupported block type : " + type);
        }
        picker = new WeightedRandom<>(weights, random);
    }

    /** 가중치에 따라 고른 종류의 새 블록을 0° 상태로 만든다. 호출할 때마다 새 인스턴스다 */
    @Override
    public Block next() {
        return create(picker.next());
    }

    private static Block create(BlockType type) {
        return switch (type) {
            case I -> new IBlock();
            case J -> new JBlock();
            case L -> new LBlock();
            case O -> new OBlock();
            case S -> new SBlock();
            case T -> new TBlock();
            case Z -> new ZBlock();
            default -> throw new IllegalStateException("unsupported block type : " + type); // 생성자에서 이미 걸러진다
        };
    }
}
