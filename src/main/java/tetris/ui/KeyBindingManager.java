package tetris.ui;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameState;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;

/**
 * InputMap/ActionMap Key Binding (KEY-9). 눌림/뗌을 모두 바인딩해 반복 규칙(명세 7.3)을 구현한다.
 *
 * 바인딩은 WHEN_IN_FOCUSED_WINDOW라서 화면에 보이는(isShowing) 컴포넌트의 것만 동작한다. 같은 키가 여러 곳에
 * 바인딩돼 있으면 Swing은 isEnabled()가 true인 첫 Action에 넘기므로, 겹칠 수 있는 키는 isEnabled로 한쪽만 켠다.
 */
public class KeyBindingManager {
    private static final String PRESSED = ".pressed";
    private static final String RELEASED = ".released";
    // 수정 키를 누른 채 뗀 경우에도 눌림 상태가 풀리도록 뗌은 수정 키 조합까지 등록한다 (눌림은 수정 키 없이만: KEY-7)
    private static final int[] RELEASE_MODIFIERS = {
            0, InputEvent.SHIFT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK, InputEvent.ALT_DOWN_MASK};

    private final Runnable onQuit;
    private final Set<GameAction> held = EnumSet.noneOf(GameAction.class); // 반복 불가 키 중 눌려 있는 것
    private final List<KeyStroke> registered = new ArrayList<>();
    private JComponent target;
    private GameSession session;
    private KeyMap keyMap;
    private boolean suspended;

    /** onQuit: Quit 키를 눌렀을 때 실행. 확인창을 띄워야 해서 세션 호출(requestQuit)은 화면 쪽이 맡는다 */
    public KeyBindingManager(Runnable onQuit) {
        if (onQuit == null) throw new IllegalArgumentException("onQuit cannot be null");
        this.onQuit = onQuit;
    }

    /** WHEN_IN_FOCUSED_WINDOW로 게임 키를 세션 명령에 연결 */
    public void bindGameKeys(JComponent target, KeyMap keyMap, GameSession session) {
        if (target == null || keyMap == null || session == null)
            throw new IllegalArgumentException("target, keyMap, session cannot be null");
        this.target = target;
        this.session = session;

        ActionMap actions = target.getActionMap();
        for (GameAction action : GameAction.values()) {
            actions.put(name(action) + PRESSED, new PressAction(action));
            actions.put(name(action) + RELEASED, new ReleaseAction(action));
        }
        rebind(keyMap);
    }

    /** 조작키 변경 후 바인딩 다시 적용 */
    public void rebind(KeyMap keyMap) {
        if (target == null) throw new IllegalStateException("bindGameKeys must be called first");
        this.keyMap = keyMap;

        InputMap inputs = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        for (KeyStroke stroke : registered) inputs.remove(stroke);
        registered.clear();

        for (GameAction action : GameAction.values()) {
            int code = keyMap.keyOf(action);
            register(inputs, KeyStroke.getKeyStroke(code, 0, false), name(action) + PRESSED);
            for (int modifiers : RELEASE_MODIFIERS) {
                register(inputs, KeyStroke.getKeyStroke(code, modifiers, true), name(action) + RELEASED);
            }
        }
        held.clear();
    }

    /** 창 포커스 이탈 시 눌림 상태 초기화 (KEY-8) */
    public void clearPressedKeys() {
        held.clear();
    }

    /** 확인창이 떠 있는 동안 게임 키를 모두 끈다. 확인창이 자기 키(↑↓←→ Enter Esc)를 받는다 */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    /** 메뉴·확인창 공용: 수정 키 없는 눌림 하나를 command에 연결 */
    static void bindKey(JComponent target, int keyCode, Runnable command) {
        bindKey(target, keyCode, () -> true, command);
    }

    /** active가 false면 이벤트를 소비하지 않아 같은 키의 다른 바인딩이 받을 수 있다 */
    static void bindKey(JComponent target, int keyCode, BooleanSupplier active, Runnable command) {
        String name = "menu." + keyCode;
        target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        target.getActionMap().put(name, new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return active.getAsBoolean();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                command.run();
            }
        });
    }

    private void register(InputMap inputs, KeyStroke stroke, String actionName) {
        inputs.put(stroke, actionName);
        registered.add(stroke);
    }

    private static String name(GameAction action) {
        return "game." + action.name();
    }

    /**
     * 이 기능의 키를 지금 게임이 받아야 하는지.
     * PLAYING이면 모두 받는다. PAUSED에서는 Pause·Quit만 받되, 방향키에 지정돼 있으면 Pause 메뉴 이동이 우선한다 (KEY-5)
     */
    private boolean accepts(GameAction action) {
        if (suspended) return false;
        GameState state = session.getState();
        if (state == GameState.PLAYING) return true;
        return state == GameState.PAUSED
                && (action == GameAction.PAUSE || action == GameAction.QUIT)
                && !isMenuKey(keyMap.keyOf(action));
    }

    private static boolean isMenuKey(int keyCode) {
        return keyCode >= KeyEvent.VK_LEFT && keyCode <= KeyEvent.VK_DOWN;
    }

    private void perform(GameAction action) {
        switch (action) {
            case MOVE_LEFT -> session.moveLeft();
            case MOVE_RIGHT -> session.moveRight();
            case SOFT_DROP -> session.moveDown();
            case ROTATE -> session.rotateRight();
            case HARD_DROP -> session.hardDrop();
            case PAUSE -> session.togglePause();
            case QUIT -> onQuit.run();
        }
    }

    private final class PressAction extends AbstractAction {
        private final GameAction action;

        PressAction(GameAction action) {
            this.action = action;
        }

        @Override
        public boolean isEnabled() {
            return accepts(action);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // Rotate·Hard Drop·Pause·Quit은 최초 눌림만. OS 반복 이벤트는 뗄 때까지 무시한다 (명세 7.3)
            if (!action.isRepeatable() && !held.add(action)) return;
            perform(action);
        }
    }

    private final class ReleaseAction extends AbstractAction {
        private final GameAction action;

        ReleaseAction(GameAction action) {
            this.action = action;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            held.remove(action);
        }
    }
}
