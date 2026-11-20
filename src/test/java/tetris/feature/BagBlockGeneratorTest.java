package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tetris.component.block.BlockType;
import tetris.feature.game.Difficulty;
import tetris.feature.rule.BagBlockGenerator;

/** 가중치 7-bag: NORMAL은 7개마다 7종 1번씩, EASY/HARD는 210개 주머니마다 I ±20%를 정확히 지키고 고르게 나온다. */
public class BagBlockGeneratorTest {
    private static final int BIG_BAG = 210; // EASY/HARD 가중치 합(최대공약수 1)

    private static List<BlockType> draw(BagBlockGenerator generator, int draws) {
        List<BlockType> types = new ArrayList<>();
        for (int i = 0; i < draws; i++) types.add(generator.next().getType());
        return types;
    }

    private static Map<BlockType, Integer> count(List<BlockType> types) {
        Map<BlockType, Integer> counts = new EnumMap<>(BlockType.class);
        for (BlockType type : types) counts.merge(type, 1, Integer::sum);
        return counts;
    }

    @Test
    @DisplayName("NORMAL은 표준 7-bag: 7개 묶음마다 7종이 정확히 한 번씩")
    void NORMAL은_7개마다_7종이_한번씩_나온다() {
        for (long seed = 0; seed < 20; seed++) {
            List<BlockType> types = draw(new BagBlockGenerator(Difficulty.NORMAL.weights(), new Random(seed)), 700);
            for (int start = 0; start < types.size(); start += 7)
                assertEquals(EnumSet.copyOf(List.of(BlockType.ordinary())), EnumSet.copyOf(types.subList(start, start + 7)),
                        "seed " + seed + " bag at " + start);
        }
    }

    @Test
    @DisplayName("NORMAL: 같은 블록은 최대 2번 연속, 같은 종류 사이 간격은 최대 12")
    void NORMAL은_연속과_가뭄이_제한된다() {
        List<BlockType> types = draw(new BagBlockGenerator(Difficulty.NORMAL.weights(), new Random(7)), 7000);
        Map<BlockType, Integer> last = new EnumMap<>(BlockType.class);
        for (int i = 0; i < types.size(); i++) {
            if (i >= 2) assertTrue(!(types.get(i) == types.get(i - 1) && types.get(i) == types.get(i - 2)), "triple at " + i);
            Integer previous = last.put(types.get(i), i);
            if (previous != null) assertTrue(i - previous <= 13, "gap " + (i - previous) + " at " + i);
        }
    }

    @Test
    @DisplayName("2-19 / 2-20: EASY·HARD는 210개 주머니마다 I 36·24개, 나머지 29·31개로 정확하다")
    void EASY_HARD는_주머니마다_가중치를_정확히_지킨다() {
        for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.HARD)) {
            Map<BlockType, Integer> weights = difficulty.weights();
            for (long seed = 0; seed < 10; seed++) {
                List<BlockType> types = draw(new BagBlockGenerator(weights, new Random(seed)), BIG_BAG * 5);
                for (int start = 0; start < types.size(); start += BIG_BAG)
                    assertEquals(weights, count(types.subList(start, start + BIG_BAG)), difficulty + " seed " + seed);
            }
        }
    }

    @Test
    // 묶음은 6~8개이고 한 종류가 빠지거나 2개 들어가는 묶음은 연달아 오지 않는다.
    // 그래서 같은 종류 사이 간격은 묶음 3개(24) 이하, 연속은 묶음 경계를 넘어도 3개 이하다
    @DisplayName("EASY·HARD도 고르게: 같은 블록 3연속 이하, 같은 종류 사이 간격 24 이하")
    void EASY_HARD도_고르게_나온다() {
        for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.HARD)) for (long seed = 0; seed < 20; seed++) {
            List<BlockType> types = draw(new BagBlockGenerator(difficulty.weights(), new Random(seed)), BIG_BAG * 20);
            Map<BlockType, Integer> last = new EnumMap<>(BlockType.class);
            for (int i = 0; i < types.size(); i++) {
                if (i >= 3) assertTrue(types.subList(i - 3, i + 1).stream().distinct().count() > 1, difficulty + " run of 4 at " + i);
                Integer previous = last.put(types.get(i), i);
                if (previous != null) assertTrue(i - previous <= 24, difficulty + " gap " + (i - previous) + " at " + i);
            }
        }
    }

    @Test
    void 같은_시드는_같은_순서_다른_시드는_다른_순서() {
        Map<BlockType, Integer> weights = Difficulty.EASY.weights();
        assertEquals(draw(new BagBlockGenerator(weights, new Random(11)), 300),
                draw(new BagBlockGenerator(weights, new Random(11)), 300));
        assertNotEquals(draw(new BagBlockGenerator(weights, new Random(11)), 300),
                draw(new BagBlockGenerator(weights, new Random(12)), 300));
    }

    @Test
    void 가중치_0인_종류는_나오지_않는다() {
        Map<BlockType, Integer> weights = new EnumMap<>(Difficulty.NORMAL.weights());
        weights.put(BlockType.S, 0);
        weights.put(BlockType.Z, 0);
        List<BlockType> types = draw(new BagBlockGenerator(weights, new Random(1)), 500);
        assertTrue(!types.contains(BlockType.S) && !types.contains(BlockType.Z));
        for (int start = 0; start < 500 - 5; start += 5) assertEquals(5, EnumSet.copyOf(types.subList(start, start + 5)).size());
    }

    @Test
    void 잘못된_입력은_거부한다() {
        Map<BlockType, Integer> zero = new EnumMap<>(BlockType.class);
        for (BlockType type : BlockType.ordinary()) zero.put(type, 0);
        Map<BlockType, Integer> negative = new EnumMap<>(Difficulty.NORMAL.weights());
        negative.put(BlockType.I, -1);
        Map<BlockType, Integer> weight = new EnumMap<>(Difficulty.NORMAL.weights());
        weight.put(BlockType.W, 1);
        assertThrows(IllegalArgumentException.class, () -> new BagBlockGenerator(null, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new BagBlockGenerator(Difficulty.NORMAL.weights(), null));
        assertThrows(IllegalArgumentException.class, () -> new BagBlockGenerator(zero, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new BagBlockGenerator(negative, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new BagBlockGenerator(weight, new Random()));
    }
}
