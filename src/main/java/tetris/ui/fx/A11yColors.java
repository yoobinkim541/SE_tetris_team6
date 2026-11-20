package tetris.ui.fx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

/**
 * 색맹 모드 강조용 색. Okabe–Ito라서 적록·청황만으로 구분하지 않는다.
 * 의미는 색과 함께 글자·무늬·화살표로 남긴다.
 */
public final class A11yColors {
    public static final Color ORANGE = new Color(0xE69F00);
    public static final Color SKY = new Color(0x56B4E9);
    public static final Color BLUISH_GREEN = new Color(0x009E73);
    public static final Color YELLOW = new Color(0xF0E442);
    public static final Color BLUE = new Color(0x0072B2);
    public static final Color VERMILION = new Color(0xD55E00);
    public static final Color PURPLE = new Color(0xCC79A7);
    public static final Color WHITE = Color.WHITE;
    public static final Color BLACK = Color.BLACK;

    private A11yColors() {
    }

    public static Color accent(boolean colorBlind) {
        return colorBlind ? YELLOW : new Color(0xFFC94D);
    }

    public static Color warning(boolean colorBlind) {
        return colorBlind ? VERMILION : new Color(0xFF8A65);
    }

    public static Color hp(boolean colorBlind) {
        return colorBlind ? VERMILION : new Color(0xFF4D6A);
    }

    public static Color splitHp(boolean colorBlind) {
        return colorBlind ? SKY : new Color(0x3DDC8A);
    }

    public static Color gauge(boolean colorBlind) {
        return colorBlind ? BLUE : new Color(0x3DE0FF);
    }

    public static Color target(boolean colorBlind) {
        return colorBlind ? ORANGE : new Color(255, 48, 72);
    }

    public static Color lucky(boolean colorBlind) {
        return colorBlind ? WHITE : new Color(0xFFC94D);
    }

    public static Color selected(boolean colorBlind) {
        return colorBlind ? ORANGE : new Color(0x3DE0FF);
    }

    public static Color mine(boolean colorBlind) {
        return colorBlind ? ORANGE : new Color(0x8BD3FF);
    }

    /** 착지 미리보기. 색맹 모드는 점선 윤곽과 대각선이라 색조만으로 구분하지 않는다 */
    public static void paintGhostCell(Graphics2D g, int x, int y, int size, boolean colorBlind) {
        if (size <= 1) return;
        Color mark = colorBlind ? WHITE : new Color(0xFFC94D);
        Stroke previous = g.getStroke();
        float dash = Math.max(2f, size / 4f);
        g.setStroke(new BasicStroke(Math.max(1.5f, size / 8f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[] {dash, dash}, 0f));
        g.setColor(mark);
        int inset = Math.max(1, size / 8);
        g.drawRect(x + inset, y + inset, Math.max(1, size - inset * 2 - 1), Math.max(1, size - inset * 2 - 1));
        g.setStroke(previous);
        g.drawLine(x + inset, y + inset, x + size - inset - 1, y + size - inset - 1);
        g.drawLine(x + size - inset - 1, y + inset, x + inset, y + size - inset - 1);
    }

    public static void paintLuckyMark(Graphics2D g, int x, int y, int size, boolean colorBlind) {
        Stroke previous = g.getStroke();
        int inset = Math.max(1, size / 6);
        int cx = x + size / 2;
        int cy = y + size / 2;
        int arm = Math.max(2, size / 2 - inset);
        g.setStroke(new BasicStroke(Math.max(2f, size / 6f)));
        g.setColor(BLACK);
        drawStar(g, x, y, size, cx, cy, arm, inset);
        g.setStroke(new BasicStroke(1f));
        g.setColor(colorBlind ? WHITE : new Color(0xFFC94D));
        drawStar(g, x + 1, y + 1, size - 2, cx, cy, Math.max(1, arm - 1), inset);
        g.setStroke(previous);
    }

    private static void drawStar(Graphics2D g, int x, int y, int size, int cx, int cy, int arm, int inset) {
        if (size <= 1) return;
        g.drawRect(x + inset, y + inset, Math.max(1, size - inset * 2 - 1), Math.max(1, size - inset * 2 - 1));
        g.drawLine(cx, cy - arm, cx, cy + arm);
        g.drawLine(cx - arm, cy, cx + arm, cy);
        g.drawLine(cx - arm, cy - arm, cx + arm, cy + arm);
        g.drawLine(cx + arm, cy - arm, cx - arm, cy + arm);
    }
}
