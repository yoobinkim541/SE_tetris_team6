package tetris.feature.raid;

/**
 * 줄 삭제 데미지 (§4.1).
 * 기본값에 T-스핀 ×1.5를 곱하고, 콤보 2부터 (콤보-1)×5를 더한 뒤,
 * 크리티컬 ×2, 동시 공격 ×1.5, 날카로운 블록 1.1^중첩을 곱하고 마지막에 한 번 반올림한다.
 * 필살기 150은 여기 넣지 않는다.
 */
public final class RaidDamage {
    public static final int[] LINE = {0, 10, 25, 45, 70};

    private RaidDamage() {
    }

    public static int of(int lines, boolean tSpin, int combo, boolean crit, boolean sync, int sharpStacks) {
        if (lines <= 0) return 0;
        int clamped = Math.min(4, lines);
        double damage = LINE[clamped];
        if (tSpin && clamped > 0) damage *= 1.5;
        if (combo >= 2) damage += 5.0 * (combo - 1);
        if (crit) damage *= 2;
        if (sync) damage *= 1.5;
        if (sharpStacks > 0) damage *= Math.pow(1.1, sharpStacks);
        return (int) Math.round(damage);
    }
}
