package tetris.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
