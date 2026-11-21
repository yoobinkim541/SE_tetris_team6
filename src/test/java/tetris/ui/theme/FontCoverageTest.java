package tetris.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import tetris.ui.Messages;
import tetris.ui.raid.RaidPanel;

/** 안내 문구가 고른 글꼴에서 네모로 깨지거나 빈칸으로 사라지지 않는지 확인한다 */
class FontCoverageTest {
    @Test
    void everyMessageIsDisplayableByTheChosenFont() {
        for (String text : Messages.allTexts()) {
            assertEquals(-1, GameTheme.fontFor(text, 16, false).canDisplayUpTo(text), text);
        }
    }

    @Test
    void missingGlyphUsesARunFontThatCanDisplayIt() {
        String text = "가\u00B7나";
        assertTrue(GameTheme.text(16, false).canDisplayUpTo(text) >= 0, "Do Hyeon lacks the middle dot");
        Font dot = GameTheme.runFont(text, text.indexOf('\u00B7'), 16, false);
        assertEquals(-1, dot.canDisplayUpTo("\u00B7"));
        Font hangul = GameTheme.runFont(text, 0, 16, false);
        assertTrue(hangul.canDisplay('가'));
    }

    @Test
    void everyVisibleCharacterPaintsPixels() {
        java.util.List<String> texts = new java.util.ArrayList<>(Messages.allTexts());
        texts.addAll(RaidPanel.captions());
        texts.add("←→↑↓◀▶/");
        for (String text : texts) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ' || c == '\n' || c == '\t' || c == '%') continue;
                assertTrue(ink(String.valueOf(c), GameTheme.display(22)) > 0, "display:" + text + " @" + c);
                assertTrue(ink(String.valueOf(c), GameTheme.text(18, false)) > 0, "text:" + text + " @" + c);
            }
        }
    }

    private static int ink(String text, Font font) {
        BufferedImage image = new BufferedImage(80, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        GameTheme.applyQuality(g);
        g.setFont(font);
        g.setColor(Color.WHITE);
        GameTheme.draw(g, text, 4, 34);
        g.dispose();
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 20) count++;
            }
        }
        return count;
    }
}
