package tetris.feature.setting;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 기능 - 키(VK 코드) 매핑과 변경 검증 (KEY-1~6). 기존 ControlKeySet / ControlKeyChange 흡수.
 * 불변식: 7개 기능 모두 지정 가능한 키를 가지며 서로 겹치지 않는다 (KEY-3). 이를 깨는 변경은 적용하지 않는다.
 */
public class KeyMap {
    public enum AssignResult { OK, UNCHANGED, RESERVED_KEY, DUPLICATE }

    private static final Map<GameAction, Integer> DEFAULTS = defaults(); // SET-6

    private final EnumMap<GameAction, Integer> keys = new EnumMap<>(GameAction.class);

    public KeyMap() {
        resetToDefaults();
    }

    private static Map<GameAction, Integer> defaults() {
        EnumMap<GameAction, Integer> map = new EnumMap<>(GameAction.class);
        map.put(GameAction.MOVE_LEFT, KeyEvent.VK_LEFT);
        map.put(GameAction.MOVE_RIGHT, KeyEvent.VK_RIGHT);
        map.put(GameAction.SOFT_DROP, KeyEvent.VK_DOWN);
        map.put(GameAction.ROTATE, KeyEvent.VK_UP);
        map.put(GameAction.HARD_DROP, KeyEvent.VK_SPACE);
        map.put(GameAction.PAUSE, KeyEvent.VK_P);
        map.put(GameAction.QUIT, KeyEvent.VK_Q);
        return Collections.unmodifiableMap(map); //unmodifiableMap은 내부 element 수정이 불가능하므로 외부에서 DEFAULTS 수정을 구조적으로 차단한다.
    }

    public int keyOf(GameAction action) {
        return keys.get(action);
    }

    /** 화면에 보일 키 이름 (예: "Left", "Space", "P"). Key Guide와 설정 화면이 사용 (KEY-6) */
    public String displayName(GameAction action) {
        return KeyEvent.getKeyText(keyOf(action));
    }

    /** 키를 눌렀을 때 대응하는 기능. 없으면 null */
    public GameAction actionOf(int keyCode) {
        for (Map.Entry<GameAction, Integer> entry : keys.entrySet()) {
            if (entry.getValue() == keyCode) return entry.getKey();
        }
        return null;
    }

    /** 검증 통과 시에만 적용. 예약키·중복이면 상태를 바꾸지 않는다. 중복 상대 기능은 actionOf로 알 수 있다 */
    public AssignResult assign(GameAction action, int keyCode) {
        if (!isAssignable(keyCode)) return AssignResult.RESERVED_KEY;     // KEY-2
        if (keyOf(action) == keyCode) return AssignResult.UNCHANGED;       // KEY-4
        if (actionOf(keyCode) != null) return AssignResult.DUPLICATE;      // KEY-3: 자동 교환 없음

        keys.put(action, keyCode);
        return AssignResult.OK;
    }

    /** 7개 기능 전체를 한 번에 바꾼다. 누락·예약키·중복이 하나라도 있으면 아무것도 바꾸지 않고 false (명세 12.2) */
    public boolean tryReplaceAll(Map<GameAction, Integer> newKeys) {
        //전체 GameAction에 대한 키 바인딩이 newKeys에 정의되어 있지 않다 -> 키 프리셋 대체 실패
        if (newKeys == null || !newKeys.keySet().containsAll(DEFAULTS.keySet())) return false;

        Set<Integer> used = new HashSet<>();
        for (GameAction action : GameAction.values()) {
            Integer code = newKeys.get(action);
            //Action에 대응하는 키가 없음 || 지정 불가능한 키 || 서로 다른 Action이 똑같은 키를 바인딩함 -> 키 프리셋 대체 실패 
            if (code == null || !isAssignable(code) || !used.add(code)) return false;
        }

        //키 프리셋을 newKey로 교체
        for (GameAction action : GameAction.values()) keys.put(action, newKeys.get(action));
        return true;
    }

    /** 저장용 읽기 전용 사본 */
    public Map<GameAction, Integer> codes() {
        return Collections.unmodifiableMap(new EnumMap<>(keys));
    }

    /** 지정 가능한 키인지: A-Z, 0-9, 방향키, Space (Enter/Esc/수정키/F키 제외) */
    public static boolean isAssignable(int keyCode) {
        return (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z)
                || (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
                || (keyCode >= KeyEvent.VK_LEFT && keyCode <= KeyEvent.VK_DOWN)
                || keyCode == KeyEvent.VK_SPACE;
    }

    public void resetToDefaults() {
        keys.putAll(DEFAULTS);
    }
}
