package tetris.feature.rule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import tetris.component.block.Block;
import tetris.feature.item.ItemType;

/**
 * 아이템 모드용 블록 생성기. 평소에는 감싼 생성기(base)의 블록을 그대로 내주고,
 * requestItem()이 불린 뒤 첫 next() 한 번만 아이템 블록을 내준다.
 *
 * 아이템 블록은 다섯 가지(ItemType 4종 + 무게추)를 같은 확률(각 20%)로 고른다.
 * ItemType이면 base의 일반 블록 하나를 만들어 채워진 칸 중 무작위 한 칸에 그 아이템을 붙이고,
 * 무게추면 weightBlockFactory가 만든 블록을 그대로 쓴다.
 * 언제 requestItem()을 부를지(누적 10줄 판정)는 이 클래스가 아니라 호출하는 쪽이 정한다.
 *
 * 가정(팀 합의 필요):
 * - ItemType에는 무게추가 없어서, "어떤 아이템 블록을 만들지"를 만드는 방법 5가지 중 하나를 고르는 식으로 구현했다.
 * - 무게추 블록은 WeightBlock을 직접 쓰지 않고 생성자로 받은 함수로 만든다. 만드는 쪽(MainFrame)이 WeightBlock::new를 넘긴다.
 * - base는 아이템 모드 규칙대로 NORMAL 가중치 생성기를 넘긴다고 가정한다. 이 클래스는 가중치를 따로 확인하지 않는다.
 * - GameSession은 이 클래스를 BlockGenerator가 아니라 ItemBlockGenerator 타입으로 들고 있어야 requestItem()을 부를 수 있다.
 */
public class ItemBlockGenerator implements BlockGenerator {
    private static final int EQUAL_WEIGHT = 1;

    private final BlockGenerator base;
    private final Random random;
    private final WeightedRandom<Supplier<Block>> itemBlockMaker;
    private boolean itemPending;

    /**
     * base: 일반 블록과 아이템을 붙일 블록을 만드는 생성기
     * weightBlockFactory: 무게추 블록을 새로 만드는 함수
     * random: 아이템 종류와 붙일 칸 선택에 쓴다. 하나라도 null이면 IllegalArgumentException
     */
    public ItemBlockGenerator(BlockGenerator base, Supplier<Block> weightBlockFactory, Random random) {
        if (base == null) throw new IllegalArgumentException("base cannot be null");
        if (weightBlockFactory == null) throw new IllegalArgumentException("weightBlockFactory cannot be null");
        if (random == null) throw new IllegalArgumentException("random cannot be null");
        this.base = base;
        this.random = random;

        Map<Supplier<Block>, Integer> makers = new LinkedHashMap<>(); // 순서를 고정해야 같은 시드에서 같은 결과가 나온다
        for (ItemType type : ItemType.values()) makers.put(() -> blockWithItem(type), EQUAL_WEIGHT);
        makers.put(weightBlockFactory, EQUAL_WEIGHT);
        itemBlockMaker = new WeightedRandom<>(makers, random);
    }

    /**
     * 다음 next() 한 번을 아이템 블록으로 만든다.
     * 가정(팀 합의 필요): next() 전에 여러 번 불러도 아이템 블록은 하나만 나온다(요청이 쌓이지 않는다).
     */
    public void requestItem() {
        itemPending = true;
    }

    @Override
    public Block next() {
        if (!itemPending) return base.next();

        // 아이템 블록을 다 만든 뒤에 요청을 지운다. 만드는 도중 예외가 나면 요청이 남아 다음 next()에서 다시 시도한다
        Block block = itemBlockMaker.next().get();
        if (block == null) throw new IllegalStateException("item block factory returned null");
        itemPending = false;
        return block;
    }

    private Block blockWithItem(ItemType type) {
        Block block = base.next();
        block.attachItemToRandomCell(type, random);
        return block;
    }
}
