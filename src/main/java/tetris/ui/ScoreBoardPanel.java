package tetris.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.JPanel;
import tetris.feature.score.ScoreEntry;
import tetris.feature.game.GameMode;
import tetris.feature.game.Difficulty;

/** 스코어보드 화면. 기존 ScoreBoardScreen 대체. */
public class ScoreBoardPanel extends JPanel {
    private static final int MAIN_MENU = 0;
    private static final int EXIT = 1;

    private static final String[] CATEGORIES = {"클래식 · 쉬움", "클래식 · 보통", "클래식 · 어려움", "아이템 · 보통"};
    private final Runnable onMainMenu;
    private final Runnable onExit;
    private List<ScoreEntry> entries = List.of();
    private List<ScoreEntry> allEntries = List.of();
    private int category = 1;
    private int highlightedCategory = -1;
    private int highlightedRank = -1;
    private int highlightIndex = -1;
    private boolean afterGame;
    private int selected = MAIN_MENU;
    private String notice = "";
    private final tetris.ui.input.Hits hits = new tetris.ui.input.Hits();

    public ScoreBoardPanel(Runnable onMainMenu, Runnable onExit) {
        if (onMainMenu == null || onExit == null) throw new IllegalArgumentException("onMainMenu, onExit cannot be null");
        this.onMainMenu = onMainMenu;
        this.onExit = onExit;
        setBackground(Theme.BACKGROUND);
        setFocusable(true);
        KeyBindingManager.bindKey(this, KeyEvent.VK_LEFT, () -> changeCategory(-1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_RIGHT, () -> changeCategory(1));

        KeyBindingManager.bindKey(this, KeyEvent.VK_UP, () -> moveSelection(-1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_DOWN, () -> moveSelection(1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, this::activateSelected);
        KeyBindingManager.bindKey(this, KeyEvent.VK_ESCAPE, () -> !afterGame, onMainMenu);
        tetris.ui.input.MenuMouse.install(this, hits, this::hoverHit, this::clickHit);
    }

    /** 메뉴에서 열 때. highlightIndex: 방금 등록한 기록의 순위 index, 강조 없으면 -1. 기록이 없으면 '기록 없음' 표시 */
    public void show(List<ScoreEntry> entries, int highlightIndex) {
        show(entries, highlightIndex, false);
    }

    /** afterGame이면 게임 종료 후 화면: 하단에 Main Menu(기본) / Exit 선택지를 표시한다 */
    public void show(List<ScoreEntry> entries, int highlightIndex, boolean afterGame) {
        show(entries, highlightIndex, afterGame, GameMode.CLASSIC, Difficulty.NORMAL);
    }

    public void show(List<ScoreEntry> entries, int highlightIndex, boolean afterGame, GameMode mode, Difficulty difficulty) {
        this.allEntries = List.copyOf(entries);
        category = mode == GameMode.ITEM ? 3 : difficulty.ordinal();
        highlightedCategory = category;
        highlightedRank = highlightIndex;
        filterCategory();
        this.afterGame = afterGame;
        this.selected = MAIN_MENU;
        this.notice = "";
        repaint();
    }

    public void changeCategory(int delta) {
        category = Theme.cycle(category, delta, CATEGORIES.length);
        filterCategory();
        repaint();
    }

    private void filterCategory() {
        GameMode mode = category == 3 ? GameMode.ITEM : GameMode.CLASSIC;
        Difficulty difficulty = category == 3 ? Difficulty.NORMAL : Difficulty.values()[category];
        entries = allEntries.stream().filter(e -> e.mode() == mode && e.difficulty() == difficulty).toList();
        highlightIndex = category == highlightedCategory ? highlightedRank : -1;
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
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        int h = getHeight();
        tetris.ui.theme.UiKit.paintScene(g2, w, h);
        int unit = Theme.unit(h);

        tetris.ui.theme.GameTheme.paintTitle(g2, "스코어보드", Math.max(22, unit * 2), w / 2, unit * 3, false);
        String categoryLabel = "◀ " + CATEGORIES[category] + " ▶";
        g2.setFont(Theme.font(categoryLabel, unit, false));
        hits.begin(null);
        int textWidth = g2.getFontMetrics().stringWidth(categoryLabel);
        int textX = w / 2 - textWidth / 2;
        int labelBaseline = unit * 4 + unit / 2;
        int textTop = labelBaseline - g2.getFontMetrics().getAscent();
        int textH = g2.getFontMetrics().getAscent() + g2.getFontMetrics().getDescent();
        int arrow = g2.getFontMetrics().stringWidth("◀") + 8;
        hits.add("prev", textX - 4, textTop - 4, arrow, textH + 8);
        hits.add("next", textX + textWidth - arrow + 4, textTop - 4, arrow, textH + 8);
        Theme.drawCentered(g2, categoryLabel, w / 2, labelBaseline);
        int closeW = Math.max(64, unit * 4);
        int closeH = Math.max(28, unit + 8);
        int closeX = w - closeW - 8;
        int closeY = 8;
        tetris.ui.theme.UiKit.paintCapsule(g2, closeX, closeY, closeW, closeH);
        g2.setFont(Theme.font("닫기", Math.max(12, unit * 3 / 4), false));
        g2.setColor(Theme.TEXT);
        tetris.ui.theme.UiKit.drawInBox(g2, "닫기", new java.awt.Rectangle(closeX, closeY, closeW, closeH),
                tetris.ui.theme.UiKit.Align.CENTER);
        hits.add("close", closeX, closeY, closeW, closeH);

        java.awt.Rectangle table = tableBounds();
        g2.setColor(new Color(8, 14, 32, 190));
        g2.fillRoundRect(table.x, table.y, table.width, table.height, 16, 16);
        tetris.ui.theme.UiKit.paintFrame(g2, tetris.ui.theme.UiKit.Tone.CYAN, table.x, table.y, table.width, table.height);

        int corner = 72;
        int rowHeight = unit + unit / 2;
        int headerBaseline = table.y + corner;
        int rankX = table.x + corner;
        int nameX = rankX + unit * 2;
        int levelRight = table.x + table.width - corner;
        int scoreRight = levelRight - unit * 2;

        g2.setFont(Theme.font("순위", unit, false));
        g2.setColor(Theme.DIM_TEXT);
        tetris.ui.theme.UiKit.drawInBox(g2, "순위", new java.awt.Rectangle(rankX, headerBaseline - unit, unit * 2, unit),
                tetris.ui.theme.UiKit.Align.LEFT);
        tetris.ui.theme.UiKit.drawInBox(g2, "이름", new java.awt.Rectangle(nameX, headerBaseline - unit, unit * 4, unit),
                tetris.ui.theme.UiKit.Align.LEFT);
        tetris.ui.theme.UiKit.drawInBox(g2, "점수", new java.awt.Rectangle(scoreRight - unit * 3, headerBaseline - unit, unit * 3, unit),
                tetris.ui.theme.UiKit.Align.RIGHT);
        tetris.ui.theme.UiKit.drawInBox(g2, "레벨", new java.awt.Rectangle(levelRight - unit * 2, headerBaseline - unit, unit * 2, unit),
                tetris.ui.theme.UiKit.Align.RIGHT);

        if (entries.isEmpty()) {
            g2.setFont(Theme.font(Messages.get("scoreboard.empty"), unit, false));
            Theme.drawCentered(g2, Messages.get("scoreboard.empty"), w / 2, headerBaseline + rowHeight * 3);
        }

        for (int i = 0; i < entries.size(); i++) {
            ScoreEntry entry = entries.get(i);
            java.awt.Rectangle row = rowBounds(i);
            int baseline = row.y + row.height - 6;
            if (i == highlightIndex) {
                g2.setColor(Theme.HIGHLIGHT_ROW);
                g2.fillRect(row.x, row.y, row.width, row.height);
                g2.setColor(tetris.ui.theme.UiKit.CYAN);
                g2.setStroke(new java.awt.BasicStroke(3f));
                g2.drawRect(row.x + 1, row.y + 1, row.width - 3, row.height - 3);
                g2.setStroke(new java.awt.BasicStroke(1f));
            }
            if (i < 3) drawMedal(g2, rankX - unit, baseline - unit / 2, i);
            g2.setFont(Theme.font(entry.name(), unit, false));
            g2.setColor(i == highlightIndex ? Theme.ACCENT : Theme.TEXT);
            tetris.ui.theme.UiKit.drawInBox(g2, String.valueOf(i + 1),
                    new java.awt.Rectangle(rankX, row.y, unit * 2, row.height), tetris.ui.theme.UiKit.Align.RIGHT);
            tetris.ui.theme.UiKit.drawInBox(g2, entry.name(),
                    new java.awt.Rectangle(nameX, row.y, Math.max(8, scoreRight - nameX - unit * 4), row.height),
                    tetris.ui.theme.UiKit.Align.LEFT);
            g2.setFont(tetris.ui.theme.GameTheme.fontFor(String.valueOf(entry.score()), unit, true));
            g2.setColor(java.awt.Color.WHITE);
            tetris.ui.theme.UiKit.drawInBox(g2, String.valueOf(entry.score()),
                    new java.awt.Rectangle(scoreRight - unit * 4, row.y, unit * 4, row.height),
                    tetris.ui.theme.UiKit.Align.RIGHT);
            tetris.ui.theme.UiKit.drawInBox(g2, String.valueOf(entry.level()),
                    new java.awt.Rectangle(levelRight - unit * 2, row.y, unit * 2, row.height),
                    tetris.ui.theme.UiKit.Align.RIGHT);
        }

        int bottom = table.y + table.height + unit;
        if (!notice.isEmpty()) {
            g2.setFont(Theme.font(notice, Math.max(10, unit * 3 / 4), false));
            g2.setColor(Theme.WARNING);
            Theme.drawCentered(g2, notice, w / 2, bottom);
        }
        if (afterGame) {
            List<String> options = List.of(Messages.get("scoreboard.mainMenu"), Messages.get("scoreboard.exit"));
            g2.setFont(Theme.text(unit, true));
            int widest = 0;
            for (String option : options) widest = Math.max(widest, g2.getFontMetrics().stringWidth(option));
            int buttonWidth = widest + unit * 3;
            int buttonHeight = unit + unit / 2;
            int gap = Math.max(6, unit / 3);
            int optionTop = bottom + unit / 2;
            int optionX = w / 2 - buttonWidth / 2;
            hits.add("option:0", optionX, optionTop, buttonWidth, buttonHeight);
            hits.add("option:1", optionX, optionTop + buttonHeight + gap, buttonWidth, buttonHeight);
            Theme.drawMenu(g2, options, selected, w / 2, optionTop, unit);
        }

        String guide = afterGame
                ? Messages.get("guide.scoreboardAfterGame")
                : Messages.keyGuide(MainFrame.Screen.SCOREBOARD, null);
        Theme.drawGuide(g2, guide, w, h);
    }

    public String hitAt(int x, int y) {
        return hits.hit(x, y);
    }

    private void hoverHit(String id) {
        if (id == null || !afterGame || !id.startsWith("option:")) return;
        int index = id.endsWith("1") ? EXIT : MAIN_MENU;
        if (index != selected) {
            selected = index;
            repaint();
        }
    }

    private void clickHit(String id) {
        if (id == null) return;
        switch (id) {
            case "prev" -> changeCategory(-1);
            case "next" -> changeCategory(1);
            case "close" -> onMainMenu.run();
            case "option:0" -> {
                selected = MAIN_MENU;
                activateSelected();
            }
            case "option:1" -> {
                selected = EXIT;
                activateSelected();
            }
            default -> { }
        }
    }

    /** 기록 표. 분류 비교 테스트가 이 사각형만 본다 */
    public java.awt.Rectangle tableBounds() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        int unit = Theme.unit(h);
        return new java.awt.Rectangle(unit, unit * 5, w - unit * 2, unit * 14);
    }

    public java.awt.Rectangle rowBounds(int index) {
        java.awt.Rectangle table = tableBounds();
        int unit = Theme.unit(Math.max(1, getHeight()));
        int corner = 72;
        int rowHeight = unit + unit / 2;
        int y = table.y + corner + 10 + rowHeight * index;
        return new java.awt.Rectangle(table.x + corner / 2, y, table.width - corner, rowHeight - 4);
    }


    /** 강조 행 안쪽. 테두리가 아닌 채움색을 읽는다 */
    public java.awt.Point highlightSample() {
        java.awt.Rectangle row = rowBounds(Math.max(0, highlightIndex));
        return new java.awt.Point(row.x + 8, row.y + row.height / 2);
    }

    private static void drawMedal(Graphics2D g, int x, int y, int rank) {
        Color color = switch (rank) {
            case 0 -> new Color(0xF6C945);
            case 1 -> new Color(0xD7DEE8);
            default -> new Color(0xE0A36A);
        };
        g.setColor(color);
        g.fillOval(x, y - 8, 14, 14);
        g.setColor(new Color(0x1B1E24));
        g.setFont(tetris.ui.theme.GameTheme.fontFor(String.valueOf(rank + 1), 10, true));
        g.drawString(String.valueOf(rank + 1), x + 4, y + 3);
    }
}
