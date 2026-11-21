package tetris.ui.versus;

import javax.swing.JComponent;

/** 화면에 올리지 않는 동작. 보이는 알약 버튼이 이 객체의 doClick과 같은 일을 한다 */
public final class LobbyPress extends JComponent {
    private final Runnable action;
    private String text;

    public LobbyPress(String label, Runnable action) {
        this.text = label;
        this.action = action;
        setFocusable(false);
        setVisible(false);
    }

    public void doClick() {
        if (isEnabled()) action.run();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
