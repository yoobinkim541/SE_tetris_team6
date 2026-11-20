package tetris.raid.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import tetris.ui.raid.RaidPanel;
import tetris.ui.theme.GameTheme;

/** 레이드 문구가 고른 글꼴에서 네모로 깨지지 않는지 확인한다 */
class RaidFontCoverageTest {
    @Test
    void everyRaidCaptionIsDisplayableByTheChosenFont() {
        for (String text : RaidPanel.captions()) {
            assertFalse(text.indexOf('\u00B7') >= 0, text);
            assertEquals(-1, GameTheme.fontFor(text, 16, false).canDisplayUpTo(text), text);
        }
    }
}
