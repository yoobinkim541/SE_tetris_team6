package tetris.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.JPanel;
import tetris.feature.score.ScoreEntry;

/** 스코어보드 화면. 기존 ScoreBoardScreen 대체. */
public class ScoreBoardPanel extends JPanel {
    private static final int MAIN_MENU = 0;
    private static final int EXIT = 1;

    private final Runnable onMainMenu;
    private final Runnable onExit;
    private List<ScoreEntry> entries = List.of();
    private int highlightIndex = -1;
    private boolean afterGame;
    private int selected = MAIN_MENU;
    private String notice = "";

    public ScoreBoardPanel(Runnable onMainMenu, Runnable onExit) {
        if (onMainMenu == null || onExit == null) throw new IllegalArgumentException("onMainMenu, onExit cannot be null");
        this.onMainMenu = onMainMenu;
        this.onExit = onExit;
        setBackground(Theme.BACKGROUND);
        setFocusable(true);

        KeyBindingManager.bindKey(this, KeyEvent.VK_UP, () -> moveSelection(-1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_DOWN, () -> moveSelection(1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, this::activateSelected);
        KeyBindingManager.bindKey(this, KeyEvent.VK_ESCAPE, () -> !afterGame, onMainMenu);
    }

    /** 메뉴에서 열 때. highlightIndex: 방금 등록한 기록의 순위 index, 강조 없으면 -1. 기록이 없으면 '기록 없음' 표시 */
    public void show(List<ScoreEntry> entries, int highlightIndex) {
        show(entries, highlightIndex, false);
    }

    /** afterGame이면 게임 종료 후 화면: 하단에 Main Menu(기본) / Exit 선택지를 표시한다 */
    public void show(List<ScoreEntry> entries, int highlightIndex, boolean afterGame) {
        this.entries = List.copyOf(entries);
        this.highlightIndex = highlightIndex;
        this.afterGame = afterGame;
        this.selected = MAIN_MENU;
        this.notice = "";
        repaint();
    }

    /** 저장 실패 등 안내 한 줄 */
    public void showNotice(String notice) {
        this.notice = notice;
        repaint();
    }

    public void moveSelection(int delta) {
        if (!afterGame) return;
        selected = Theme.cycle(selected, delta, 2);
        repaint();
    }

    public void activateSelected() {
        if (afterGame && selected == EXIT) onExit.run();
        else onMainMenu.run();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        int h = getHeight();
        int unit = Theme.unit(h);

        g2.setFont(Theme.text(unit * 2, true));
        g2.setColor(Theme.ACCENT);
        Theme.drawCentered(g2, Messages.get("scoreboard.title"), w / 2, unit * 4);

        int rowHeight = unit + unit / 2;
        int top = unit * 6;
        int rankX = w / 10;
        int nameX = w / 5;
        int scoreRight = w * 3 / 4;
        int levelRight = w * 9 / 10;

        g2.setFont(Theme.text(unit, true));
        g2.setColor(Theme.DIM_TEXT);
        g2.drawString(Messages.get("scoreboard.rank"), rankX - unit, top);
        g2.drawString(Messages.get("scoreboard.name"), nameX, top);
        Theme.drawRight(g2, Messages.get("scoreboard.score"), scoreRight, top);
        Theme.drawRight(g2, Messages.get("scoreboard.level"), levelRight, top);

        if (entries.isEmpty()) {
            g2.setFont(Theme.text(unit, false));
            Theme.drawCentered(g2, Messages.get("scoreboard.empty"), w / 2, top + rowHeight * 3);
        }

        for (int i = 0; i < entries.size(); i++) {
            ScoreEntry entry = entries.get(i);
            int baseline = top + rowHeight * (i + 1);
            if (i == highlightIndex) {
                g2.setColor(Theme.HIGHLIGHT_ROW);
                g2.fillRect(rankX - unit * 2, baseline - unit - unit / 6, levelRight - rankX + unit * 3, rowHeight);
            }
            g2.setFont(Theme.text(unit, i == highlightIndex));
            g2.setColor(i == highlightIndex ? Theme.ACCENT : Theme.TEXT);
            Theme.drawRight(g2, String.valueOf(i + 1), rankX + unit, baseline);
            g2.drawString(entry.name(), nameX, baseline);
            Theme.drawRight(g2, String.valueOf(entry.score()), scoreRight, baseline);
            Theme.drawRight(g2, String.valueOf(entry.level()), levelRight, baseline);
        }

        int bottom = top + rowHeight * 11 + unit;
        if (!notice.isEmpty()) {
            g2.setFont(Theme.text(Math.max(10, unit * 3 / 4), false));
            g2.setColor(Theme.WARNING);
            Theme.drawCentered(g2, notice, w / 2, bottom);
        }
        if (afterGame) {
            List<String> options = List.of(Messages.get("scoreboard.mainMenu"), Messages.get("scoreboard.exit"));
            Theme.drawMenu(g2, options, selected, w / 2, bottom + unit / 2, unit);
        }

        String guide = afterGame
                ? Messages.get("guide.scoreboardAfterGame")
                : Messages.keyGuide(MainFrame.Screen.SCOREBOARD, null);
        Theme.drawGuide(g2, guide, w, h);
    }
}
