package tetris.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/** 화면 공통 색·글꼴과 그리기 도우미. 모든 패널이 같은 모양의 제목·메뉴·안내 문구를 쓰도록 한 곳에 둔다. */
final class Theme {
    static final Color BACKGROUND = new Color(0x1B1E24);
    static final Color PANEL = new Color(0x262A33);
    static final Color GRID = new Color(0x323744);
    static final Color TEXT = new Color(0xE8E8E8);
    static final Color DIM_TEXT = new Color(0x9AA0AA);
    static final Color ACCENT = new Color(0xFFC94D);
    static final Color HIGHLIGHT_ROW = new Color(0x3D4A63);
    static final Color WARNING = new Color(0xFF8A65);
    static final Color OVERLAY = new Color(0, 0, 0, 170);

    private static final String SELECTED_MARK = "▶ ";

    private Theme() {
    }

    /** 한글이 나오는 글자. 논리 글꼴 Dialog는 한글 Windows에서 맑은 고딕으로 연결된다 */
    static Font text(int size, boolean bold) {
        return new Font(Font.DIALOG, bold ? Font.BOLD : Font.PLAIN, size);
    }

    /** 블록 문자용 굵은 고정폭 글꼴 (RND-5) */
    static Font blockLetter(int size) {
        return new Font(Font.MONOSPACED, Font.BOLD, size);
    }

    /** 패널 높이에 비례한 글자 크기. 셀 크기가 바뀌어도 화면 비율이 유지된다 */
    static int unit(int panelHeight) {
        return Math.max(10, panelHeight / 32);
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    static void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }

    static void drawRight(Graphics2D g, String text, int rightX, int baselineY) {
        g.drawString(text, rightX - g.getFontMetrics().stringWidth(text), baselineY);
    }

    /** 세로 메뉴. 선택 항목은 강조색과 표시 기호로 그린다. 반환: 마지막 항목 아래 y */
    static int drawMenu(Graphics2D g, List<String> items, int selected, int centerX, int topY, int fontSize) {
        int lineHeight = fontSize * 2;
        int y = topY;
        for (int i = 0; i < items.size(); i++) {
            boolean isSelected = i == selected;
            g.setFont(text(fontSize, isSelected));
            g.setColor(isSelected ? ACCENT : TEXT);
            drawCentered(g, isSelected ? SELECTED_MARK + items.get(i) : items.get(i), centerX, y + fontSize);
            y += lineHeight;
        }
        return y;
    }

    /** 화면 맨 아래 Key Guide 한 줄 */
    static void drawGuide(Graphics2D g, String guide, int width, int height) {
        int size = Math.max(10, unit(height) * 3 / 4);
        g.setFont(text(size, false));
        g.setColor(DIM_TEXT);
        drawCentered(g, guide, width / 2, height - size);
    }

    /** 확인창·Pause용 반투명 배경과 가운데 상자. 반환: 상자 {x, y, w, h} */
    static int[] drawDialogBox(Graphics2D g, int width, int height, int boxWidth, int boxHeight) {
        g.setColor(OVERLAY);
        g.fillRect(0, 0, width, height);
        int x = (width - boxWidth) / 2;
        int y = (height - boxHeight) / 2;
        g.setColor(PANEL);
        g.fillRoundRect(x, y, boxWidth, boxHeight, 16, 16);
        g.setColor(ACCENT);
        g.drawRoundRect(x, y, boxWidth, boxHeight, 16, 16);
        return new int[] {x, y, boxWidth, boxHeight};
    }

    /** 순환 선택 이동 (Main Menu 규칙: 맨 위에서 ↑ → 맨 아래) */
    static int cycle(int index, int delta, int size) {
        return Math.floorMod(index + delta, size);
    }
}
