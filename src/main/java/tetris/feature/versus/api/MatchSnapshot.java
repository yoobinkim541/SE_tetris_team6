package tetris.feature.versus.api;

import java.util.ArrayList;
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
     * @param next        다음 블록 모양, 최대 4x4. 비어 있을 수 있다. previews의 맨 앞과 같다
     * @param pendingRows 다음 Spawn 전에 보드 아래로 들어올 공격 줄. 위에서 아래 순서, 0~10줄
     * @param items       cells와 같은 20x10. 아이템이 붙은 칸은 표시 문자({@link VersusRules#ITEM_SYMBOLS}), 나머지는 0.
     *                    아이템 대전에서만 채워진다 (R3-ITEM). 공격 칸에는 아이템이 없다
     * @param hold        홀드 블록 모양. 비어 있으면 길이 0 (HLD-1)
     * @param ghost       20x10 착지 미리보기. 0이 아닌 칸만 고스트 (GST-1)
     * @param previews    Next 미리보기, 최대 3개. 비어 있으면 next 하나만 그린다 (NXT-2)
     * @param tSpinLocks  이 판에서 T-스핀으로 잠근 횟수. 반복해도 늘어나 효과음을 한 번씩 낸다
     */
    public record PlayerView(int[][] cells, int[][] next, long score, List<int[]> pendingRows, int[][] items,
                             int[][] hold, int[][] ghost, List<int[][]> previews, int tSpinLocks) {
        /** 아이템·홀드·고스트가 없는 화면 정보 (일반·시간제한 대전) */
        public PlayerView(int[][] cells, int[][] next, long score, List<int[]> pendingRows) {
            this(cells, next, score, pendingRows, new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS]);
        }

        /** 홀드·고스트·추가 넥스트가 없는 화면 정보 */
        public PlayerView(int[][] cells, int[][] next, long score, List<int[]> pendingRows, int[][] items) {
            this(cells, next, score, pendingRows, items, new int[0][], emptyGhost(), List.of(), 0);
        }

        /** T-스핀 횟수가 없는 화면 정보 */
        public PlayerView(int[][] cells, int[][] next, long score, List<int[]> pendingRows, int[][] items,
                          int[][] hold, int[][] ghost, List<int[][]> previews) {
            this(cells, next, score, pendingRows, items, hold, ghost, previews, 0);
        }

        public PlayerView {
            cells = copyGrid(cells, VersusRules.BOARD_ROWS, VersusRules.BOARD_COLS);
            next = copyShape(next, "Next");
            if (score < 0) throw new IllegalArgumentException("negative score");
            if (pendingRows == null || pendingRows.size() > VersusRules.MAX_PENDING_ROWS) {
                throw new IllegalArgumentException("pendingRows must hold 0.." + VersusRules.MAX_PENDING_ROWS + " rows");
            }
            pendingRows = pendingRows.stream().map(AttackRows::copyRow).toList();
            items = copyItems(items, cells);
            hold = hold == null ? new int[0][] : copyShape(hold, "hold");
            ghost = copyGhost(ghost);
            if (previews == null) previews = List.of();
            if (previews.size() > 3) throw new IllegalArgumentException("at most 3 previews");
            List<int[][]> copied = new ArrayList<>();
            for (int[][] shape : previews) copied.add(copyShape(shape, "preview"));
            previews = List.copyOf(copied);
            if (tSpinLocks < 0) throw new IllegalArgumentException("negative tSpinLocks");
        }

        @Override
        public int[][] items() {
            return copyItems(items, cells);
        }

        private static int[][] copyItems(int[][] source, int[][] cells) {
            if (source == null || source.length != VersusRules.BOARD_ROWS) throw new IllegalArgumentException("items height");
            int[][] result = new int[source.length][];
            for (int r = 0; r < source.length; r++) {
                if (source[r] == null || source[r].length != VersusRules.BOARD_COLS) {
                    throw new IllegalArgumentException("items width");
                }
                for (int c = 0; c < source[r].length; c++) {
                    int v = source[r][c];
                    if (v == 0) continue;
                    if (VersusRules.ITEM_SYMBOLS.indexOf(v) < 0) throw new IllegalArgumentException("item symbol " + v);
                    if (cells[r][c] == 0 || cells[r][c] == VersusRules.ATTACK_CELL) {
                        throw new IllegalArgumentException("item on an empty or attack cell");
                    }
                }
                result[r] = source[r].clone();
            }
            return result;
        }

        @Override
        public int[][] cells() {
            return copyGrid(cells, cells.length, VersusRules.BOARD_COLS);
        }

        @Override
        public int[][] next() {
            return copyShape(next, "Next");
        }

        @Override
        public int[][] hold() {
            return copyShape(hold, "hold");
        }

        @Override
        public int[][] ghost() {
            return copyGhost(ghost);
        }

        @Override
        public List<int[][]> previews() {
            return previews.stream().map(shape -> copyShape(shape, "preview")).toList();
        }

        @Override
        public List<int[]> pendingRows() {
            return pendingRows.stream().map(int[]::clone).toList();
        }

        private static int[][] emptyGhost() {
            return new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        }

        private static int[][] copyShape(int[][] shape, String name) {
            if (shape == null || shape.length > 4) throw new IllegalArgumentException(name + " must be at most 4 rows");
            int width = shape.length == 0 ? 0 : (shape[0] == null ? -1 : shape[0].length);
            if (width > 4) throw new IllegalArgumentException(name + " must be at most 4 columns");
            return copyGrid(shape, shape.length, width);
        }

        private static int[][] copyGhost(int[][] source) {
            if (source == null) return emptyGhost();
            int[][] copied = copyGrid(source, VersusRules.BOARD_ROWS, VersusRules.BOARD_COLS);
            for (int[] row : copied) {
                for (int value : row) {
                    if (value == VersusRules.ATTACK_CELL) throw new IllegalArgumentException("ghost cannot be an attack cell");
                }
            }
            return copied;
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
