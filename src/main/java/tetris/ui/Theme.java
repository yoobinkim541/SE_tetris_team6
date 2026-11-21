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
    static final Color OVERLAY = new Color(0, 0, 0, 230);

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

    /** 블록 문자. 싱글·대전 보드가 같은 라벨 글꼴로 그린다 */
    static Font blockLetter(int size) {
        return GameTheme.text(size, false);
    }

    /** 패널 높이에 비례한 글자 크기. 셀 크기가 바뀌어도 화면 비율이 유지된다 */
    static int unit(int panelHeight) {
        return Math.max(10, panelHeight / 32);
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        GameTheme.applyQuality(g2);
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
        int gap = Math.max(8, (fontSize / 3 + 4) / 8 * 8);
        int y = topY;
        float pulse = UiKit.pulse();
        for (int i = 0; i < items.size(); i++) {
            boolean isSelected = i == selected;
            float grow = isSelected ? 1.04f + 0.02f * pulse : 1f;
            int dw = Math.round(buttonWidth * grow);
            int dh = Math.round(buttonHeight * grow);
            int x = centerX - dw / 2;
            int dy = y + (buttonHeight - dh) / 2;
            UiKit.paintButton(g, isSelected, x, dy, dw, dh, colorBlind, pulse);
            if (isSelected) {
                UiKit.paintArrow(g, x - fontSize, dy + dh / 2, Math.max(8, fontSize / 2), colorBlind, pulse);
            }
            int size = fontSize;
            int limit = Math.max(8, buttonWidth - fontSize);
            g.setFont(GameTheme.display(size));
            while (size > 11 && GameTheme.width(g, items.get(i)) > limit) {
                g.setFont(GameTheme.display(--size));
            }
            UiKit.drawInAsset(g, UiKit.AssetSlot.BUTTON, new java.awt.Rectangle(x, dy, dw, dh),
                    items.get(i), UiKit.Align.CENTER, isSelected);
            y += buttonHeight + gap;
        }
        return y;
    }

    /** 화면 맨 아래 Key Guide. 칸에 안 들어가면 줄을 나누고, 12px 아래로 내리지 않는다 */
    static void drawGuide(Graphics2D g, String guide, int width, int height) {
        int size = Math.max(12, unit(height) * 3 / 4);
        g.setFont(font(guide, size, false));
        int barWidth = Math.min(width - size * 2, Math.max(size * 8, width * 3 / 4));
        String[] lines = guideLines(g, guide == null ? "" : guide, barWidth);
        int barHeight = Math.max(28, size + size / 2 + (lines.length - 1) * (size + 4));
        int x = (width - barWidth) / 2;
        int y = Math.max(0, height - barHeight - Math.max(4, size / 4));
        UiKit.paintCapsule(g, x, y, barWidth, barHeight);
        g.setColor(TEXT);
        java.awt.Rectangle hole = UiKit.contentBox(UiKit.AssetSlot.CAPSULE, new java.awt.Rectangle(x, y, barWidth, barHeight));
        int lineH = Math.max(1, hole.height / lines.length);
        for (int i = 0; i < lines.length; i++) {
            UiKit.drawInBox(g, lines[i], new java.awt.Rectangle(hole.x, hole.y + i * lineH, hole.width, lineH),
                    UiKit.Align.CENTER);
        }
    }

    private static String[] guideLines(Graphics2D g, String guide, int barWidth) {
        java.awt.Rectangle probe = UiKit.contentBox(UiKit.AssetSlot.CAPSULE, new java.awt.Rectangle(0, 0, barWidth, 32));
        if (GameTheme.drawnBounds(g.getFont(), guide, g.getFontRenderContext()).getWidth() <= probe.width) {
            return new String[] {guide};
        }
        int split = guide.indexOf("   ");
        if (split < 0) split = guide.indexOf(" · ");
        if (split <= 0) return new String[] {guide};
        return new String[] {guide.substring(0, split).trim(), guide.substring(split).trim()};
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
