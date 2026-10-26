package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.feature.game.Difficulty;
import tetris.feature.item.ItemType;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.rule.ItemBlockGenerator;
import tetris.feature.rule.WeightedBlockGenerator;

/**
 * 아이템 블록 생성기가 요청 뒤 첫 블록 한 번만 아이템 블록을 내주는지,
 * 아이템 5종(L·B·D·S·무게추)을 같은 확률로 고르는지, 아이템을 채워진 칸에 하나만 붙이는지 검증한다.
 */
public class ItemBlockGeneratorTest {
    private static final int DRAWS = 10_000;
    // 요구사항 허용 오차는 5%p지만, 10,000회에서 표준편차가 약 0.4%p라 2%p로 좁혀야 가중치 실수(예: 한 종류만 2배)를 잡는다
    private static final double TOLERANCE = 0.02;

    // 무게추 대신 쓰는 표시용 블록. 실제 WeightBlock은 A 트랙에서 만든다
    private static final class FakeWeight extends Block {
        FakeWeight() {
            super(new int[][] {{0, 1, 1, 0}, {1, 1, 1, 1}});
        }
    }

    private static final Supplier<Block> FAKE_WEIGHT = FakeWeight::new;

    private static ItemBlockGenerator generator(BlockGenerator base, long seed) {
        return new ItemBlockGenerator(base, FAKE_WEIGHT, new Random(seed));
    }

    private static ItemBlockGenerator normalGenerator(long seed) {
        Random random = new Random(seed);
        return new ItemBlockGenerator(new WeightedBlockGenerator(Difficulty.NORMAL.weights(), random), FAKE_WEIGHT, random);
    }

    // 블록에 붙은 아이템(없으면 empty). 두 개 이상 붙었거나 빈 칸에 붙었으면 실패시킨다
    private static Optional<ItemType> itemOf(Block block) {
        Optional<ItemType> found = Optional.empty();
        int[][] shape = block.getShape();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                Optional<ItemType> item = block.itemCellAt(r, c);
                if (item.isEmpty()) continue;
                assertTrue(found.isEmpty(), "아이템 칸이 두 개 이상");
                assertTrue(shape[r][c] != 0, "빈 칸에 아이템이 붙음");
                found = item;
            }
        }
        return found;
    }

    //#region 요청 흐름
    @Test
    void 요청하지_않으면_base_블록을_그대로_내준다() {
        Block first = new IBlock();
        Block second = new TBlock();
        ItemBlockGenerator generator = generator(GameTestSupport.sequence(first, second), 1);

        assertSame(first, generator.next());
        assertSame(second, generator.next());
        assertTrue(itemOf(first).isEmpty());
        assertTrue(itemOf(second).isEmpty());
    }

    @Test
    void 요청_뒤_첫_블록만_아이템_블록이고_그다음은_일반_블록() {
        ItemBlockGenerator generator = normalGenerator(5);
        generator.requestItem();

        Block item = generator.next();
        assertTrue(item instanceof FakeWeight || itemOf(item).isPresent());

        for (int i = 0; i < 50; i++) {
            Block block = generator.next();
            assertTrue(!(block instanceof FakeWeight) && itemOf(block).isEmpty(), "요청 없이 아이템 블록이 나옴");
        }
    }

    @Test
    void next_전에_여러_번_요청해도_아이템_블록은_하나() {
        ItemBlockGenerator generator = normalGenerator(9);
        generator.requestItem();
        generator.requestItem();
        generator.requestItem();

        Block item = generator.next();
        Block after = generator.next();

        assertTrue(item instanceof FakeWeight || itemOf(item).isPresent());
        assertTrue(!(after instanceof FakeWeight) && itemOf(after).isEmpty());
    }

    @Test
    void 일반_아이템은_base가_만든_블록에_붙는다() {
        // base가 항상 O 블록만 내주면 무게추가 아닌 아이템 블록은 모두 O 블록이어야 한다
        for (long seed = 0; seed < 50; seed++) {
            ItemBlockGenerator generator = generator(() -> new OBlock(), seed);
            generator.requestItem();
            Block block = generator.next();
            if (block instanceof FakeWeight) continue;

            assertTrue(block instanceof OBlock);
            assertTrue(itemOf(block).isPresent());
        }
    }

    @Test
    void 무게추를_고르면_base_블록을_쓰지_않는다() {
        // base에서 블록을 하나 버리면 다음 일반 블록 순서가 바뀌므로, 무게추일 때는 base를 부르면 안 된다
        int checked = 0;
        for (long seed = 0; seed < 100; seed++) {
            int[] baseCalls = {0};
            ItemBlockGenerator generator = generator(() -> {
                baseCalls[0]++;
                return new OBlock();
            }, seed);
            generator.requestItem();
            Block block = generator.next();

            if (block instanceof FakeWeight) {
                assertEquals(0, baseCalls[0], "seed " + seed);
                checked++;
            } else {
                assertEquals(1, baseCalls[0], "seed " + seed);
            }
        }
        assertTrue(checked > 0, "무게추가 한 번도 뽑히지 않음");
    }

    @Test
    void 아이템_블록을_만들다_예외가_나면_요청이_남아_다음에_다시_시도한다() {
        // base가 처음 한 번 실패하는 경우. 무게추가 아닌 종류가 뽑히는 시드를 찾아 확인한다
        boolean checked = false;
        for (long seed = 0; seed < 100 && !checked; seed++) {
            boolean[] failOnce = {true};
            ItemBlockGenerator generator = generator(() -> {
                if (failOnce[0]) {
                    failOnce[0] = false;
                    throw new IllegalStateException("base failed");
                }
                return new OBlock();
            }, seed);
            generator.requestItem();

            Block first;
            try {
                first = generator.next();
            } catch (IllegalStateException e) {
                Block retried = generator.next();
                assertTrue(retried instanceof FakeWeight || itemOf(retried).isPresent(), "요청이 사라짐");
                checked = true;
                continue;
            }
            assertTrue(first instanceof FakeWeight); // 무게추가 뽑히면 base를 부르지 않아 실패하지 않는다
        }
        assertTrue(checked);
    }

    @Test
    void 무게추_블록에는_아이템을_붙이지_않는다() {
        for (long seed = 0; seed < 200; seed++) {
            ItemBlockGenerator generator = normalGenerator(seed);
            generator.requestItem();
            Block block = generator.next();
            if (block instanceof FakeWeight) assertTrue(itemOf(block).isEmpty());
        }
    }
    //#endregion

    //#region 분포
    @Test
    void 아이템_5종이_각각_20퍼센트로_나온다() {
        ItemBlockGenerator generator = normalGenerator(42);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < DRAWS; i++) {
            generator.requestItem();
            Block block = generator.next();
            String kind = block instanceof FakeWeight ? "W" : itemOf(block).orElseThrow().name();
            counts.merge(kind, 1, Integer::sum);
        }

        List<String> kinds = new ArrayList<>();
        for (ItemType type : ItemType.values()) kinds.add(type.name());
        kinds.add("W");
        assertEquals(kinds.size(), counts.size());
        for (String kind : kinds) assertEquals(0.2, counts.getOrDefault(kind, 0) / (double) DRAWS, TOLERANCE, kind);
    }

    @Test
    void 같은_시드면_같은_아이템_순서() {
        assertEquals(drawKinds(normalGenerator(11), 100), drawKinds(normalGenerator(11), 100));
    }

    private static List<String> drawKinds(ItemBlockGenerator generator, int count) {
        List<String> kinds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            generator.requestItem();
            Block block = generator.next();
            kinds.add(block instanceof FakeWeight ? "W" : block.getType() + ":" + itemOf(block).orElseThrow() + "@" + itemCell(block));
        }
        return kinds;
    }

    private static String itemCell(Block block) {
        int[][] shape = block.getShape();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (block.itemCellAt(r, c).isPresent()) return r + "," + c;
            }
        }
        return "-";
    }
    //#endregion

    //#region 잘못된 입력
    @Test
    void null_입력은_예외() {
        BlockGenerator base = () -> new OBlock();
        assertThrows(IllegalArgumentException.class, () -> new ItemBlockGenerator(null, FAKE_WEIGHT, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new ItemBlockGenerator(base, null, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new ItemBlockGenerator(base, FAKE_WEIGHT, null));
    }

    @Test
    void 무게추_함수가_null을_돌려주면_예외() {
        // 무게추가 뽑힐 때까지 시드를 바꿔 가며 확인한다
        boolean thrown = false;
        for (long seed = 0; seed < 100 && !thrown; seed++) {
            ItemBlockGenerator generator = new ItemBlockGenerator(() -> new OBlock(), () -> null, new Random(seed));
            generator.requestItem();
            try {
                generator.next();
            } catch (IllegalStateException e) {
                thrown = true;
            }
        }
        assertTrue(thrown);
    }
    //#endregion

    @Test
    void 요청이_없으면_아이템_종류를_고르지_않는다() {
        // 요청 없이 next()를 부르면 Random을 쓰지 않으므로, base가 같은 시드를 써도 일반 모드와 같은 순서가 나온다
        Random shared = new Random(3);
        ItemBlockGenerator generator = new ItemBlockGenerator(new WeightedBlockGenerator(Difficulty.NORMAL.weights(), shared), FAKE_WEIGHT, shared);
        WeightedBlockGenerator plain = new WeightedBlockGenerator(Difficulty.NORMAL.weights(), new Random(3));
        for (int i = 0; i < 100; i++) assertEquals(plain.next().getType(), generator.next().getType());
    }
}
