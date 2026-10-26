package tetris.feature.rule;

import tetris.feature.game.Difficulty;

/**
 * 현재 Level과 Level 상승 카운터를 관리하고, Level·난이도에 맞는 블록 낙하 간격을 계산한다.
 * Level은 줄을 지웠을 때(onLinesCleared)와 블록이 새로 나왔을 때(onBlockSpawned)에만 바뀐다.
 * 난이도는 Level이 오를 때 빨라지는 폭만 바꾸고, Level이 오르는 조건은 모든 난이도가 같다.
 */
public class LevelPolicy {
    private static final int BLOCKS_PER_LEVEL = 10;
    private static final int LINES_PER_LEVEL = 5;
    private static final int MAX_LINES_AT_ONCE = 4; // 블록 하나는 최대 4줄을 채운다
    private static final int FIRST_LEVEL_INTERVAL_MILLIS = 1000; // Level 1은 난이도와 관계없이 1초에 한 칸
    private static final int MIN_INTERVAL_MILLIS = 250;

    private final Difficulty difficulty;
    private int level;
    private int lineCounter;
    private int blockCounter;

    /** NORMAL 난이도. 아직 난이도를 넘기지 않는 호출부(GameSession)가 그대로 동작하도록 남겨 둔다 */
    public LevelPolicy() {
        this(Difficulty.NORMAL);
    }

    /** 한 판 동안 쓸 난이도를 정한다. reset()해도 난이도는 바뀌지 않는다 */
    public LevelPolicy(Difficulty difficulty) {
        if (difficulty == null) throw new IllegalArgumentException("difficulty cannot be null");
        this.difficulty = difficulty;
        reset();
    }

    /**
     * 주어진 Level·난이도의 낙하 간격(ms). Level 1에서 1000이고, Level이 하나 오를 때마다
     * 난이도의 감소폭(EASY 160, NORMAL 200, HARD 240)만큼 줄어들며 250 아래로는 내려가지 않는다.
     * NORMAL은 1차 때 쓰던 간격과 모든 Level에서 같다. level이 1 미만이면 IllegalArgumentException.
     */
    public static int fallIntervalMillis(int level, Difficulty difficulty) {
        if (level < 1) throw new IllegalArgumentException("level should be 1 or more. Value : " + level);
        if (difficulty == null) throw new IllegalArgumentException("difficulty cannot be null");
        long interval = FIRST_LEVEL_INTERVAL_MILLIS - (long) difficulty.levelStepMillis() * (level - 1);
        return (int) Math.max(MIN_INTERVAL_MILLIS, interval);
    }

    /** NORMAL 난이도 기준 낙하 간격(ms) */
    public static int fallIntervalMillis(int level) {
        return fallIntervalMillis(level, Difficulty.NORMAL);
    }

    /** 현재 Level과 이 정책의 난이도에 맞는 낙하 간격(ms) */
    public int fallIntervalMillis() {
        return fallIntervalMillis(level, difficulty);
    }

    /**
     * 한 번에 지운 줄 수(0..4)를 누적하고, 5줄이 모이면 Level을 하나 올린다.
     * 5를 넘긴 나머지는 다음 Level 계산에 이어진다. 블록 카운터와는 따로 센다.
     */
    public void onLinesCleared(int lines) {
        if (lines < 0 || lines > MAX_LINES_AT_ONCE)
            throw new IllegalArgumentException(String.format("lines should be between %d and %d. Value : %d", 0, MAX_LINES_AT_ONCE, lines));

        lineCounter += lines;
        if (lineCounter >= LINES_PER_LEVEL) {
            level++;
            lineCounter -= LINES_PER_LEVEL;
        }
    }

    /** 블록이 새로 나올 때마다 세고, 10개가 되면 Level을 하나 올린다 */
    public void onBlockSpawned() {
        blockCounter++;
        if (blockCounter >= BLOCKS_PER_LEVEL) {
            level++;
            blockCounter -= BLOCKS_PER_LEVEL;
        }
    }

    public int getLevel() {
        return level;
    }

    public int getLineCounter() {
        return lineCounter;
    }

    public int getBlockCounter() {
        return blockCounter;
    }

    /** Level 1, 카운터 0으로 되돌린다. 새 게임과 Restart에서만 호출한다 */
    public void reset() {
        level = 1;
        lineCounter = 0;
        blockCounter = 0;
    }
}
