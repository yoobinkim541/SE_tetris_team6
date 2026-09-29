package tetris.ui;

import javax.swing.JPanel;
import tetris.feature.setting.GameAction;
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

    /** Control Keys 하위 화면(7개 기능과 현재 키 목록)을 연다 */
    public void openControlKeys() {
        // 하위 화면 표시
    }

    /** 하위 화면을 닫고 설정 목록으로 돌아간다 (Esc) */
    public void closeControlKeys() {
        // 설정 목록 복귀
    }

    /** 선택한 기능의 새 키 입력 대기 시작: "새 키를 누르세요 (Esc 취소)" 표시 */
    public void beginKeyCapture(GameAction action) {
        // 입력 대기 상태 진입
    }

    /** 새 키 입력 대기 취소 (Esc). 키 설정은 바뀌지 않는다 */
    public void cancelKeyCapture() {
        // 대기 상태 해제
    }

    /** Control Keys: 새 키 입력을 받아 KeyMap.assign 결과에 따라 안내 문구 표시 */
    public void captureKey(int keyCode) {
        // OK -> 적용·저장, RESERVED_KEY/DUPLICATE -> 안내만
    }
}
