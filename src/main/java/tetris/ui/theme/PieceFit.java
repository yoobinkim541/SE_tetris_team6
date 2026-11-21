package tetris.ui.theme;

/**
 * 미리보기 조각을 칸 안에 맞춘다. 비율을 유지하고 가운데에 둔다.
 */
public final class PieceFit {
    public final int cell;
    public final int originX;
    public final int originY;
    public final int minRow;
    public final int minCol;
    public final int rows;
    public final int cols;

    private PieceFit(int cell, int originX, int originY, int minRow, int minCol, int rows, int cols) {
        this.cell = cell;
        this.originX = originX;
        this.originY = originY;
        this.minRow = minRow;
        this.minCol = minCol;
        this.rows = rows;
        this.cols = cols;
    }

    public static int cellSize(int[][] shape, int width, int height, int preferred) {
        return of(shape, 0, 0, width, height, preferred).cell;
    }

    public static PieceFit of(int[][] shape, int x, int y, int width, int height, int preferred) {
        int minRow = Integer.MAX_VALUE;
        int maxRow = -1;
        int minCol = Integer.MAX_VALUE;
        int maxCol = -1;
        if (shape != null) {
            for (int r = 0; r < shape.length; r++) {
                if (shape[r] == null) continue;
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] == 0) continue;
                    minRow = Math.min(minRow, r);
                    maxRow = Math.max(maxRow, r);
                    minCol = Math.min(minCol, c);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }
        if (maxRow < 0 || width <= 0 || height <= 0) {
            return new PieceFit(Math.max(1, preferred), x, y, 0, 0, 0, 0);
        }
        int rows = maxRow - minRow + 1;
        int cols = maxCol - minCol + 1;
        int fit = Math.min(width / cols, height / rows);
        if (preferred > 0) fit = Math.min(preferred, fit);
        int cell = Math.max(1, fit);
        int originX = x + (width - cols * cell) / 2;
        int originY = y + (height - rows * cell) / 2;
        return new PieceFit(cell, originX, originY, minRow, minCol, rows, cols);
    }
}
