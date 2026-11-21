package tetris.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import org.junit.jupiter.api.Test;
import tetris.ui.Messages;

/** 안내 문구가 고른 글꼴에서 네모로 깨지지 않는지 확인한다 */
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
}
