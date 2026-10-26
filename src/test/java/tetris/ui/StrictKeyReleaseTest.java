package tetris.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.event.InputEvent;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tetris.component.block.TBlock;
import tetris.feature.game.*;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;

/** KEY-7/8: 수정 키를 여러 개 누른 채 키를 떼어도 다음 일반 입력은 정상 처리한다. */
class StrictKeyReleaseTest {
    private static final int[] MODIFIERS = {InputEvent.SHIFT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK,
            InputEvent.ALT_DOWN_MASK, InputEvent.META_DOWN_MASK, InputEvent.ALT_GRAPH_DOWN_MASK};

    private static int mask(int combination) {
        int mask = 0;
        for (int bit = 0; bit < MODIFIERS.length; bit++)
            if ((combination & (1 << bit)) != 0) mask |= MODIFIERS[bit];
        return mask;
    }

    static Stream<Arguments> releases() {
        return Arrays.stream(GameAction.values()).filter(action -> !action.isRepeatable())
                .flatMap(action -> IntStream.range(0, 1 << MODIFIERS.length).mapToObj(value -> Arguments.of(action, mask(value))));
    }

    static Stream<Arguments> modifiedPresses() {
        return Arrays.stream(GameAction.values())
                .flatMap(action -> IntStream.range(1, 1 << MODIFIERS.length).mapToObj(value -> Arguments.of(action, mask(value))));
    }

    private static Action action(JPanel target, int code, int modifiers, boolean release) {
        Object name = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(code, modifiers, release));
        return name == null ? null : target.getActionMap().get(name);
    }

    private static void dispatch(Action action) {
        if (action != null && action.isEnabled()) action.actionPerformed(null);
    }

    @ParameterizedTest(name = "release {0}, modifiers={1}")
    @MethodSource("releases")
    void physicalReleaseAllowsTheNextPlainPress(GameAction gameAction, int modifiers) {
        GameSession session = new GameSession(TBlock::new, new GameClock() {
            @Override public void start(int intervalMillis) { }
            @Override public void stop() { }
        }, new GameListener() {
            @Override public void onChanged(GameSnapshot snapshot) { }
            @Override public void onGameOver(GameSnapshot snapshot) { }
        });
        session.start();
        int[] quits = {0};
        JPanel target = new JPanel();
        KeyMap keyMap = new KeyMap();
        new KeyBindingManager(() -> quits[0]++).bindGameKeys(target, keyMap, session);
        int code = keyMap.keyOf(gameAction);
        Action press = action(target, code, 0, false);
        dispatch(press);
        long scoreAfterFirst = session.snapshot().score();
        dispatch(press); // OS 반복 입력은 여전히 무시해야 한다
        assertEquals(scoreAfterFirst, session.snapshot().score());
        dispatch(action(target, code, modifiers, true));
        dispatch(press);
        switch (gameAction) {
            case ROTATE -> assertEquals(2, session.snapshot().currentBlock().getRotation());
            case HARD_DROP -> assertTrue(session.snapshot().score() > scoreAfterFirst);
            case PAUSE -> assertEquals(GameState.PLAYING, session.getState());
            case QUIT -> assertEquals(2, quits[0]);
            default -> fail("non-repeatable action expected");
        }
    }

    @ParameterizedTest(name = "ignore {0}, modifiers={1}")
    @MethodSource("modifiedPresses")
    void modifiedPressesRemainUnbound(GameAction gameAction, int modifiers) {
        JPanel target = new JPanel();
        KeyMap keyMap = new KeyMap();
        GameSession session = new GameSession(TBlock::new, new GameClock() {
            @Override public void start(int intervalMillis) { }
            @Override public void stop() { }
        }, new GameListener() {
            @Override public void onChanged(GameSnapshot snapshot) { }
            @Override public void onGameOver(GameSnapshot snapshot) { }
        });
        new KeyBindingManager(() -> fail("unexpected quit")).bindGameKeys(target, keyMap, session);
        assertNull(action(target, keyMap.keyOf(gameAction), modifiers, false));
    }
}
