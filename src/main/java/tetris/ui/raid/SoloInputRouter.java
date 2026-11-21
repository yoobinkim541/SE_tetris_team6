package tetris.ui.raid;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;

/**
 * 솔로 레이드 입력. 설정의 {@link KeyMap}을 P1 명령으로 바꾼다.
 * 누르고 떼는 규칙은 대전 {@code BattleInputRouter}와 같다. 모던 DAS는 엔진이 press를 잡고 반복한다.
 */
public final class SoloInputRouter implements AutoCloseable {
    private static final Object OWNER = new Object();
    private static final int[] RELEASE_MODIFIERS = releaseModifiers();

    private final JRootPane root;
    private final Consumer<PlayerCommand> sink;
    private final Predicate<GameAction> accepts;
    private final Set<GameAction> held = EnumSet.noneOf(GameAction.class);
    private final Map<KeyStroke, Object> installed = new LinkedHashMap<>();
    private long sequence;
    private boolean closed;

    public SoloInputRouter(JRootPane root, KeyMap keys, Consumer<PlayerCommand> sink, Predicate<GameAction> accepts) {
        this.root = Objects.requireNonNull(root, "root");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.accepts = Objects.requireNonNull(accepts, "accepts");
        if (root.getClientProperty(OWNER) != null) throw new IllegalStateException("root already has a solo router");
        root.putClientProperty(OWNER, this);
        rebind(keys);
    }

    public void rebind(KeyMap keys) {
        if (closed) throw new IllegalStateException("router closed");
        Objects.requireNonNull(keys, "keys");
        clearPressedKeys();
        uninstall();
        for (GameAction action : GameAction.values()) {
            int code = keys.keyOf(action);
            install(KeyStroke.getKeyStroke(code, 0, false), () -> press(action));
            for (int modifiers : RELEASE_MODIFIERS) {
                install(KeyStroke.getKeyStroke(code, modifiers, true), () -> release(action));
            }
        }
    }

    public void clearPressedKeys() {
        var previous = List.copyOf(held);
        held.clear();
        for (GameAction action : previous) send(action, false);
    }

    @Override
    public void close() {
        if (closed) return;
        clearPressedKeys();
        closed = true;
        uninstall();
        root.putClientProperty(OWNER, null);
    }

    private void press(GameAction action) {
        if (!accepts.test(action)) return;
        boolean first = held.add(action);
        if (first || action.isRepeatable()) send(action, true);
    }

    private void release(GameAction action) {
        if (held.remove(action)) send(action, false);
    }

    private void send(GameAction action, boolean pressed) {
        sink.accept(new PlayerCommand(PlayerId.ONE, action, pressed, sequence++));
    }

    private void install(KeyStroke stroke, Runnable command) {
        InputMap map = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        Object name = new Object();
        installed.put(stroke, name);
        map.put(stroke, name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!closed) command.run();
            }
        });
    }

    private void uninstall() {
        InputMap map = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        for (var entry : installed.entrySet()) {
            if (map.get(entry.getKey()) == entry.getValue()) map.remove(entry.getKey());
            root.getActionMap().remove(entry.getValue());
        }
        installed.clear();
    }

    private static int[] releaseModifiers() {
        int[] flags = {InputEvent.SHIFT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK, InputEvent.ALT_DOWN_MASK,
                InputEvent.META_DOWN_MASK, InputEvent.ALT_GRAPH_DOWN_MASK};
        int[] combinations = new int[1 << flags.length];
        for (int combination = 0; combination < combinations.length; combination++) {
            for (int bit = 0; bit < flags.length; bit++) {
                if ((combination & (1 << bit)) != 0) combinations[combination] |= flags[bit];
            }
        }
        return combinations;
    }
}
