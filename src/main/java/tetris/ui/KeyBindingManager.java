package tetris.ui;

import javax.swing.JComponent;
import tetris.feature.game.GameSession;
import tetris.feature.setting.KeyMap;

/** InputMap/ActionMap Key Binding (KEY-9). 눌림/뗌을 모두 바인딩해 반복 규칙(명세 7.3)을 구현한다. */
public class KeyBindingManager {
    /** WHEN_IN_FOCUSED_WINDOW로 게임 키를 세션 명령에 연결 */
    public void bindGameKeys(JComponent target, KeyMap keyMap, GameSession session) {
        // Rotate/Hard Drop/Pause/Quit은 최초 눌림만 처리, 좌/우/Down은 반복 허용
    }

    /** 조작키 변경 후 바인딩 다시 적용 */
    public void rebind(KeyMap keyMap) {
        // 기존 바인딩 제거 후 재등록
    }

    /** 창 포커스 이탈 시 눌림 상태 초기화 (KEY-8) */
    public void clearPressedKeys() {
        // 초기화
    }
}
