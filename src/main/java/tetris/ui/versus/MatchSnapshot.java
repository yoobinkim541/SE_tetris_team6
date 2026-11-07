package tetris.ui.versus;

import java.util.List;
import java.util.Objects;

/** Provisional B-owned display DTO; adapt A's API here when S7-A1 is available. */
public record MatchSnapshot(String matchId, long revision, Player one, Player two,
                            long remainingMillis, String status) {
    public static final int ATTACK_CELL = 9;
    public MatchSnapshot {
        if (Objects.requireNonNull(matchId).isBlank() || revision < 0 || remainingMillis < -1)
            throw new IllegalArgumentException("invalid match identity/time");
        Objects.requireNonNull(one); Objects.requireNonNull(two); Objects.requireNonNull(status);
    }

    /** cells contains visible fixed + active cells. Next is at most 4x4; queue is top to bottom. */
    public record Player(int[][] cells, int[][] next, long score, List<int[]> pendingRows) {
        public Player {
            cells = copy(cells, 20, 10);
            if (next == null || next.length > 4) throw new IllegalArgumentException("invalid Next");
            next = copy(next, next.length, next.length == 0 ? 0 : next[0].length);
            if (next.length > 0 && next[0].length > 4 || score < 0)
                throw new IllegalArgumentException("invalid Next/score");
            if (pendingRows == null || pendingRows.size() > 10)
                throw new IllegalArgumentException("queue must contain 0..10 rows");
            pendingRows = pendingRows.stream().map(row -> copy(new int[][]{row},1,10)[0]).toList();
            for (int[] row : pendingRows) for (int value : row)
                if (value != 0 && value != ATTACK_CELL) throw new IllegalArgumentException("invalid attack row");
        }
        @Override public int[][] cells() { return copy(cells,20,10); }
        @Override public int[][] next() { return copy(next,next.length,next.length == 0 ? 0 : next[0].length); }
        @Override public List<int[]> pendingRows() { return pendingRows.stream().map(int[]::clone).toList(); }
    }

    private static int[][] copy(int[][] source, int height, int width) {
        if (source == null || source.length != height) throw new IllegalArgumentException("invalid height");
        int[][] result = new int[height][];
        for (int r=0;r<height;r++) {
            if (source[r] == null || source[r].length != width) throw new IllegalArgumentException("invalid width");
            result[r] = source[r].clone();
            for (int value : result[r]) if (value < 0 || value > ATTACK_CELL)
                throw new IllegalArgumentException("unknown cell value");
        }
        return result;
    }
}
