package tetris.feature.raid;

import java.util.Random;

/** 럭키 블록. 기본 10개 중 1개, 행운 카드가 켜진 보스 동안에는 5개 중 1개 */
public final class RaidLuck {
    private RaidLuck() {
    }

    public static boolean roll(Random random, boolean doubled) {
        return random.nextInt(doubled ? 5 : 10) == 0;
    }
}
