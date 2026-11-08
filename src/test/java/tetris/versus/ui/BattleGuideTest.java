package tetris.versus.ui;

import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.PlayerId;
import tetris.ui.versus.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleGuideTest {
    @Test void guideNamesPlayersActionsAndAssignedKeys() throws Exception {
        var keys = new BattleKeyMap();
        assertTrue(keys.assign(PlayerId.TWO, GameAction.MOVE_LEFT, KeyEvent.VK_Z));
        assertTrue(keys.assign(PlayerId.ONE, GameAction.PAUSE, KeyEvent.VK_X));
        String guide = keys.guide();
        assertTrue(guide.contains("P1")); assertTrue(guide.contains("P2"));
        assertTrue(guide.contains("왼쪽 Z")); assertTrue(guide.contains("일시정지 X"));
        SwingUtilities.invokeAndWait(() -> {
            var panel = new BattlePanel(false, guide);
            assertEquals(guide, panel.getAccessibleContext().getAccessibleDescription());
            assertEquals(new BattleKeyMap().guide(), new BattlePanel(false).getAccessibleContext().getAccessibleDescription());
        });
    }
}
