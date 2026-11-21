package tetris.feature.setting;

import tetris.feature.game.Difficulty;

/** 사용자 설정 모델 (SET-1~6). 기존 ColorChange / BoardSizeChange 흡수. 저장은 save 계층 담당 */
public class Settings {
    private static final ScreenSize DEFAULT_SCREEN_SIZE = ScreenSize.MEDIUM; // SET-6
    private static final boolean DEFAULT_COLOR_BLIND_MODE = false;
    private static final boolean DEFAULT_DEV_NETWORK_OVERLAY = false; // 개발자 모드: 기본 꺼짐
    private static final Difficulty DEFAULT_DIFFICULTY = Difficulty.NORMAL; // DIF-1
    private static final ControlStyle DEFAULT_CONTROL_STYLE = ControlStyle.MODERN; // CTL-1
    private static final boolean DEFAULT_EFFECTS_HIGH = true;
    private static final boolean DEFAULT_SFX = true;
    private static final boolean DEFAULT_BGM = true;

    private ScreenSize screenSize = DEFAULT_SCREEN_SIZE;
    private boolean fullscreen;
    private boolean colorBlindMode = DEFAULT_COLOR_BLIND_MODE;
    private boolean devNetworkOverlay = DEFAULT_DEV_NETWORK_OVERLAY;
    private Difficulty difficulty = DEFAULT_DIFFICULTY;
    private ControlStyle controlStyle = DEFAULT_CONTROL_STYLE;
    private boolean effectsHigh = DEFAULT_EFFECTS_HIGH;
    private boolean sfx = DEFAULT_SFX;
    private boolean bgm = DEFAULT_BGM;
    private final KeyMap keyMap = new KeyMap();

    public ScreenSize getScreenSize() {
        return screenSize;
    }

    public void setScreenSize(ScreenSize size) {
        if (size == null) throw new IllegalArgumentException("size cannot be null");
        screenSize = size;
    }

    /** 테두리 없는 현재 모니터. 창 크기(Small/Medium/Large)는 끄면 돌아오도록 그대로 둔다 */
    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean enabled) {
        fullscreen = enabled;
    }

    public boolean isColorBlindMode() {
        return colorBlindMode;
    }

    public void setColorBlindMode(boolean enabled) {
        colorBlindMode = enabled;
    }

    /** 개발자 모드: P2P 로비·대전 화면에 연결 상태를 표시한다 (dev-net-overlay). 기본 꺼짐 */
    public boolean isDevNetworkOverlay() {
        return devNetworkOverlay;
    }

    public void setDevNetworkOverlay(boolean enabled) {
        devNetworkOverlay = enabled;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        if (difficulty == null) throw new IllegalArgumentException("difficulty cannot be null");
        this.difficulty = difficulty;
    }

    public ControlStyle getControlStyle() {
        return controlStyle;
    }

    public void setControlStyle(ControlStyle style) {
        if (style == null) throw new IllegalArgumentException("style cannot be null");
        controlStyle = style;
    }

    /** 연출 입자. 없으면 높음. {@code -Dtetris.fx=low} 와 같이 낮음이면 입자를 끈다 */
    public boolean isEffectsHigh() {
        return effectsHigh;
    }

    public void setEffectsHigh(boolean high) {
        effectsHigh = high;
    }

    /** 효과음. 저장 파일에 없으면 켜짐 */
    public boolean isSfxEnabled() {
        return sfx;
    }

    public void setSfxEnabled(boolean enabled) {
        sfx = enabled;
    }

    /** 배경음. 저장 파일에 없으면 켜짐 */
    public boolean isBgmEnabled() {
        return bgm;
    }

    public void setBgmEnabled(boolean enabled) {
        bgm = enabled;
    }

    /** 키 변경은 KeyMap의 검증 메서드(assign, tryReplaceAll)로만 한다 */
    public KeyMap getKeyMap() {
        return keyMap;
    }

    /** Reset Settings: 위 항목을 모두 기본값으로 (Scoreboard는 건드리지 않음) */
    public void resetToDefaults() {
        screenSize = DEFAULT_SCREEN_SIZE;
        fullscreen = false;
        colorBlindMode = DEFAULT_COLOR_BLIND_MODE;
        devNetworkOverlay = DEFAULT_DEV_NETWORK_OVERLAY;
        difficulty = DEFAULT_DIFFICULTY;
        controlStyle = DEFAULT_CONTROL_STYLE;
        effectsHigh = DEFAULT_EFFECTS_HIGH;
        sfx = DEFAULT_SFX;
        bgm = DEFAULT_BGM;
        keyMap.resetToDefaults();
    }
}
