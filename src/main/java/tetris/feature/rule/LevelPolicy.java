package tetris.feature.rule;

/**
 * Level·카운터·낙하 속도 (LVL-1~5, §4 L5/S3).
 * Level은 줄 삭제(onLinesCleared)와 Spawn(onBlockSpawned)에서만 바뀐다.
 */
public class LevelPolicy {
    public LevelPolicy() {
        // Level 1, 카운터 0
    }

    /** 낙하 간격(ms) = max(250, 1200 - 200 * level) */
    public static int fallIntervalMillis(int level) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 현재 Level의 낙하 간격(ms) */
    public int fallIntervalMillis() {
        throw new UnsupportedOperationException("TODO");
    }

    /** L5: lineCounter += lines, 5 이상이면 Level +1 후 5 차감 */
    public void onLinesCleared(int lines) {
        // 카운터 누적, 기준 도달 시 Level 상승
    }

    /** S3: blockCounter += 1, 10 이상이면 Level +1 후 10 차감 */
    public void onBlockSpawned() {
        // 카운터 누적, 기준 도달 시 Level 상승
    }

    public int getLevel() {
        throw new UnsupportedOperationException("TODO");
    }

    public int getLineCounter() {
        throw new UnsupportedOperationException("TODO");
    }

    public int getBlockCounter() {
        throw new UnsupportedOperationException("TODO");
    }

    /** 새 게임/Restart: Level 1, 카운터 0 */
    public void reset() {
        // 초기화
    }
}
