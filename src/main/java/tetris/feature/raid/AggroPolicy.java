package tetris.feature.raid;

/** 최근 30초 데미지 비율로 P1이 대상이 될 확률 (§4.3). 둘 다 0이면 번갈아 가므로 NaN */
public final class AggroPolicy {
    private AggroPolicy() {
    }

    public static double chanceP1(long damage1, long damage2) {
        long total = damage1 + damage2;
        if (total <= 0) return Double.NaN;
        double chance = 0.5 + 0.3 * (damage1 - damage2) / (double) total;
        return Math.max(0.2, Math.min(0.8, chance));
    }
}
