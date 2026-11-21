package tetris.ui.theme;

import java.awt.Rectangle;
import tetris.component.block.Block;
import tetris.feature.data.Board;

/**
 * 싱글 화면 배치. 왕관은 보드 위 여백에만 있고, 홀드·다음은 보드 좌우 탭이다.
 * 테스트는 이 사각형으로 픽셀을 읽는다.
 */
public final class SoloLayout {
    public final int cell;
    public final int width;
    public final int height;
    public final Rectangle board;
    public final Rectangle crown;
    public final Rectangle hold;
    public final Rectangle next;
    public final Rectangle effects;
    public final Rectangle guide;
    public final Rectangle score;
    public final Rectangle level;
    public final Rectangle lines;
    public final Rectangle time;
    public final Rectangle status;

    private SoloLayout(int cell, int width, int height, Rectangle board, Rectangle crown, Rectangle hold,
                       Rectangle next, Rectangle effects, Rectangle guide, Rectangle score, Rectangle level,
                       Rectangle lines, Rectangle time, Rectangle status) {
        this.cell = cell;
        this.width = width;
        this.height = height;
        this.board = board;
        this.crown = crown;
        this.hold = hold;
        this.next = next;
        this.effects = effects;
        this.guide = guide;
        this.score = score;
        this.level = level;
        this.lines = lines;
        this.time = time;
        this.status = status;
    }

    public static SoloLayout of(int cell) {
        int safe = Math.max(1, cell);
        int frame = GameTheme.snap8(Math.max(16, safe / 2));
        int gap = 8;
        int side = GameTheme.snap8(safe * 5 + frame);
        int crownH = GameTheme.snap8(Math.max(56, safe * 2));
        int labelH = 16;
        int hud = GameTheme.snap8(Math.max(40, safe + 8));
        int guideH = GameTheme.snap8(Math.max(32, safe));
        int width = side * 2 + safe * Board.WIDTH;
        int height = crownH + safe * Board.HEIGHT + labelH + hud + guideH;
        Rectangle board = new Rectangle(side, crownH, safe * Board.WIDTH, safe * Board.HEIGHT);
        Rectangle crown = new Rectangle(board.x, 0, board.width, board.y);
        int tabW = Math.max(8, side - frame - gap);
        Rectangle hold = new Rectangle(gap, board.y, tabW, GameTheme.snap8(safe * 5));
        Rectangle next = new Rectangle(board.x + board.width + frame, board.y, tabW, GameTheme.snap8(safe * 8));
        int effectsTop = hold.y + hold.height + gap;
        int effectsBottom = board.y + board.height;
        Rectangle effects = new Rectangle(gap, effectsTop, tabW, Math.max(safe, effectsBottom - effectsTop));
        int inner = width - gap * 5;
        int scoreW = GameTheme.snap8(Math.max(safe * 6, inner * 2 / 5));
        int other = Math.max(8, (inner - scoreW) / 3);
        int tabY = board.y + board.height + labelH;
        Rectangle score = new Rectangle(gap, tabY, scoreW, hud);
        Rectangle level = new Rectangle(gap * 2 + scoreW, tabY, other, hud);
        Rectangle lines = new Rectangle(gap * 3 + scoreW + other, tabY, other, hud);
        int timeX = gap * 4 + scoreW + other * 2;
        Rectangle time = new Rectangle(timeX, tabY, Math.max(8, width - gap - timeX), hud);
        Rectangle guide = new Rectangle(gap, height - guideH, width - gap * 2, Math.max(8, guideH - 8));
        int statusTop = next.y + next.height + gap;
        Rectangle status = new Rectangle(next.x, statusTop, tabW, Math.max(24, tabY - statusTop - 8));
        return new SoloLayout(safe, width, height, board, crown, hold, next, effects, guide, score, level, lines, time, status);
    }

    public Rectangle boardCell(int row, int col) {
        return new Rectangle(board.x + col * cell, board.y + row * cell, cell, cell);
    }

    /** 다음 칸 상자 안에 조각을 가운데 둔 뒤, 모양의 (localRow, localCol) 칸 */
    public Rectangle centeredCell(Rectangle box, Block piece, int localRow, int localCol, int pieceCell) {
        if (piece == null || box.width <= 0 || box.height <= 0) return new Rectangle();
        int[][] shape = piece.getShape();
        int minRow = Integer.MAX_VALUE, maxRow = -1, minCol = Integer.MAX_VALUE, maxCol = -1;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 0) continue;
                minRow = Math.min(minRow, r);
                maxRow = Math.max(maxRow, r);
                minCol = Math.min(minCol, c);
                maxCol = Math.max(maxCol, c);
            }
        }
        if (maxRow < 0) return new Rectangle();
        int startX = box.x + (box.width - (maxCol - minCol + 1) * pieceCell) / 2;
        int startY = box.y + (box.height - (maxRow - minRow + 1) * pieceCell) / 2;
        return new Rectangle(startX + (localCol - minCol) * pieceCell, startY + (localRow - minRow) * pieceCell, pieceCell, pieceCell);
    }
}
