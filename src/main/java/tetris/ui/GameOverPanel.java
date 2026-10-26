package tetris.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;
import tetris.feature.game.GameSnapshot;

/** GAME OVER 화면: 최종 Score, Level 표시. Enter로 다음 단계 (Name 입력 또는 Scoreboard). 기존 EndScreen 대체. */
public class GameOverPanel extends JPanel {
    private GameSnapshot finalState;

    /** onContinue: Enter를 눌렀을 때 다음 단계로 */
    public GameOverPanel(Runnable onContinue) {
        if (onContinue == null) throw new IllegalArgumentException("onContinue cannot be null");
        setBackground(Theme.BACKGROUND);
        setFocusable(true);
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, onContinue);
    }

    public void show(GameSnapshot finalState) {
        this.finalState = finalState;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        int h = getHeight();
        int unit = Theme.unit(h);

        g2.setFont(Theme.text(unit * 3, true));
        g2.setColor(Theme.WARNING);
        Theme.drawCentered(g2, Messages.get("gameOver.title"), w / 2, h / 4);

        if (finalState != null) {
            int y = h * 2 / 5;
            y = drawResult(g2, Messages.get("game.score"), String.valueOf(finalState.score()), w / 2, y, unit);
            y = drawResult(g2, Messages.get("game.level"), String.valueOf(finalState.level()), w / 2, y, unit);
            drawResult(g2, Messages.get("game.lines"), String.valueOf(finalState.lines()), w / 2, y, unit);
        }

        Theme.drawGuide(g2, Messages.keyGuide(MainFrame.Screen.GAME_OVER, null), w, h);
    }

    private static int drawResult(Graphics2D g, String label, String value, int centerX, int y, int unit) {
        g.setFont(Theme.text(unit, false));
        g.setColor(Theme.DIM_TEXT);
        Theme.drawCentered(g, label, centerX, y);
        g.setFont(Theme.text(unit * 2, true));
        g.setColor(Theme.TEXT);
        Theme.drawCentered(g, value, centerX, y + unit * 2 + unit / 2);
        return y + unit * 5;
    }
}
