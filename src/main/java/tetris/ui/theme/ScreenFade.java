package tetris.ui.theme;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * 화면 전환 페이드. 250ms 안에 사라지고, 포커스를 받지 않아 키 입력을 막지 않는다.
 */
public final class ScreenFade extends JComponent {
    private static final int DURATION_MS = 250;
    private static final int FRAME_MS = 16;

    private float alpha;
    private final Timer timer = new Timer(FRAME_MS, this::tick);

    public ScreenFade() {
        setOpaque(false);
        setFocusable(false);
        setRequestFocusEnabled(false);
        timer.setRepeats(true);
        timer.setCoalesce(true);
    }

    public void play() {
        alpha = 0.55f;
        setVisible(true);
        if (!timer.isRunning()) timer.start();
    }

    private void tick(ActionEvent event) {
        alpha -= 0.55f * FRAME_MS / DURATION_MS;
        if (alpha <= 0f) {
            alpha = 0f;
            timer.stop();
            setVisible(false);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (alpha <= 0f) return;
        g.setColor(new Color(0f, 0f, 0f, alpha));
        g.fillRect(0, 0, getWidth(), getHeight());
    }
}
