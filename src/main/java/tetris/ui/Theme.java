package tetris.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import tetris.ui.theme.GameTheme;
import tetris.ui.theme.UiKit;

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

    private Theme() {
    }

    /** 한글이 나오는 글자. Do Hyeon, 실패하면 Dialog */
    static Font text(int size, boolean bold) {
        return GameTheme.text(size, bold);
    }

    /** 문자열 전체를 그릴 수 있는 글꼴. 숫자만 Orbitron */
    static Font font(String value, int size, boolean preferDigits) {
        return GameTheme.fontFor(value, size, preferDigits);
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
        GameTheme.drawCentered(g, text, centerX, baselineY);
    }

    static void drawRight(Graphics2D g, String text, int rightX, int baselineY) {
        GameTheme.drawRight(g, text, rightX, baselineY);
    }

    /** 세로 메뉴. 선택 항목은 강조색과 화살로 그린다. 반환: 마지막 항목 아래 y */
    static int drawMenu(Graphics2D g, List<String> items, int selected, int centerX, int topY, int fontSize) {
        return drawMenu(g, items, selected, centerX, topY, fontSize, false);
    }

    static int drawMenu(Graphics2D g, List<String> items, int selected, int centerX, int topY, int fontSize,
                        boolean colorBlind) {
        int widest = 0;
        g.setFont(text(fontSize, true));
        for (String item : items) widest = Math.max(widest, g.getFontMetrics().stringWidth(item));
        return drawPills(g, items, selected, centerX, topY, fontSize, widest + fontSize * 3, colorBlind);
    }

    /** 가로 알약 메뉴. 선택된 항목은 빛과 화살. 반환: 마지막 항목 아래 y */
    static int drawPills(Graphics2D g, List<String> items, int selected, int centerX, int topY, int fontSize,
                         int buttonWidth, boolean colorBlind) {
        int buttonHeight = fontSize + fontSize / 2;
        int gap = Math.max(6, fontSize / 3);
        int y = topY;
        float pulse = UiKit.pulse();
        for (int i = 0; i < items.size(); i++) {
            boolean isSelected = i == selected;
            int x = centerX - buttonWidth / 2;
            UiKit.paintButton(g, isSelected, x, y, buttonWidth, buttonHeight, colorBlind, pulse);
            if (isSelected) {
                UiKit.paintArrow(g, x - fontSize, y + buttonHeight / 2, Math.max(8, fontSize / 2), colorBlind, pulse);
            }
            g.setFont(font(items.get(i), fontSize, false));
            g.setColor(isSelected ? Color.WHITE : TEXT);
            drawCentered(g, items.get(i), centerX, y + buttonHeight / 2 + g.getFontMetrics().getAscent() / 2 - 1);
            y += buttonHeight + gap;
        }
        return y;
    }

    /** 화면 맨 아래 Key Guide 한 줄. 한글이 있으면 Do Hyeon, 숫자만 Orbitron */
    static void drawGuide(Graphics2D g, String guide, int width, int height) {
        int size = Math.max(10, unit(height) * 3 / 4);
        g.setFont(font(guide, size, false));
        int barWidth = Math.min(width - size * 2, Math.max(size * 8, width * 3 / 4));
        while (g.getFontMetrics().stringWidth(guide) > barWidth - size && size > 8) {
            g.setFont(font(guide, --size, false));
        }
        int barHeight = size + size / 2;
        int x = (width - barWidth) / 2;
        int y = height - barHeight - size / 3;
        UiKit.paintCapsule(g, x, y, barWidth, barHeight);
        g.setColor(TEXT);
        drawCentered(g, guide, width / 2, y + barHeight / 2 + g.getFontMetrics().getAscent() / 2 - 1);
    }

    /** 확인창·Pause용 반투명 배경과 가운데 상자. 반환: 상자 {x, y, w, h} */
    static int[] drawDialogBox(Graphics2D g, int width, int height, int boxWidth, int boxHeight) {
        g.setColor(OVERLAY);
        g.fillRect(0, 0, width, height);
        int x = (width - boxWidth) / 2;
        int y = (height - boxHeight) / 2;
        g.setColor(new Color(10, 14, 28, 210));
        g.fillRoundRect(x, y, boxWidth, boxHeight, 18, 18);
        UiKit.paintFrame(g, UiKit.Tone.VIOLET, x, y, boxWidth, boxHeight);
        return new int[] {x, y, boxWidth, boxHeight};
    }

    /** 순환 선택 이동 (Main Menu 규칙: 맨 위에서 ↑ → 맨 아래) */
    static int cycle(int index, int delta, int size) {
        return Math.floorMod(index + delta, size);
    }
}
