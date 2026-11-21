package tetris.ui.versus;

import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.JTextField;
import tetris.ui.theme.GameTheme;
import tetris.ui.theme.UiKit;

/** 유리 캡슐 입력칸. 재고 모양을 그리지 않는다 */
public final class GlassField extends JTextField {
    private final JComponent focusOwner;

    public GlassField(String text, int columns, JComponent focusOwner) {
        super(text, columns);
        this.focusOwner = focusOwner;
        setOpaque(false);
        setForeground(java.awt.Color.WHITE);
        setCaretColor(UiKit.CYAN);
        setFont(GameTheme.text(16, false));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 12, 2, 12));
    }

    @Override
    public boolean requestFocusInWindow() {
        return focusOwner != null ? focusOwner.requestFocusInWindow() : super.requestFocusInWindow();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            UiKit.paintGlassCapsule(g2, 0, 0, getWidth(), getHeight());
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
