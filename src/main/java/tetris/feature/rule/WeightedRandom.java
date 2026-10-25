package tetris.feature.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 가중치에 비례하는 확률로 항목 하나를 고른다 (Roulette Wheel Selection).
 * 항목 k가 뽑힐 확률은 (k의 가중치) / (가중치 합)이고, 매번 이전 결과와 무관하게 독립적으로 고른다.
 * 누적합 배열을 이분 탐색하므로 한 번 뽑을 때 Random을 정확히 한 번만 쓴다.
 */
public class WeightedRandom<T> {
    private final List<T> items = new ArrayList<>();
    private final int[] cumulative; // cumulative[i] = 0번부터 i번 항목까지의 가중치 합
    private final Random random;

    /**
     * weights의 순회 순서대로 항목을 등록한다. 생성 뒤 weights를 바꿔도 영향이 없다.
     * 가중치는 0 이상 정수이고 합이 1 이상이어야 한다. 가중치 0인 항목은 뽑히지 않는다.
     * 어기거나 null이 들어오면 IllegalArgumentException.
     */
    public WeightedRandom(Map<T, Integer> weights, Random random) {
        if (weights == null) throw new IllegalArgumentException("weights cannot be null");
        if (random == null) throw new IllegalArgumentException("random cannot be null");

        cumulative = new int[weights.size()];
        int total = 0;
        for (Map.Entry<T, Integer> entry : weights.entrySet()) {
            Integer weight = entry.getValue();
            if (entry.getKey() == null) throw new IllegalArgumentException("item cannot be null");
            if (weight == null || weight < 0)
                throw new IllegalArgumentException("weight should be 0 or more. Item : " + entry.getKey() + ", Value : " + weight);
            if (weight > Integer.MAX_VALUE - total)
                throw new IllegalArgumentException("sum of weights should not exceed " + Integer.MAX_VALUE);

            total += weight;
            cumulative[items.size()] = total;
            items.add(entry.getKey());
        }
        if (total < 1) throw new IllegalArgumentException("sum of weights should be 1 or more. Value : " + total);

        this.random = random;
    }

    /** 가중치 비율에 따라 항목 하나를 고른다 */
    public T next() {
        int pick = random.nextInt(cumulative[cumulative.length - 1]);
        return items.get(firstIndexAbove(pick));
    }

    // 누적합이 pick보다 큰 첫 항목의 위치. 가중치 0인 항목은 누적합이 바로 앞 항목과 같아서 여기서 건너뛰게 된다
    private int firstIndexAbove(int pick) {
        int index = Arrays.binarySearch(cumulative, pick + 1);
        if (index < 0) return -index - 1;
        while (index > 0 && cumulative[index - 1] == pick + 1) index--;
        return index;
    }
}
