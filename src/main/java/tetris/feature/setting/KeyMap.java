package tetris.feature.setting;

/** 기능 - 키(VK 코드) 매핑과 변경 검증 (KEY-1~6). 기존 ControlKeySet / ControlKeyChange 흡수. */
public class KeyMap {
    public enum AssignResult { OK, UNCHANGED, RESERVED_KEY, DUPLICATE }

    public KeyMap() {
        // 기본값: Left Right Down Up Space P Q
    }

    public int keyOf(GameAction action) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 화면에 보일 키 이름 (예: "Left", "Space", "P"). Key Guide와 설정 화면이 사용 (KEY-6) */
    public String displayName(GameAction action) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 키를 눌렀을 때 대응하는 기능. 없으면 null */
    public GameAction actionOf(int keyCode) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 검증 통과 시에만 적용. 예약키·중복이면 상태를 바꾸지 않는다 */
    public AssignResult assign(GameAction action, int keyCode) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 지정 가능한 키인지: A-Z, 0-9, 방향키, Space (Enter/Esc/수정키/F키 제외) */
    public static boolean isAssignable(int keyCode) {
        throw new UnsupportedOperationException("TODO");
    }

    public void resetToDefaults() {
        // 기본 키로 복원
    }
}
