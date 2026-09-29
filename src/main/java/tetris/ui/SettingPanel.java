package tetris.ui;

import javax.swing.JPanel;
import tetris.feature.setting.Settings;

/** 설정 화면 (SET-2): Screen Size / Control Keys / Color Blind / Reset Scoreboard / Reset Settings. 기존 SettingScreen 대체. */
public class SettingPanel extends JPanel {
    public SettingPanel(Settings settings) {
        // 현재 값 표시
    }

    public void moveSelection(int delta) {
        // 항목 이동
    }

    public void activateSelected() {
        // Enter: 순환/토글/하위 화면/확인창
    }

    /** Control Keys: 새 키 입력을 받아 KeyMap.assign 결과에 따라 안내 문구 표시 */
    public void captureKey(int keyCode) {
        // OK -> 적용·저장, RESERVED_KEY/DUPLICATE -> 안내만
    }
}
