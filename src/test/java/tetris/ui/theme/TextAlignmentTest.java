package tetris.ui.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import tetris.feature.setting.ScreenSize;
import tetris.ui.GamePanel;

/** 글자 잉크 중심이 에셋 칸 중심과 ±1px 이고, 잉크가 칸 안에 있는지 확인한다 */
class TextAlignmentTest {
    @Test
    void representativesStayInsideTheContentHole() {
        int[][] windows = {
                size(ScreenSize.SMALL.cellPixels()),
                size(ScreenSize.MEDIUM.cellPixels()),
                size(ScreenSize.LARGE.cellPixels()),
                {1920, 1080}
        };
        for (int[] window : windows) {
            int unit = Math.max(10, window[1] / 32);
            int font = unit + unit / 4;
            int buttonW = Math.min(window[0] - unit * 4, (int) (window[0] * 0.62));
            int buttonH = font + font / 2;
            check("pill", window, UiKit.AssetSlot.BUTTON, buttonW, buttonH, "아이템 모드",
                    GameTheme.display(font), UiKit.Align.CENTER, false, true);
            check("pill-selected", window, UiKit.AssetSlot.BUTTON, buttonW, buttonH, "아이템 모드",
                    GameTheme.display(font), UiKit.Align.CENTER, true, true);
            check("settings", window, UiKit.AssetSlot.BUTTON, buttonW, Math.max(40, unit * 2), "보통",
                    GameTheme.text(Math.max(14, unit), false), UiKit.Align.RIGHT, false, false);
            int capH = Math.max(32, unit + 8);
            check("capsule", window, UiKit.AssetSlot.CAPSULE, Math.max(96, unit * 6), capH, "128450",
                    GameTheme.digits(Math.max(12, capH / 2)), UiKit.Align.CENTER, false, true);
            int tabW = Math.max(96, unit * 5);
            int strip = Math.max(16, UiKit.tabTitleInset(tabW, tabW) + 16);
            check("tab", window, UiKit.AssetSlot.TAB, tabW, strip, "홀드",
                    GameTheme.text(Math.max(12, unit / 2), true), UiKit.Align.CENTER, false, true);
            check("nametab", window, UiKit.AssetSlot.NAME, 220, 56, "P1",
                    GameTheme.display(16), UiKit.Align.CENTER, false, true);
            check("banner", window, UiKit.AssetSlot.BANNER, Math.min(480, window[0] - 32), 36, "경고",
                    GameTheme.display(18), UiKit.Align.CENTER, false, true);
            check("result", window, UiKit.AssetSlot.BUTTON, Math.max(120, unit * 8), buttonH, "계속",
                    GameTheme.display(font), UiKit.Align.CENTER, false, true);
            check("confirm", window, UiKit.AssetSlot.BUTTON, unit * 5, unit + unit / 2, "예",
                    GameTheme.display(Math.max(12, unit)), UiKit.Align.CENTER, true, true);
            checkIcon(window, unit);
        }
    }

    private static void checkIcon(int[] window, int unit) {
        Rectangle box = new Rectangle(0, 0, Math.max(80, unit * 6), Math.max(24, unit));
        String where = window[0] + "x" + window[1];
        assertIconCenter(where + " diamond", box, "◆", "P1");
        assertIconCenter(where + " lock", box, "잠금", "비공개");
        assertIconCenter(where + " warn", box, "!", "경고");
    }

    private static void assertIconCenter(String name, Rectangle box, String icon, String text) {
        Font font = GameTheme.text(14, false);
        Ink iconInk = paintIcon(box, font, icon, "");
        Ink textInk = paintIcon(box, font, "", text);
        double mid = box.y + box.height / 2.0;
        assertTrue(Math.abs(iconInk.centerY() - mid) <= 1.0,
                name + " icon dy=" + (iconInk.centerY() - mid));
        assertTrue(Math.abs(textInk.centerY() - mid) <= 1.0,
                name + " text dy=" + (textInk.centerY() - mid));
    }

    private static Ink paintIcon(Rectangle box, Font font, String icon, String text) {
        BufferedImage image = new BufferedImage(box.width + 8, box.height + 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        GameTheme.applyQuality(g);
        g.setFont(font);
        g.setColor(Color.WHITE);
        UiKit.drawIconText(g, box, icon, text);
        g.dispose();
        return ink(image, 40);
    }

    private static void check(String name, int[] window, UiKit.AssetSlot slot, int width, int height, String text,
                              Font font, UiKit.Align align, boolean selected, boolean horizontal) {
        width = Math.max(24, width);
        height = Math.max(16, height);
        Rectangle rect = new Rectangle(0, 0, width, height);
        Rectangle hole = UiKit.contentBox(slot, rect);
        BufferedImage image = new BufferedImage(width + 16, height + 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        GameTheme.applyQuality(g);
        g.setFont(font);
        g.setColor(Color.WHITE);
        UiKit.drawInAsset(g, slot, rect, text, align, selected);
        g.dispose();
        Ink ink = ink(image, 40);
        String where = name + " " + window[0] + "x" + window[1] + " " + width + "x" + height;
        assertTrue(ink.count > 8, where + " has no ink");
        double dx = ink.centerX() - (hole.x + hole.width / 2.0);
        double dy = ink.centerY() - (hole.y + hole.height / 2.0);
        assertTrue(Math.abs(dy) <= 1.0, where + " dy=" + dy + " hole=" + hole + " ink=" + ink);
        if (horizontal) {
            assertTrue(Math.abs(dx) <= 1.0, where + " dx=" + dx + " hole=" + hole + " ink=" + ink);
        }
        assertTrue(ink.minX >= hole.x && ink.maxX < hole.x + hole.width
                        && ink.minY >= hole.y && ink.maxY < hole.y + hole.height,
                where + " outside hole=" + hole + " ink=" + ink);
    }

    private static int[] size(int cell) {
        var preferred = GamePanel.preferredSizeFor(cell);
        return new int[] {preferred.width, preferred.height};
    }

    private static Ink ink(BufferedImage image, int alpha) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) <= alpha) continue;
                count++;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        return new Ink(minX, minY, maxX, maxY, count);
    }

    private record Ink(int minX, int minY, int maxX, int maxY, int count) {
        double centerX() {
            return (minX + maxX) / 2.0;
        }

        double centerY() {
            return (minY + maxY) / 2.0;
        }

        @Override
        public String toString() {
            return minX + "," + minY + " " + (maxX - minX + 1) + "x" + (maxY - minY + 1);
        }
    }
}
