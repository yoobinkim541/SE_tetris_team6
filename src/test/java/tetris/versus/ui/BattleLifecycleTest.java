package tetris.versus.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.event.*;
import java.util.ArrayList;
import javax.swing.*;
import tetris.ui.versus.*;
import tetris.feature.versus.api.PlayerCommand;

/** Native window lifecycle wiring; opt in with the companion Gradle init script. */
@EnabledIfSystemProperty(named="audit.gui", matches="true")
class BattleLifecycleTest {
    @Test void installedFocusCallbackAndWindowHideReleaseKeysAndCloseRemovesListeners() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var frame=new JFrame("Sprint 7 B lifecycle acceptance");
            frame.setSize(480,400);
            var root=frame.getRootPane(); var commands=new ArrayList<PlayerCommand>();
            int focusBefore=frame.getWindowFocusListeners().length;
            int hierarchyBefore=root.getHierarchyListeners().length;
            try(var router=new BattleInputRouter(root,new BattleKeyMap(),commands::add,a -> true)) {
                assertEquals(focusBefore+1,frame.getWindowFocusListeners().length);
                frame.setVisible(true);
                press(root);
                for(var listener:frame.getWindowFocusListeners())
                    listener.windowLostFocus(new WindowEvent(frame,WindowEvent.WINDOW_LOST_FOCUS));
                assertEquals(2,commands.size()); assertFalse(commands.getLast().pressed());
                press(root); frame.setVisible(false);
                assertEquals(4,commands.size()); assertFalse(commands.getLast().pressed());
                frame.setVisible(true); press(root);
                router.close();
                assertFalse(commands.getLast().pressed());
                assertEquals(focusBefore,frame.getWindowFocusListeners().length);
                assertEquals(hierarchyBefore,root.getHierarchyListeners().length);
            } finally { frame.dispose(); }
        });
    }
    private static void press(JRootPane root) {
        Object name=root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_UP,0,false));
        root.getActionMap().get(name).actionPerformed(new ActionEvent(root,0,"test"));
    }
}
