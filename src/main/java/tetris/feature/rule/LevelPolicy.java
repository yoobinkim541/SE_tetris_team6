package tetris.feature.rule;

/**
 * Level·카운터·낙하 속도 (LVL-1~5, §4 L5/S3).
 * Level은 줄 삭제(onLinesCleared)와 Spawn(onBlockSpawned)에서만 바뀐다.
 */
public class LevelPolicy {
    private static final int BLOCKS_PER_LEVEL = 10;
    private static final int LINES_PER_LEVEL = 5;
    private static final int MAX_LINES_AT_ONCE = 4; // CLR-3

    private int level;
    private int lineCounter;
    private int blockCounter;

    public LevelPolicy() {
        reset();
    }

    /** LVL-2 */
    public static int fallIntervalMillis(int level) {
        return Math.max(250, 1200 - 200 * level);
    }

    public int fallIntervalMillis() {
        return fallIntervalMillis(level);
    }

    /** L5. 두 카운터는 서로 독립이고 기준값만큼만 차감해 나머지를 유지한다 (LVL-3). lines는 0..4 */
    public void onLinesCleared(int lines) {
        if (lines < 0 || lines > MAX_LINES_AT_ONCE)
            throw new IllegalArgumentException(String.format("lines should be between %d and %d. Value : %d", 0, MAX_LINES_AT_ONCE, lines));

        lineCounter += lines;
        if (lineCounter >= LINES_PER_LEVEL) {
            level++;
            lineCounter -= LINES_PER_LEVEL;
        }
    }

    /** S3 */
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

    /** 새 게임/Restart에서만 호출한다 (LVL-4) */
    public void reset() {
        level = 1;
        lineCounter = 0;
        blockCounter = 0;
    }
}
