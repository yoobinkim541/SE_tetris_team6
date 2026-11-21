package tetris.feature.raid;

import tetris.feature.versus.api.VersusRules;

/** 럭키 칸 20x10. 줄 삭제·공격 줄 삽입과 같은 방향으로 민다 */
final class LuckyGrid {
    private LuckyGrid() {
    }

    static int[][] empty() {
        return new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
    }

    static void mark(int[][] grid, int row, int col) {
        if (row < 0 || row >= grid.length || col < 0 || col >= grid[0].length) return;
        grid[row][col] = 1;
    }

    static boolean rowHas(int[][] grid, int row) {
        if (row < 0 || row >= grid.length) return false;
        for (int value : grid[row]) if (value != 0) return true;
        return false;
    }

    /** 공격 줄이 바닥으로 들어오며 위 칸이 올라간 뒤의 럭키 칸 */
    static int[][] shiftUp(int[][] grid, int count) {
        int[][] out = empty();
        if (count <= 0) return copy(grid);
        for (int row = 0; row < grid.length - count; row++) out[row] = grid[row + count].clone();
        return out;
    }

    /** 지운 행을 빼고 위 칸을 아래로 모은다 */
    static int[][] dropRows(int[][] grid, int[] removed) {
        boolean[] gone = new boolean[grid.length];
        if (removed != null) {
            for (int row : removed) if (row >= 0 && row < gone.length) gone[row] = true;
        }
        int[][] out = empty();
        int write = grid.length - 1;
        for (int read = grid.length - 1; read >= 0; read--) {
            if (gone[read]) continue;
            out[write--] = grid[read].clone();
        }
        return out;
    }

    private static int[][] copy(int[][] grid) {
        int[][] out = new int[grid.length][];
        for (int row = 0; row < grid.length; row++) out[row] = grid[row].clone();
        return out;
    }
}
