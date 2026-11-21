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
    private final Runnable onContinue;
    private final tetris.ui.input.Hits hits = new tetris.ui.input.Hits();

    public GameOverPanel(Runnable onContinue) {
        if (onContinue == null) throw new IllegalArgumentException("onContinue cannot be null");
        this.onContinue = onContinue;
        setOpaque(false);
        setFocusable(true);
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, onContinue);
        tetris.ui.input.MenuMouse.install(this, hits, id -> { }, this::clickHit);
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

        int inset = tetris.ui.theme.GameTheme.snap8(Math.max(16, unit * 2));
        int boxW = Math.min(w - inset * 2, tetris.ui.theme.GameTheme.snap8(Math.max(8, unit * 18)));
        int boxH = Math.min(h - inset * 2, tetris.ui.theme.GameTheme.snap8(Math.max(8, unit * 16)));
        int boxX = (w - boxW) / 2;
        int boxY = (h - boxH) / 2;
        hits.begin(null);
        hits.add("continue", boxX, boxY, boxW, boxH);
        g2.setColor(new Color(8, 12, 28, 190));
        g2.fillRoundRect(boxX, boxY, boxW, boxH, 18, 18);
        UiKit.paintFrame(g2, UiKit.Tone.MAGENTA, boxX, boxY, boxW, boxH);
        g2.setFont(tetris.ui.theme.GameTheme.display(Math.max(22, unit * 2)));
        tetris.ui.theme.UiKit.drawInBox(g2, "게임 오버",
                new java.awt.Rectangle(boxX + unit, boxY + unit, boxW - unit * 2, unit * 3),
                tetris.ui.theme.UiKit.Align.CENTER);

        if (finalState != null) {
            int y = boxY + unit * 4;
            y = drawResult(g2, "점수", String.valueOf(finalState.score()), w / 2, y, unit, boxW);
            y = drawResult(g2, "레벨", String.valueOf(finalState.level()), w / 2, y, unit, boxW);
            drawResult(g2, "줄", String.valueOf(finalState.lines()), w / 2, y, unit, boxW);
        }

        Theme.drawGuide(g2, Messages.keyGuide(MainFrame.Screen.GAME_OVER, null), w, h);
    }

    private static int drawResult(Graphics2D g, String label, String value, int centerX, int y, int unit, int boxW) {
        int capW = Math.min(boxW - unit * 4, Math.max(8, unit * 10));
        int capH = Math.max(32, unit * 2);
        int capX = centerX - capW / 2;
        int labelH = Math.max(12, unit * 3 / 4);
        int gap = 4;
        int capY = y + labelH + gap;
        g.setFont(Theme.font(label, labelH, false));
        g.setColor(Theme.DIM_TEXT);
        UiKit.drawInBox(g, label, new java.awt.Rectangle(capX, capY - gap - labelH, capW, labelH), UiKit.Align.CENTER);
        UiKit.paintCapsule(g, capX, capY, capW, capH);
        g.setFont(Theme.font(value, unit + unit / 3, true));
        g.setColor(Color.WHITE);
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, new java.awt.Rectangle(capX, capY, capW, capH),
                value, UiKit.Align.CENTER);
        return capY + capH + gap;
    }

    public String hitAt(int x, int y) {
        return hits.hit(x, y);
    }

    private void clickHit(String id) {
        if ("continue".equals(id)) onContinue.run();
    }
}
