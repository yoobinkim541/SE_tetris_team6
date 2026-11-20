package tetris.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;
import tetris.feature.game.GameSnapshot;
import tetris.ui.theme.UiKit;

/** GAME OVER 화면: 최종 Score, Level 표시. Enter로 다음 단계 (Name 입력 또는 Scoreboard). */
public class GameOverPanel extends JPanel {
    private GameSnapshot finalState;

    public GameOverPanel(Runnable onContinue) {
        if (onContinue == null) throw new IllegalArgumentException("onContinue cannot be null");
        setOpaque(false);
        setFocusable(true);
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, onContinue);
    }

    public void show(GameSnapshot finalState) {
        this.finalState = finalState;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        int h = getHeight();
        int unit = Theme.unit(h);
        UiKit.paintScene(g2, w, h);

        int boxW = Math.min(w - unit * 2, unit * 18);
        int boxH = Math.min(h - unit * 4, unit * 18);
        int boxX = (w - boxW) / 2;
        int boxY = Math.max(unit, (h - boxH) / 2 - unit);
        g2.setColor(new Color(8, 12, 28, 190));
        g2.fillRoundRect(boxX, boxY, boxW, boxH, 18, 18);
        UiKit.paintFrame(g2, UiKit.Tone.MAGENTA, boxX, boxY, boxW, boxH);

        g2.setFont(Theme.font("게임 오버", unit * 2, false));
        g2.setColor(Color.WHITE);
        Theme.drawCentered(g2, "게임 오버", w / 2, boxY + unit * 3);

        if (finalState != null) {
            int y = boxY + unit * 4;
            y = drawResult(g2, "점수", String.valueOf(finalState.score()), w / 2, y, unit, boxW);
            y = drawResult(g2, "레벨", String.valueOf(finalState.level()), w / 2, y, unit, boxW);
            drawResult(g2, "줄", String.valueOf(finalState.lines()), w / 2, y, unit, boxW);
        }

        Theme.drawGuide(g2, Messages.keyGuide(MainFrame.Screen.GAME_OVER, null), w, h);
    }

    private static int drawResult(Graphics2D g, String label, String value, int centerX, int y, int unit, int boxW) {
        g.setFont(Theme.font(label, Math.max(11, unit * 3 / 4), false));
        g.setColor(Theme.DIM_TEXT);
        Theme.drawCentered(g, label, centerX, y + unit);
        int capW = Math.min(boxW - unit * 4, unit * 10);
        int capH = unit * 2;
        int capX = centerX - capW / 2;
        int capY = y + unit + unit / 3;
        UiKit.paintCapsule(g, capX, capY, capW, capH);
        int size = unit + unit / 3;
        g.setFont(Theme.font(value, size, true));
        while (g.getFontMetrics().stringWidth(value) > capW - unit && size > 8) {
            g.setFont(Theme.font(value, --size, true));
        }
        g.setColor(Color.WHITE);
        Theme.drawCentered(g, value, centerX, capY + capH / 2 + g.getFontMetrics().getAscent() / 2 - 1);
        return capY + capH + unit;
    }
}
