package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.IBlock;
import tetris.component.block.JBlock;
import tetris.component.block.LBlock;
import tetris.component.block.OBlock;
import tetris.component.block.SBlock;
import tetris.component.block.TBlock;
import tetris.component.block.ZBlock;
import tetris.feature.game.Difficulty;
import tetris.feature.rule.WeightedBlockGenerator;

/** 난이도별 가중치(I 12/10/8, 나머지 10)로 블록 7종이 기대 비율대로 나오는지 10,000회 ±5%p로 검증하고, 생성된 블록 상태와 잘못된 입력 처리를 확인한다 */
public class WeightedBlockGeneratorTest {
    private static final int DRAWS = 10_000;
    private static final double TOLERANCE = 0.05; // 기대 비율과의 차이를 비율의 절댓값 5%p까지 허용

    private static Map<BlockType, Integer> counts(WeightedBlockGenerator generator, int draws) {
        Map<BlockType, Integer> counts = new EnumMap<>(BlockType.class);
        for (int i = 0; i < draws; i++) counts.merge(generator.next().getType(), 1, Integer::sum);
        return counts;
    }

    private static List<BlockType> draw(WeightedBlockGenerator generator, int draws) {
        List<BlockType> types = new ArrayList<>();
        for (int i = 0; i < draws; i++) types.add(generator.next().getType());
        return types;
    }

    private static void assertDistribution(Map<BlockType, Integer> weights, long seed) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        Map<BlockType, Integer> counts = counts(new WeightedBlockGenerator(weights, new Random(seed)), DRAWS);
        for (Map.Entry<BlockType, Integer> entry : weights.entrySet()) {
            double expected = entry.getValue() / (double) total;
            double observed = counts.getOrDefault(entry.getKey(), 0) / (double) DRAWS;
            assertEquals(expected, observed, TOLERANCE, entry.getKey().name());
        }
    }

    //#region 분포 (실제 게임이 쓰는 Difficulty.weights() 그대로)
    @Test
    void EASY_I는_12_나머지는_10_비율로_나온다() {
        assertDistribution(Difficulty.EASY.weights(), 42);
    }

    @Test
    void NORMAL은_7종_모두_같은_비율() {
        assertDistribution(Difficulty.NORMAL.weights(), 42);
    }

    @Test
    void HARD_I는_8_나머지는_10_비율로_나온다() {
        assertDistribution(Difficulty.HARD.weights(), 42);
    }

    @Test
    void I_기대_비율은_EASY_16_67_NORMAL_14_29_HARD_11_76_퍼센트() { // 가중치 합 72 / 70 / 68 중 I의 몫
        assertEquals(0.1667, iRatio(Difficulty.EASY), 0.0001);
        assertEquals(0.1429, iRatio(Difficulty.NORMAL), 0.0001);
        assertEquals(0.1176, iRatio(Difficulty.HARD), 0.0001);
    }

    private static double iRatio(Difficulty difficulty) {
        Map<BlockType, Integer> weights = difficulty.weights();
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        return weights.get(BlockType.I) / (double) total;
    }

    @Test
    void 여러_시드에서도_난이도별_I_비율이_오차_범위_안() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (long seed = 0; seed < 10; seed++) {
                Map<BlockType, Integer> counts = counts(new WeightedBlockGenerator(difficulty.weights(), new Random(seed)), DRAWS);
                assertEquals(iRatio(difficulty), counts.get(BlockType.I) / (double) DRAWS, TOLERANCE, difficulty + " seed " + seed);
            }
        }
    }

    @Test
    void 가중치_0인_종류는_나오지_않는다() {
        Map<BlockType, Integer> weights = Difficulty.NORMAL.weights();
        weights.put(BlockType.I, 0);
        assertFalse(counts(new WeightedBlockGenerator(weights, new Random(3)), DRAWS).containsKey(BlockType.I));
    }
    //#endregion

    //#region 생성되는 블록
    @Test
    void 같은_시드면_같은_순서() {
        assertEquals(draw(new WeightedBlockGenerator(Difficulty.EASY.weights(), new Random(7)), 200),
                     draw(new WeightedBlockGenerator(Difficulty.EASY.weights(), new Random(7)), 200));
    }

    @Test
    void 생성된_블록은_종류에_맞는_0도_모양이고_매번_새_인스턴스() {
        WeightedBlockGenerator generator = new WeightedBlockGenerator(Difficulty.NORMAL.weights(), new Random(7));
        Map<BlockType, Block> reference = new EnumMap<>(BlockType.class);
        for (Block block : new Block[] {new IBlock(), new JBlock(), new LBlock(), new OBlock(), new SBlock(), new TBlock(), new ZBlock()})
            reference.put(block.getType(), block);

        for (int i = 0; i < 100; i++) {
            Block block = generator.next();
            assertTrue(Arrays.deepEquals(reference.get(block.getType()).getShape(), block.getShape()));
            assertEquals(0, block.getRotation());
            assertNotSame(block, generator.next());
        }
    }
    //#endregion

    //#region 잘못된 입력
    @Test
    void 잘못된_가중치는_예외() {
        Map<BlockType, Integer> negative = Difficulty.NORMAL.weights();
        negative.put(BlockType.T, -1);
        Map<BlockType, Integer> allZero = new EnumMap<>(BlockType.class);
        allZero.put(BlockType.O, 0);

        assertThrows(IllegalArgumentException.class, () -> new WeightedBlockGenerator(negative, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new WeightedBlockGenerator(allZero, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new WeightedBlockGenerator(null, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new WeightedBlockGenerator(Difficulty.NORMAL.weights(), null));
    }
    //#endregion
}
