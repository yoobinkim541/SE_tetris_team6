package tetris.feature.rule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import tetris.component.block.Block;
import tetris.component.block.BlockType;

/**
 * 가중치 bag 방식으로 다음 블록을 만든다. 같은 블록이 몰리거나 한 종류가 오래 안 나오는 일을 줄인다.
 *
 * - 가중치를 최대공약수로 나눈 개수만큼 블록을 담은 큰 주머니를 만든다. 주머니를 다 쓰면 새로 만든다.
 *   그래서 주머니 하나 단위로는 난이도 가중치 비율(EASY I 6/35, NORMAL 1/7, HARD I 4/35)이 정확히 지켜진다.
 * - 큰 주머니는 블록 7개 안팎의 작은 묶음(round)으로 나눠, 묶음마다 거의 모든 종류가 한 번씩 들어가게 한다.
 *   묶음 안 순서만 섞으므로 7-bag처럼 고르게 나온다 (EASY/HARD도 같은 블록 3연속 이하, 같은 종류 간격 24 이하).
 * - NORMAL(가중치 모두 같음)은 묶음 하나 = 7종 1개씩이라 표준 7-bag과 같다.
 */
public class BagBlockGenerator implements BlockGenerator {
    private static final int TYPES_PER_ROUND = 7;

    private final Map<BlockType, Integer> counts = new EnumMap<>(BlockType.class);
    private final Map<BlockType, Integer> offsets = new EnumMap<>(BlockType.class); // 남는 몫을 넣을 묶음의 시작 위치
    private final int rounds;
    private final Random random;
    private final Deque<BlockType> bag = new ArrayDeque<>();

    /** weights 규칙은 WeightedBlockGenerator와 같다. 일반 블록 7종 외의 종류나 잘못된 가중치는 IllegalArgumentException */
    public BagBlockGenerator(Map<BlockType, Integer> weights, Random random) {
        if (weights == null) throw new IllegalArgumentException("weights cannot be null");
        if (random == null) throw new IllegalArgumentException("random cannot be null");
        int gcd = 0;
        for (Map.Entry<BlockType, Integer> entry : weights.entrySet()) {
            BlockType type = entry.getKey();
            Integer weight = entry.getValue();
            if (type == null || !WeightedBlockGenerator.SUPPORTED.contains(type))
                throw new IllegalArgumentException("unsupported block type : " + type);
            if (weight == null || weight < 0)
                throw new IllegalArgumentException("weight should be 0 or more. Item : " + type + ", Value : " + weight);
            if (weight > 0) gcd = gcd(gcd, weight);
        }
        if (gcd == 0) throw new IllegalArgumentException("sum of weights should be 1 or more");

        int total = 0;
        for (Map.Entry<BlockType, Integer> entry : weights.entrySet()) {
            if (entry.getValue() == 0) continue;
            counts.put(entry.getKey(), entry.getValue() / gcd);
            total += entry.getValue() / gcd;
        }
        this.rounds = Math.max(1, total / TYPES_PER_ROUND);
        this.random = random;
        for (BlockType type : counts.keySet()) offsets.put(type, random.nextInt(rounds));
    }

    @Override
    public Block next() {
        if (bag.isEmpty()) refill();
        return WeightedBlockGenerator.create(bag.poll());
    }

    /*
     * 종류마다 개수를 묶음들에 고르게 나눠 담고, 묶음 안만 섞어 이어 붙인다.
     * 나누어떨어지지 않는 몫은 같은 간격으로 떨어진 묶음에 하나씩 넣는다. 시작 위치는 종류마다 생성 때 한 번 정해서
     * 주머니가 바뀌어도 간격이 유지된다. 그래서 한 종류가 빠지거나 2개 들어가는 묶음이 연달아 오지 않는다.
     */
    private void refill() {
        List<List<BlockType>> groups = new ArrayList<>();
        for (int i = 0; i < rounds; i++) groups.add(new ArrayList<>());

        for (Map.Entry<BlockType, Integer> entry : counts.entrySet()) {
            BlockType type = entry.getKey();
            int base = entry.getValue() / rounds;
            int extra = entry.getValue() % rounds;
            for (List<BlockType> group : groups)
                for (int k = 0; k < base; k++) group.add(type);
            for (int k = 0; k < extra; k++) groups.get((offsets.get(type) + k * rounds / extra) % rounds).add(type);
        }
        for (List<BlockType> group : groups) {
            Collections.shuffle(group, random);
            bag.addAll(group);
        }
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
