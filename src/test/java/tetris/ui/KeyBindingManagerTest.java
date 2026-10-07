package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tetris.component.block.TBlock;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;

/** 명세 7.3 반복 규칙, KEY-5(메뉴 키 우선), KEY-8(눌림 초기화), 조작키 변경 후 rebind */
public class KeyBindingManagerTest {
    private final JPanel target = new JPanel();
    private final KeyMap keyMap = new KeyMap();
    private int quitRequests;
    private KeyBindingManager manager;
    private GameSession session;

    @BeforeEach
    void setUp() {
        session = new GameSession(TBlock::new, new GameClock() {
            @Override public void start(int intervalMillis) { }
            @Override public void stop() { }
        }, new GameListener() {
            @Override public void onChanged(GameSnapshot snapshot) { }
            @Override public void onGameOver(GameSnapshot snapshot) { }
        });
        session.start();
        manager = new KeyBindingManager(() -> quitRequests++);
        manager.bindGameKeys(target, keyMap, session);
    }

    private Action action(int keyCode, boolean released) {
        Object name = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(keyCode, 0, released));
        return name == null ? null : target.getActionMap().get(name);
    }

    /** Swing처럼 isEnabled일 때만 실행. 반환: 이벤트를 소비했는지 */
    private boolean press(int keyCode) {
        Action action = action(keyCode, false);
        if (action == null || !action.isEnabled()) return false;
        action.actionPerformed(null);
        return true;
    }

    private void release(int keyCode) {
        action(keyCode, true).actionPerformed(null);
    }

    private int rotation() {
        return session.snapshot().currentBlock().getRotation();
    }

    private int column() {
        return session.snapshot().currentBlock().getCol();
    }

    @Test
    void rotateIsHandledOnlyOnceWhileHeld() {
        press(KeyEvent.VK_UP);
        press(KeyEvent.VK_UP); // OS 반복 이벤트
        assertEquals(1, rotation());

        release(KeyEvent.VK_UP);
        press(KeyEvent.VK_UP);
        assertEquals(2, rotation());
    }

    @Test
    void moveRepeatsWhileHeld() {
        int start = column();
        press(KeyEvent.VK_LEFT);
        press(KeyEvent.VK_LEFT);
        assertEquals(start - 2, column());
    }

    @Test
    void clearPressedKeysAllowsPressingAgain() {
        press(KeyEvent.VK_UP);
        manager.clearPressedKeys(); // 창 포커스 이탈 (KEY-8)
        press(KeyEvent.VK_UP);
        assertEquals(2, rotation());
    }

    @Test
    void pausedGameIgnoresPlayKeysButAcceptsPauseAndQuit() {
        press(KeyEvent.VK_P);
        assertEquals(GameState.PAUSED, session.getState());

        assertFalse(press(KeyEvent.VK_UP), "방향키는 Pause 메뉴가 받아야 한다");
        assertFalse(press(KeyEvent.VK_SPACE));
        assertTrue(press(KeyEvent.VK_Q));
        assertEquals(1, quitRequests);

        release(KeyEvent.VK_P);
        press(KeyEvent.VK_P);
        assertEquals(GameState.PLAYING, session.getState());
    }

    @Test
    void pauseKeyOnArrowGivesWayToMenuWhilePaused() {
        keyMap.assign(GameAction.ROTATE, KeyEvent.VK_R);
        keyMap.assign(GameAction.PAUSE, KeyEvent.VK_UP);
        manager.rebind(keyMap);

        press(KeyEvent.VK_UP);
        assertEquals(GameState.PAUSED, session.getState());
        release(KeyEvent.VK_UP);
        assertFalse(press(KeyEvent.VK_UP), "Pause 중 ↑는 메뉴 이동 (KEY-5)");
    }

    @Test
    void suspendedManagerLetsDialogHandleKeys() {
        manager.setSuspended(true);
        assertFalse(press(KeyEvent.VK_LEFT));
        assertFalse(press(KeyEvent.VK_Q));
        assertEquals(0, quitRequests);
    }

    @Test
    void gameOverIgnoresAllKeys() {
        session.end();
        assertFalse(press(KeyEvent.VK_SPACE));
        assertFalse(press(KeyEvent.VK_P));
    }

    @Test
    void rebindMovesActionToNewKey() {
        keyMap.assign(GameAction.HARD_DROP, KeyEvent.VK_X);
        manager.rebind(keyMap);

        assertNull(action(KeyEvent.VK_SPACE, false));
        long before = session.snapshot().score();
        press(KeyEvent.VK_X);
        assertTrue(session.snapshot().score() > before);
    }

    @Test
    void modifierKeysAreNotBoundForPress() {
        Object name = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK, false));
        assertNull(name); // KEY-7
    }
}
