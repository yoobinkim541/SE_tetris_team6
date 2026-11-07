package tetris.versus.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import tetris.feature.setting.GameAction;
import tetris.ui.versus.*;
import tetris.ui.versus.PlayerCommand.PlayerId;

class BattleInputTest {
    private static void key(JRootPane root,int code,boolean released,int modifiers) {
        Object name=root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(code,modifiers,released));
        assertNotNull(name);
        var action=root.getActionMap().get(name); assertNotNull(action);
        if(action.isEnabled()) action.actionPerformed(new ActionEvent(root,0,"test"));
    }
    @Test void routesPlayersRepeatsAndSharedActionsOnceAndReleasesWhileInactive() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root=new JRootPane(); var commands=new ArrayList<PlayerCommand>();
            var active=new AtomicBoolean(true);
            try(var router=new BattleInputRouter(root,new BattleKeyMap(),commands::add,action -> active.get())) {
                key(root,KeyEvent.VK_LEFT,false,0); key(root,KeyEvent.VK_LEFT,false,0);
                key(root,KeyEvent.VK_A,false,0);
                assertEquals(List.of(PlayerId.ONE,PlayerId.ONE,PlayerId.TWO),commands.stream().map(PlayerCommand::playerId).toList());
                key(root,KeyEvent.VK_UP,false,0); key(root,KeyEvent.VK_UP,false,0);
                assertEquals(4,commands.size());
                active.set(false);
                key(root,KeyEvent.VK_UP,true,InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK);
                assertFalse(commands.getLast().pressed());
                active.set(true); key(root,KeyEvent.VK_UP,false,0);
                key(root,KeyEvent.VK_P,false,0); key(root,KeyEvent.VK_P,false,0);
                assertEquals(1,commands.stream().filter(c -> c.action()==GameAction.PAUSE).count());
                router.clearPressedKeys();
                assertFalse(commands.getLast().pressed());
                for(int i=1;i<commands.size();i++) assertTrue(commands.get(i).sequence()>commands.get(i-1).sequence());
            }
        });
    }
    @Test void duplicateChangesAreAtomicAndDoNotAlterSinglePlayerSettings() {
        var single=new tetris.feature.setting.KeyMap(); var original=single.codes();
        var keys=new BattleKeyMap();
        assertFalse(keys.assign(PlayerId.TWO,GameAction.MOVE_LEFT,KeyEvent.VK_LEFT));
        assertEquals(KeyEvent.VK_A,keys.keyOf(PlayerId.TWO,GameAction.MOVE_LEFT));
        assertFalse(keys.assign(PlayerId.TWO,GameAction.MOVE_LEFT,KeyEvent.VK_P));
        assertFalse(keys.assign(PlayerId.ONE,GameAction.ROTATE,KeyEvent.VK_ESCAPE));
        assertTrue(keys.assign(PlayerId.TWO,GameAction.MOVE_LEFT,KeyEvent.VK_Z));
        assertEquals(original,single.codes());
    }
    @Test void oneRouterPerRootRestoresBindingsAndRebindReleasesHeldKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root=new JRootPane(); var commands=new ArrayList<PlayerCommand>();
            var stroke=KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,0,false);
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke,"previous");
            var router=new BattleInputRouter(root,new BattleKeyMap(),commands::add,a -> true);
            assertThrows(IllegalStateException.class, () -> new BattleInputRouter(root,new BattleKeyMap(),commands::add,a -> true));
            key(root,KeyEvent.VK_LEFT,false,0);
            router.rebind(new BattleKeyMap()); assertFalse(commands.getLast().pressed());
            key(root,KeyEvent.VK_W,false,0);
            router.clearPressedKeys(); assertFalse(commands.getLast().pressed());
            router.resetForMatch("old");
            key(root,KeyEvent.VK_F,false,0);
            router.resetForMatch("new"); assertFalse(commands.getLast().pressed());
            key(root,KeyEvent.VK_F,false,0); assertTrue(commands.getLast().pressed());
            router.close(); router.close();
            assertEquals("previous",root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke));
            try(var next=new BattleInputRouter(root,new BattleKeyMap(),commands::add,a -> true)) { }
        });
    }
}
