package tetris;

import java.util.Arrays;

/**
 * 개발자 모드 스위치. 실행 인자 {@code --dev} 또는 시스템 속성 {@code tetris.dev=true}일 때만 켜진다.
 * 꺼져 있으면 화면과 동작이 일반 실행과 같다 (평가 빌드 기본값).
 */
public final class DevMode {
    public static final String FLAG = "--dev";
    private static volatile boolean enabled = Boolean.getBoolean("tetris.dev");

    private DevMode() {
    }

    public static boolean enabled() {
        return enabled;
    }

    /** main 인자에 --dev가 있으면 켠다. 없으면 기존 값(시스템 속성)을 유지한다 */
    public static void enableFrom(String[] args) {
        if (args != null && Arrays.asList(args).contains(FLAG)) enabled = true;
    }

    /** 테스트용 */
    static void set(boolean value) {
        enabled = value;
    }
}
