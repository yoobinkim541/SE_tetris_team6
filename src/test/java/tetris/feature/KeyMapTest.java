package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;
import tetris.feature.setting.KeyMap.AssignResult;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;

/** KEY-1~4, SET-6 (중복·예약키 거부 후 상태 불변, 같은 키 재지정, 기본값 복원) */
public class KeyMapTest {
    //#region KeyMap
    @Test
    void 기본_키는_방향키_Space_P_Q() { // SET-6
        KeyMap keys = new KeyMap();
        assertEquals(KeyEvent.VK_LEFT, keys.keyOf(GameAction.MOVE_LEFT));
        assertEquals(KeyEvent.VK_RIGHT, keys.keyOf(GameAction.MOVE_RIGHT));
        assertEquals(KeyEvent.VK_DOWN, keys.keyOf(GameAction.SOFT_DROP));
        assertEquals(KeyEvent.VK_UP, keys.keyOf(GameAction.ROTATE));
        assertEquals(KeyEvent.VK_SPACE, keys.keyOf(GameAction.HARD_DROP));
        assertEquals(KeyEvent.VK_P, keys.keyOf(GameAction.PAUSE));
        assertEquals(KeyEvent.VK_Q, keys.keyOf(GameAction.QUIT));
    }

    @Test
    void 키로_기능을_찾고_없는_키는_null() {
        KeyMap keys = new KeyMap();
        assertEquals(GameAction.ROTATE, keys.actionOf(KeyEvent.VK_UP));
        assertNull(keys.actionOf(KeyEvent.VK_Z));
    }

    @Test
    void 빈_키로_바꾸면_적용된다() {
        KeyMap keys = new KeyMap();
        assertEquals(AssignResult.OK, keys.assign(GameAction.ROTATE, KeyEvent.VK_X));
        assertEquals(KeyEvent.VK_X, keys.keyOf(GameAction.ROTATE));
        assertNull(keys.actionOf(KeyEvent.VK_UP));
    }

    @Test
    void 다른_기능이_쓰는_키는_거부하고_바꾸지_않는다() { // KEY-3
        KeyMap keys = new KeyMap();
        assertEquals(AssignResult.DUPLICATE, keys.assign(GameAction.ROTATE, KeyEvent.VK_P));
        assertEquals(KeyEvent.VK_UP, keys.keyOf(GameAction.ROTATE));
        assertEquals(KeyEvent.VK_P, keys.keyOf(GameAction.PAUSE)); // 자동 교환 없음
        assertEquals(GameAction.PAUSE, keys.actionOf(KeyEvent.VK_P));
    }

    @Test
    void 예약키는_거부하고_바꾸지_않는다() { // KEY-2
        KeyMap keys = new KeyMap();
        for (int reserved : new int[] {KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL,
                KeyEvent.VK_ALT, KeyEvent.VK_F1, KeyEvent.VK_TAB}) {
            assertEquals(AssignResult.RESERVED_KEY, keys.assign(GameAction.QUIT, reserved));
        }
        assertEquals(KeyEvent.VK_Q, keys.keyOf(GameAction.QUIT));
    }

    @Test
    void 같은_기능에_같은_키는_변경_없음() { // KEY-4
        KeyMap keys = new KeyMap();
        assertEquals(AssignResult.UNCHANGED, keys.assign(GameAction.PAUSE, KeyEvent.VK_P));
    }

    @Test
    void 지정_가능한_키는_영문_숫자_방향키_Space() { // KEY-1
        assertTrue(KeyMap.isAssignable(KeyEvent.VK_A));
        assertTrue(KeyMap.isAssignable(KeyEvent.VK_Z));
        assertTrue(KeyMap.isAssignable(KeyEvent.VK_0));
        assertTrue(KeyMap.isAssignable(KeyEvent.VK_9));
        assertTrue(KeyMap.isAssignable(KeyEvent.VK_LEFT));
        assertTrue(KeyMap.isAssignable(KeyEvent.VK_DOWN));
        assertTrue(KeyMap.isAssignable(KeyEvent.VK_SPACE));
        assertFalse(KeyMap.isAssignable(KeyEvent.VK_ENTER));
        assertFalse(KeyMap.isAssignable(KeyEvent.VK_F12));
        assertFalse(KeyMap.isAssignable(KeyEvent.VK_COMMA));
    }

    @Test
    void 기본값으로_되돌린다() {
        KeyMap keys = new KeyMap();
        keys.assign(GameAction.ROTATE, KeyEvent.VK_X);
        keys.resetToDefaults();
        assertEquals(KeyEvent.VK_UP, keys.keyOf(GameAction.ROTATE));
    }

    @Test
    void 키_전체_교체는_모두_유효할_때만_적용된다() { // 명세 12.2 keys 행
        KeyMap keys = new KeyMap();
        Map<GameAction, Integer> valid = new EnumMap<>(new KeyMap().codes());
        valid.put(GameAction.ROTATE, KeyEvent.VK_X);
        assertTrue(keys.tryReplaceAll(valid));
        assertEquals(KeyEvent.VK_X, keys.keyOf(GameAction.ROTATE));

        Map<GameAction, Integer> duplicate = new EnumMap<>(valid);
        duplicate.put(GameAction.PAUSE, KeyEvent.VK_X);
        Map<GameAction, Integer> missing = new EnumMap<>(valid);
        missing.remove(GameAction.QUIT);
        Map<GameAction, Integer> reserved = new EnumMap<>(valid);
        reserved.put(GameAction.QUIT, KeyEvent.VK_ENTER);
        for (Map<GameAction, Integer> invalid : java.util.List.of(duplicate, missing, reserved)) {
            assertFalse(keys.tryReplaceAll(invalid));
        }
        assertEquals(valid, keys.codes());
    }

    @Test
    void 키_이름은_실행_환경의_KeyEvent_이름을_쓴다() { // KEY-6
        KeyMap keys = new KeyMap();
        assertEquals(KeyEvent.getKeyText(KeyEvent.VK_SPACE), keys.displayName(GameAction.HARD_DROP));
    }

    @Test
    void 좌우와_Soft_Drop만_반복_입력() { // 명세 7.3
        for (GameAction action : GameAction.values()) {
            boolean expected = action == GameAction.MOVE_LEFT || action == GameAction.MOVE_RIGHT || action == GameAction.SOFT_DROP;
            assertEquals(expected, action.isRepeatable());
        }
    }
    //#endregion

    //#region Settings
    @Test
    void 설정_기본값은_Medium_색맹_OFF_기본_키() { // SET-6
        Settings settings = new Settings();
        assertEquals(ScreenSize.MEDIUM, settings.getScreenSize());
        assertFalse(settings.isColorBlindMode());
        assertEquals(new KeyMap().codes(), settings.getKeyMap().codes());
    }

    @Test
    void Reset_Settings는_모든_항목을_기본값으로() { // SET-2
        Settings settings = new Settings();
        settings.setScreenSize(ScreenSize.LARGE);
        settings.setColorBlindMode(true);
        settings.getKeyMap().assign(GameAction.ROTATE, KeyEvent.VK_X);

        settings.resetToDefaults();

        assertEquals(ScreenSize.MEDIUM, settings.getScreenSize());
        assertFalse(settings.isColorBlindMode());
        assertEquals(KeyEvent.VK_UP, settings.getKeyMap().keyOf(GameAction.ROTATE));
    }

    @Test
    void 화면_크기는_Small_Medium_Large_순환() { // SET-2
        assertEquals(ScreenSize.MEDIUM, ScreenSize.SMALL.getNextSize());
        assertEquals(ScreenSize.LARGE, ScreenSize.MEDIUM.getNextSize());
        assertEquals(ScreenSize.SMALL, ScreenSize.LARGE.getNextSize());
    }

    @Test
    void 화면_크기에_null은_허용하지_않는다() {
        assertThrows(IllegalArgumentException.class, () -> new Settings().setScreenSize(null));
    }
    //#endregion
}
