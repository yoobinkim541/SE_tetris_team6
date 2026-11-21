package tetris.raid.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import org.junit.jupiter.api.Test;
import tetris.ui.raid.RaidPanel;
import tetris.ui.theme.GameTheme;

/** 레이드 문구가 고른 글꼴에서 네모로 깨지지 않는지 확인한다 */
class RaidFontCoverageTest {
    @Test
    void everyRaidCaptionRunCanBeDisplayed() {
        for (String text : RaidPanel.captions()) {
            for (int i = 0; i < text.length(); i++) {
                Font run = GameTheme.runFont(text, i, 16, false);
                assertTrue(run.canDisplay(text.charAt(i)), text + " @" + i);
            }
        }
    }
}
