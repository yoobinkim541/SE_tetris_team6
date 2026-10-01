package tetris.feature.setting;

/** 사용자 설정 모델 (SET-1~6). 기존 ColorChange / BoardSizeChange 흡수. 저장은 save 계층 담당 */
public class Settings {
    private static final ScreenSize DEFAULT_SCREEN_SIZE = ScreenSize.MEDIUM; // SET-6
    private static final boolean DEFAULT_COLOR_BLIND_MODE = false;

    private ScreenSize screenSize = DEFAULT_SCREEN_SIZE;
    private boolean colorBlindMode = DEFAULT_COLOR_BLIND_MODE;
    private final KeyMap keyMap = new KeyMap();

    public ScreenSize getScreenSize() {
        return screenSize;
    }

    public void setScreenSize(ScreenSize size) {
        if (size == null) throw new IllegalArgumentException("size cannot be null");
        screenSize = size;
    }

    public boolean isColorBlindMode() {
        return colorBlindMode;
    }

    public void setColorBlindMode(boolean enabled) {
        colorBlindMode = enabled;
    }

    /** 키 변경은 KeyMap의 검증 메서드(assign, tryReplaceAll)로만 한다 */
    public KeyMap getKeyMap() {
        return keyMap;
    }

    /** Reset Settings: 위 항목을 모두 기본값으로 (Scoreboard는 건드리지 않음) */
    public void resetToDefaults() {
        screenSize = DEFAULT_SCREEN_SIZE;
        colorBlindMode = DEFAULT_COLOR_BLIND_MODE;
        keyMap.resetToDefaults();
    }
}
