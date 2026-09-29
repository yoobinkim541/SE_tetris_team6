package tetris.feature.setting;

/** 사용자 설정 모델 (SET-1~6). 기존 ColorChange / BoardSizeChange 흡수. */
public class Settings {
    public Settings() {
        // 기본값: Medium, 색맹 OFF, 기본 KeyMap
    }

    public ScreenSize getScreenSize() {
        throw new UnsupportedOperationException("TODO");
    }

    public void setScreenSize(ScreenSize size) {
        // 변경
    }

    public boolean isColorBlindMode() {
        throw new UnsupportedOperationException("TODO");
    }

    public void setColorBlindMode(boolean enabled) {
        // 변경
    }

    public KeyMap getKeyMap() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Reset Settings: 위 항목을 모두 기본값으로 (Scoreboard는 건드리지 않음) */
    public void resetToDefaults() {
        // 초기화
    }
}
