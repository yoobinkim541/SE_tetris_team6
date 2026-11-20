package tetris;

import java.util.Arrays;

/**
 * 개발자 모드 스위치 (dev-net-overlay). 두 스위치 중 하나라도 켜져 있으면 켜진다.
 * <ul>
 *   <li>실행 인자 {@code --dev} 또는 시스템 속성 {@code tetris.dev=true} (flagEnabled)</li>
 *   <li>설정 "개발자: 연결 상태 표시" (settingEnabled)</li>
 * </ul>
 * 꺼져 있으면 화면과 동작이 일반 실행과 같다 (평가 빌드 기본값).
 */
public final class DevMode {
    public static final String FLAG = "--dev";
    private static volatile boolean flagEnabled = Boolean.getBoolean("tetris.dev");
    private static volatile boolean settingEnabled;

    private DevMode() {
    }

    public static boolean enabled() {
        return flagEnabled || settingEnabled;
    }

    /** --dev 인자·시스템 속성으로 켠 값 */
    public static boolean flagEnabled() {
        return flagEnabled;
    }

    /** 설정 토글로 켠 값 */
    public static boolean settingEnabled() {
        return settingEnabled;
    }

    /** main 인자에 --dev가 있으면 켠다. 없으면 기존 값(시스템 속성)을 유지한다 */
    public static void enableFrom(String[] args) {
        if (args != null && Arrays.asList(args).contains(FLAG)) flagEnabled = true;
    }

    /** 설정 파일을 읽은 뒤 설정 토글 값으로 맞춘다. --dev는 건드리지 않는다 */
    public static void setSettingEnabled(boolean value) {
        settingEnabled = value;
    }

    /** 테스트용: 두 스위치를 함께 초기화한다 */
    static void set(boolean value) {
        flagEnabled = value;
        settingEnabled = false;
    }
}
