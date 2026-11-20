package tetris.feature.raid;

import tetris.feature.raid.api.RaidMode;

/** 보스 HP·카운트다운·시간 (§4). 표에 있는 1~4레벨은 표 값을 쓰고, 5레벨부터 ×1.3 */
public final class RaidRules {
    public static final long COUNTDOWN_MILLIS = 3_000;
    public static final long CARD_PICK_MILLIS = 5_000;
    public static final long SYNC_MILLIS = 2_000;
    public static final long ULTIMATE_WINDOW_MILLIS = 1_500;
    public static final long AGGRO_WINDOW_MILLIS = 30_000;
    public static final long GOLDEN_FLEE_MILLIS = 15_000;
    /** 레이스 제한 시간. 일시정지·카운트다운은 세지 않는다 */
    public static final long RACE_LIMIT_MILLIS = 5 * 60 * 1000L;
    /** 레이스에서 먼저 보스를 잡은 사람이 상대 대기열에 넣는 쓰레기 줄 */
    public static final int RACE_STEAL_ROWS = 2;
    public static final int GAUGE_MAX = 100;
    public static final int ULTIMATE_DAMAGE = 150;
    public static final int GAUGE_PER_LINE = 10;
    public static final int GAUGE_SYNC = 20;
    public static final int REVIVE_LINES = 8;
    public static final int REVIVE_STEP = 4;

    private static final int[] COOP_HP = {0, 300, 390, 505, 660};
    private static final int[] SOLO_HP = {0, 165, 215, 280, 365};

    private RaidRules() {
    }

    /** 레벨 1은 8조각, 레벨마다 −1, 최소 4 */
    public static int countdown(int level) {
        if (level < 1) throw new IllegalArgumentException("level");
        return Math.max(4, 9 - level);
    }

    public static int hp(RaidMode mode, int level) {
        if (level < 1) throw new IllegalArgumentException("level");
        if (mode == RaidMode.SOLO || mode == RaidMode.RACE) {
            if (level < SOLO_HP.length) return SOLO_HP[level];
            return (int) Math.round(coopHp(level) * 0.55);
        }
        return coopHp(level);
    }

    /**
     * 황금 테트로는 패턴이 없고 15초 뒤에 도망간다.
     * 같은 레벨 HP의 15/55로 잡아, 평소 처치 속도면 제한 시간 안에 잡을 수 있게 한다.
     */
    public static int goldenHp(RaidMode mode, int level) {
        return Math.max(40, (int) Math.round(hp(mode, level) * 15.0 / 55.0));
    }

    private static int coopHp(int level) {
        if (level < COOP_HP.length) return COOP_HP[level];
        int hp = COOP_HP[COOP_HP.length - 1];
        for (int lv = COOP_HP.length; lv <= level; lv++) hp = (int) Math.round(hp * 1.3);
        return hp;
    }
}
