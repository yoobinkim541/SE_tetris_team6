package tetris.feature.versus.api;

import java.util.List;
import java.util.Objects;

/**
 * 화면·네트워크가 읽는 경기 상태 사본 (R3-LIVE, R3-GARBAGE, R3-TIME). 클라이언트 표시 전용. 배열은 생성·조회 때 모두 복사한다.
 * 칸 값: 0=빈칸, 1~8=블록 색, {@link VersusRules#ATTACK_CELL}=공격 줄.
 *
 * @param revision        같은 matchId 안에서 단조 증가. 오래된 STATE를 버리는 기준
 * @param remainingMillis TIME_LIMIT의 남은 시간. 시간 제한이 없으면 -1
 * @param result          FINISHED일 때만 non-null
 */
public record MatchSnapshot(String matchId, long revision, PlayerView one, PlayerView two,
                            long remainingMillis, MatchPhase phase, MatchResult result) {
    public MatchSnapshot {
        Objects.requireNonNull(matchId, "matchId");
        if (matchId.isBlank()) throw new IllegalArgumentException("blank matchId");
        if (revision < 0) throw new IllegalArgumentException("negative revision");
        if (remainingMillis < -1) throw new IllegalArgumentException("remainingMillis must be >= -1");
        Objects.requireNonNull(one, "one");
        Objects.requireNonNull(two, "two");
        Objects.requireNonNull(phase, "phase");
        if ((phase == MatchPhase.FINISHED) != (result != null)) {
            throw new IllegalArgumentException("result must be present exactly when FINISHED");
        }
    }

    public PlayerView player(PlayerId id) {
        return id == PlayerId.ONE ? one : two;
    }

    /**
     * 한 참가자의 화면 정보.
     *
     * @param cells       20x10. 고정 칸 + 떨어지는 블록
     * @param next        다음 블록 모양, 최대 4x4. 비어 있을 수 있다
     * @param pendingRows 다음 Spawn 전에 보드 아래로 들어올 공격 줄. 위에서 아래 순서, 0~10줄
     */
    public record PlayerView(int[][] cells, int[][] next, long score, List<int[]> pendingRows) {
        public PlayerView {
            cells = copyGrid(cells, VersusRules.BOARD_ROWS, VersusRules.BOARD_COLS);
            if (next == null || next.length > 4) throw new IllegalArgumentException("Next must be at most 4 rows");
            int nextWidth = next.length == 0 ? 0 : (next[0] == null ? -1 : next[0].length);
            if (nextWidth > 4) throw new IllegalArgumentException("Next must be at most 4 columns");
            next = copyGrid(next, next.length, nextWidth);
            if (score < 0) throw new IllegalArgumentException("negative score");
            if (pendingRows == null || pendingRows.size() > VersusRules.MAX_PENDING_ROWS) {
                throw new IllegalArgumentException("pendingRows must hold 0.." + VersusRules.MAX_PENDING_ROWS + " rows");
            }
            pendingRows = pendingRows.stream().map(AttackRows::copyRow).toList();
        }

        @Override
        public int[][] cells() {
            return copyGrid(cells, cells.length, VersusRules.BOARD_COLS);
        }

        @Override
        public int[][] next() {
            return copyGrid(next, next.length, next.length == 0 ? 0 : next[0].length);
        }

        @Override
        public List<int[]> pendingRows() {
            return pendingRows.stream().map(int[]::clone).toList();
        }

    }

    static int[][] copyGrid(int[][] source, int height, int width) {
        if (source == null || source.length != height) throw new IllegalArgumentException("grid height");
        int[][] result = new int[height][];
        for (int r = 0; r < height; r++) {
            if (source[r] == null || source[r].length != width) throw new IllegalArgumentException("grid width");
            for (int v : source[r]) {
                if (v < 0 || v > VersusRules.ATTACK_CELL) throw new IllegalArgumentException("cell value " + v);
            }
            result[r] = source[r].clone();
        }
        return result;
    }
}
