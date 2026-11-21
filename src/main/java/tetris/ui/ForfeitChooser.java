package tetris.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import tetris.feature.versus.api.PlayerId;
import tetris.ui.theme.GameTheme;
import tetris.ui.theme.UiKit;

/** 대전 기권 선택. Windows 기본 창 대신 테마 오버레이로 고른다. */
public final class ForfeitChooser {
    private ForfeitChooser() {
    }

    public static PlayerId choose(JFrame owner) {
        Panel panel = new Panel();
        JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        panel.onClose(dialog::dispose);
        dialog.setVisible(true);
        return panel.choice();
    }

    private static final class Panel extends JPanel {
        private int selected = 2;
        private PlayerId choice;
        private Runnable close = () -> { };
        private final Rectangle[] buttons = {new Rectangle(), new Rectangle(), new Rectangle()};

        Panel() {
            setPreferredSize(new Dimension(440, 220));
            setFocusable(true);
            setBackground(new Color(0x070B18));
            KeyBindingManager.bindKey(this, KeyEvent.VK_LEFT, () -> move(-1));
            KeyBindingManager.bindKey(this, KeyEvent.VK_RIGHT, () -> move(1));
            KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, this::activate);
            KeyBindingManager.bindKey(this, KeyEvent.VK_ESCAPE, this::cancel);
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { hover(e.getX(), e.getY()); }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    hover(e.getX(), e.getY());
                    if (hit(e.getX(), e.getY()) >= 0) activate();
                }
            });
        }

        void onClose(Runnable close) {
            this.close = close;
        }

        PlayerId choice() {
            return choice;
        }

        private void move(int delta) {
            selected = Math.floorMod(selected + delta, 3);
            repaint();
        }

        private void activate() {
            choice = switch (selected) {
                case 0 -> PlayerId.ONE;
                case 1 -> PlayerId.TWO;
                default -> null;
            };
            close.run();
        }

        private void cancel() {
            choice = null;
            close.run();
        }

        private int hit(int x, int y) {
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].contains(x, y)) return i;
            }
            return -1;
        }

        private void hover(int x, int y) {
            int index = hit(x, y);
            setCursor(index >= 0 ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                    : java.awt.Cursor.getDefaultCursor());
            if (index >= 0 && index != selected) {
                selected = index;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics;
            GameTheme.applyQuality(g);
            UiKit.paintNight(g, getWidth(), getHeight());
            UiKit.paintFrame(g, UiKit.Tone.VIOLET, 8, 8, getWidth() - 16, getHeight() - 16);
            g.setFont(GameTheme.display(22));
            g.setColor(Color.WHITE);
            GameTheme.drawCentered(g, "기권할 플레이어", getWidth() / 2, 52);
            String[] labels = {"P1 기권", "P2 기권", "취소"};
            int w = 112;
            int h = 40;
            int gap = 12;
            int x0 = (getWidth() - (w * 3 + gap * 2)) / 2;
            for (int i = 0; i < labels.length; i++) {
                int x = x0 + i * (w + gap);
                buttons[i].setBounds(x, 96, w, h);
                UiKit.paintButton(g, i == selected, x, 96, w, h, false, UiKit.pulse());
                g.setFont(GameTheme.display(15));
                g.setColor(Color.WHITE);
                UiKit.drawInBox(g, labels[i], buttons[i], UiKit.Align.CENTER);
            }
            g.setFont(GameTheme.text(12, false));
            g.setColor(Color.WHITE);
            GameTheme.drawCentered(g, "← → 선택, Enter 확정, Esc 취소", getWidth() / 2, 176);
        }
    }
}
