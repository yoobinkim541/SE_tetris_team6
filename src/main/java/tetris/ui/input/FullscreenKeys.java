package tetris.ui.input;

import java.awt.event.KeyEvent;

/** F11과 Alt+Enter만 전체 화면을 토글한다. Esc는 각 화면의 의미 그대로 둔다 */
public final class FullscreenKeys {
    private FullscreenKeys() {
    }

    public static boolean isToggle(KeyEvent event) {
        if (event == null || event.getID() != KeyEvent.KEY_PRESSED) return false;
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) return false;
        if (event.getKeyCode() == KeyEvent.VK_F11) return true;
        return event.getKeyCode() == KeyEvent.VK_ENTER && event.isAltDown()
                && !event.isControlDown() && !event.isMetaDown();
    }
}
