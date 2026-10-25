package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tetris.feature.rule.WeightedRandom;

/** 가중치 비율대로 뽑히는지(요구사항 예시 4종 중 B만 2배 → 40/20/20/20을 10,000회, ±5%p), 재현성, 잘못된 가중치 거부를 검증한다 */
public class WeightedRandomTest {
    private static final int DRAWS = 10_000;
    private static final double TOLERANCE = 0.05; // 기대 비율과의 차이를 비율의 절댓값 5%p까지 허용

    private static Map<String, Integer> weights(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], (Integer) pairs[i + 1]);
        return map;
    }

    private static Map<String, Integer> count(WeightedRandom<String> random, int draws) {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < draws; i++) counts.merge(random.next(), 1, Integer::sum);
        return counts;
    }

    private static List<String> draw(WeightedRandom<String> random, int draws) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < draws; i++) result.add(random.next());
        return result;
    }

    //#region 분포
    @Test
    void 요구사항_예시_B만_2배면_40_20_20_20() {
        WeightedRandom<String> random = new WeightedRandom<>(weights("A", 1, "B", 2, "C", 1, "D", 1), new Random(42));
        Map<String, Integer> counts = count(random, DRAWS);

        Map<String, Double> expected = Map.of("A", 0.2, "B", 0.4, "C", 0.2, "D", 0.2);
        for (Map.Entry<String, Double> entry : expected.entrySet()) {
            double observed = counts.getOrDefault(entry.getKey(), 0) / (double) DRAWS;
            assertEquals(entry.getValue(), observed, TOLERANCE, entry.getKey());
        }
    }

    @Test
    void 여러_시드에서도_오차_범위_안() {
        for (long seed = 0; seed < 20; seed++) {
            Map<String, Integer> counts = count(
                    new WeightedRandom<>(weights("A", 1, "B", 2, "C", 1, "D", 1), new Random(seed)), DRAWS);
            assertEquals(0.4, counts.get("B") / (double) DRAWS, TOLERANCE, "seed " + seed);
        }
    }

    @Test
    void 가중치_0인_항목은_뽑히지_않는다() {
        Map<String, Integer> counts = count(
                new WeightedRandom<>(weights("A", 0, "B", 3, "C", 0, "D", 1, "E", 0), new Random(1)), DRAWS);
        assertFalse(counts.containsKey("A"));
        assertFalse(counts.containsKey("C"));
        assertFalse(counts.containsKey("E"));
        assertEquals(0.75, counts.get("B") / (double) DRAWS, TOLERANCE);
    }

    @Test
    void 항목이_하나면_항상_그_항목() {
        WeightedRandom<String> random = new WeightedRandom<>(weights("only", 5), new Random(3));
        assertTrue(draw(random, 100).stream().allMatch("only"::equals));
    }
    //#endregion

    //#region 재현성
    @Test
    void 같은_시드면_같은_순서() {
        Map<String, Integer> w = weights("A", 1, "B", 2, "C", 1, "D", 1);
        assertEquals(draw(new WeightedRandom<>(w, new Random(7)), 200),
                     draw(new WeightedRandom<>(w, new Random(7)), 200));
    }

    @Test
    void 생성_후_원본_맵을_바꿔도_영향_없음() {
        Map<String, Integer> w = weights("A", 1, "B", 1);
        WeightedRandom<String> random = new WeightedRandom<>(w, new Random(5));
        w.put("A", 0);
        w.put("C", 100);

        Map<String, Integer> counts = count(random, DRAWS);
        assertFalse(counts.containsKey("C"));
        assertEquals(0.5, counts.get("A") / (double) DRAWS, TOLERANCE);
    }
    //#endregion

    //#region 잘못된 입력
    @Test
    void 음수_가중치는_예외() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedRandom<>(weights("A", 1, "B", -1), new Random()));
    }

    @Test
    void 가중치_합이_0이면_예외() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedRandom<>(weights("A", 0, "B", 0), new Random()));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedRandom<>(Map.<String, Integer>of(), new Random()));
    }

    @Test
    void 가중치_합이_int_범위를_넘으면_예외() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedRandom<>(weights("A", Integer.MAX_VALUE, "B", 1), new Random()));
    }

    @Test
    void null_입력은_예외() {
        Map<String, Integer> nullWeight = new HashMap<>();
        nullWeight.put("A", null);
        Map<String, Integer> nullItem = new HashMap<>();
        nullItem.put(null, 1);

        assertThrows(IllegalArgumentException.class, () -> new WeightedRandom<>(null, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new WeightedRandom<>(weights("A", 1), null));
        assertThrows(IllegalArgumentException.class, () -> new WeightedRandom<>(nullWeight, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new WeightedRandom<>(nullItem, new Random()));
    }
    //#endregion
}
