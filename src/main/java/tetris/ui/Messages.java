package tetris.ui;

import tetris.feature.setting.KeyMap;

/** UI 문자열 단일 출처 (명세 9.2). Key Guide 문구는 현재 KeyMap에서 생성하고 하드코딩하지 않는다. */
public class Messages {
    public static String get(String key) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 화면별 Key Guide 문구. 플레이 키는 keyMap.displayName으로 만들어 키 변경이 자동 반영된다 (명세 9.1) */
    public static String keyGuide(MainFrame.Screen screen, KeyMap keyMap) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Pause 화면 전용 Key Guide */
    public static String pauseKeyGuide(KeyMap keyMap) {
        throw new UnsupportedOperationException("TODO");
    }
}
