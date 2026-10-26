package tetris.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.Consumer;
import javax.swing.JPanel;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.feature.data.Board;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.setting.Settings;

/**
 * 게임 화면: Board, Next, Score, Level, GameState, Key Guide. 기존 BoardScreen 대체.
 * Pause 메뉴와 확인창은 이 패널의 자식 오버레이로 보드 위에 겹쳐 그린다 (명세 9: CardLayout만으로는 겹칠 수 없음).
 *
 * 배치 (c = 셀 크기, p = 여백 c/2): [p][보드 10c][p][정보 6c][p] x [p][보드 20c][p]
 */
public class GamePanel extends JPanel implements GameListener {
    private static final int INFO_COLUMNS = 6;
    private static final int NEXT_BOX_ROWS = 4;

    private final BlockPalette palette = new BlockPalette();
    private final PausePanel pausePanel;
    private final Consumer<GameSnapshot> onGameOver;
    private ConfirmDialog confirmDialog; // 떠 있는 확인창, 없으면 null
    private GameSnapshot snapshot;
    private int cellSize;
    private boolean colorBlindMode;
    private String[] keyGuide = new String[0];

    /** onGameOver: Game Over 화면으로 넘어가도록 화면 흐름에 알린다 */
    public GamePanel(Settings settings, PausePanel pausePanel, Consumer<GameSnapshot> onGameOver) {
        if (settings == null || pausePanel == null || onGameOver == null)
            throw new IllegalArgumentException("settings, pausePanel, onGameOver cannot be null");
        this.pausePanel = pausePanel;
        this.onGameOver = onGameOver;

        setLayout(null); // 오버레이는 doLayout에서 패널 전체 크기로 맞춘다
        setBackground(Theme.BACKGROUND);
        setFocusable(true);
        enableInputMethods(false); // 한글 IME가 게임 키 입력을 가로채지 않도록 (KEY-10)

        pausePanel.setVisible(false);
        add(pausePanel);
        setCellSize(settings.getScreenSize().cellPixels());
        applySettings(settings);
    }

    /** 셀 크기에 맞는 패널 크기 (창 크기 계산·자동 축소용, RND-2·RND-3) */
    public static Dimension preferredSizeFor(int cellSize) {
        int padding = cellSize / 2;
        return new Dimension(
                padding * 3 + cellSize * (Board.WIDTH + INFO_COLUMNS),
                padding * 2 + cellSize * Board.HEIGHT);
    }

    @Override
    public void onChanged(GameSnapshot snapshot) {
        this.snapshot = snapshot;
        updateOverlays();
        repaint();
    }

    @Override
    public void onGameOver(GameSnapshot snapshot) {
        this.snapshot = snapshot; // 마지막 정상 상태 유지 (SPN-4)
        updateOverlays();
        repaint();
        onGameOver.accept(snapshot);
    }

    /** 설정 변경 시 셀 크기·색맹 모드 다시 적용 */
    public void applySettings(Settings settings) {
        colorBlindMode = settings.isColorBlindMode();
        keyGuide = Messages.keyGuide(MainFrame.Screen.GAME, settings.getKeyMap()).split("\n");
        pausePanel.setKeyGuide(Messages.pauseKeyGuide(settings.getKeyMap()));
        repaint();
    }

    /** 실제로 그릴 셀 크기. 화면에 맞추느라 설정값보다 작을 수 있다 (RND-3) */
    public void setCellSize(int cellSize) {
        this.cellSize = cellSize;
        setPreferredSize(preferredSizeFor(cellSize));
        revalidate();
        repaint();
    }

    /** 확인창을 띄운다. 떠 있는 동안 Pause 메뉴는 숨겨 키가 겹치지 않게 한다 */
    public void showConfirm(ConfirmDialog dialog) {
        if (confirmDialog != null) remove(confirmDialog);
        confirmDialog = dialog;
        add(dialog, 0); // 맨 앞에 그린다
        updateOverlays();
        revalidate();
        repaint();
    }

    /** 확인창을 닫는다. 아직 PAUSED면 Pause 메뉴가 다시 보인다 */
    public void closeConfirm() {
        if (confirmDialog == null) return;
        remove(confirmDialog);
        confirmDialog = null;
        updateOverlays();
        repaint();
    }

    public boolean isConfirmShowing() {
        return confirmDialog != null;
    }

    // PAUSED이고 확인창이 없을 때만 Pause 메뉴를 보인다. 게임이 다시 돌면 확인창도 닫는다
    private void updateOverlays() {
        boolean paused = snapshot != null && snapshot.state() == GameState.PAUSED;
        if (!paused && confirmDialog != null) {
            remove(confirmDialog);
            confirmDialog = null;
        }
        boolean showPause = paused && confirmDialog == null;
        if (showPause && !pausePanel.isVisible()) pausePanel.resetSelection();
        pausePanel.setVisible(showPause);
    }

    @Override
    public void doLayout() {
        for (Component child : getComponents()) child.setBounds(0, 0, getWidth(), getHeight());
    }

    // ── 그리기 ───────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = Theme.smooth(g);
        int padding = cellSize / 2;
        int boardX = padding;
        int boardY = padding;

        drawBoard(g2, boardX, boardY);
        drawInfo(g2, boardX + cellSize * Board.WIDTH + padding, boardY);
    }

    private void drawBoard(Graphics2D g, int x, int y) {
        g.setColor(Theme.PANEL);
        g.fillRect(x, y, cellSize * Board.WIDTH, cellSize * Board.HEIGHT);
        g.setColor(Theme.GRID);
        for (int col = 0; col <= Board.WIDTH; col++) {
            g.drawLine(x + col * cellSize, y, x + col * cellSize, y + cellSize * Board.HEIGHT);
        }
        for (int row = 0; row <= Board.HEIGHT; row++) {
            g.drawLine(x, y + row * cellSize, x + cellSize * Board.WIDTH, y + row * cellSize);
        }
        if (snapshot == null) return;

        int[][] cells = snapshot.cells();
        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                int value = cells[row][col];
                if (value <= 0) continue;
                drawCell(g, x + col * cellSize, y + row * cellSize, cellSize, BlockType.values()[value - 1]);
            }
        }

        Block current = snapshot.currentBlock();
        for (var cell : snapshot.clearingCells()) {
            if (cell.row() < 0 || cell.row() >= Board.HEIGHT || cell.col() < 0 || cell.col() >= Board.WIDTH) continue;
            g.setColor(Color.WHITE);
            g.fillRect(x + cell.col() * cellSize, y + cell.row() * cellSize, cellSize, cellSize);
        }
        if (current == null) return;
        int[][] shape = current.getShape();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                int row = current.getRow() + r;
                if (shape[r][c] == 0 || row < 0) continue; // 보드 위 숨은 줄은 그리지 않는다
                drawCell(g, x + (current.getCol() + c) * cellSize, y + row * cellSize, cellSize, current.getType(), current.itemCellAt(r, c).orElse(null));
            }
        }
    }

    private void drawInfo(Graphics2D g, int x, int y) {
        int width = cellSize * INFO_COLUMNS;
        boolean compact = cellSize <= 20;
        int labelSize = compact ? Math.max(6, cellSize / 2) : Math.max(9, cellSize * 9 / 20);
        int valueSize = Math.max(12, cellSize * 4 / 5);

        y = drawLabel(g, Messages.get("game.next"), x, y, labelSize);
        int boxHeight = cellSize * NEXT_BOX_ROWS;
        g.setColor(Theme.PANEL);
        g.fillRect(x, y, width, boxHeight);
        if (snapshot != null && snapshot.nextBlock() != null) drawNext(g, snapshot.nextBlock(), x, y, width, boxHeight);
        y += boxHeight + cellSize / 2;

        long score = snapshot == null ? 0 : snapshot.score();
        int level = snapshot == null ? 1 : snapshot.level();
        int lines = snapshot == null ? 0 : snapshot.lines();
        String state = snapshot == null ? GameState.LOADING.name() : snapshot.state().name();
        int lineHeight = labelSize + labelSize / 2;
        int guideTop = cellSize / 2 + cellSize * Board.HEIGHT - lineHeight * (keyGuide.length + 1);
        if (compact) {
            java.util.List<String> rows = new java.util.ArrayList<>();
            rows.add("SCORE " + score);
            rows.add("LEVEL " + level + "  LINES " + lines);
            rows.add("STATE " + state);
            if (snapshot != null) {
                rows.add("MODE " + snapshot.mode());
                rows.add("DIFF " + snapshot.difficulty());
                if (snapshot.mode() == tetris.feature.game.GameMode.ITEM)
                    rows.add("NEXT ITEM " + snapshot.nextItemLines());
            }
            int plainRows = rows.size();
            if (snapshot != null) {
                for (var effect : snapshot.effects())
                    rows.add(effectTag(effect) + " " + remainingText(effect));
            }
            g.setFont(Theme.text(labelSize, true));
            for (int i = 0; i < rows.size(); i++) {
                String row = rows.get(i);
                g.setColor(i < plainRows ? Theme.TEXT : Theme.ACCENT); // 지속 효과 줄은 노란색
                // 큰 점수도 정보 영역 밖으로 넘치지 않도록 이 행에서만 글자 크기를 줄인다.
                int fontSize = labelSize;
                g.setFont(Theme.text(fontSize, true));
                while (g.getFontMetrics().stringWidth(row) > width && fontSize > 3)
                    g.setFont(Theme.text(--fontSize, true));
                g.drawString(row, x, y += labelSize + labelSize / 2);
            }
        } else {
            y = drawValue(g, Messages.get("game.score"), String.valueOf(score), x, y, labelSize, valueSize);
            // LEVEL·LINES는 한 줄 두 칸으로 놓아 지속 효과 섹션 자리를 만든다
            drawValue(g, Messages.get("game.level"), String.valueOf(level), x, y, labelSize, valueSize);
            y = drawValue(g, Messages.get("game.lines"), String.valueOf(lines), x + width / 2, y, labelSize, valueSize);
            y = drawValue(g, Messages.get("game.state"), state, x, y, labelSize, labelSize + labelSize / 4);
            if (snapshot != null) {
                g.setFont(Theme.text(labelSize, true));
                g.setColor(Theme.TEXT);
                int step = labelSize + labelSize / 2;
                g.drawString("MODE " + snapshot.mode(), x, y += step);
                g.drawString("DIFFICULTY " + snapshot.difficulty(), x, y += step);
                if (snapshot.mode() == tetris.feature.game.GameMode.ITEM)
                    g.drawString("NEXT ITEM " + snapshot.nextItemLines(), x, y += step);
                drawEffects(g, snapshot.effects(), x, y, width, labelSize, guideTop);
            }
        }

        // Key Guide는 보드 아래쪽에 맞춰 붙인다
        int guideY = cellSize / 2 + cellSize * Board.HEIGHT - lineHeight * keyGuide.length;
        drawLabel(g, Messages.get("game.keys"), x, guideY - lineHeight, labelSize);
        g.setFont(Theme.text(labelSize, false));
        g.setColor(Theme.TEXT);
        for (String line : keyGuide) {
            guideY += lineHeight;
            g.drawString(line, x, guideY - labelSize / 3);
        }
    }

    private static final int GAUGE_SEGMENTS = 10; // 지속 효과의 최대 남은 블록 수

    /**
     * 지속 효과 섹션: 노란색으로 [이름  설명 ... N블록]과 그 아래 10칸 게이지를 그린다.
     * limitY(조작 안내 위쪽)를 넘으면 게이지는 빼고 글자 줄만 그려 안내를 덮지 않는다.
     */
    private void drawEffects(Graphics2D g, java.util.List<tetris.feature.item.ActiveEffect> effects, int x, int y,
                             int width, int labelSize, int limitY) {
        if (effects.isEmpty()) return;

        int step = labelSize + labelSize / 2;
        int barHeight = Math.max(3, labelSize / 3);
        int barGap = Math.max(2, labelSize / 4);
        int headerStep = labelSize + labelSize / 4; // 제목 줄은 여백을 줄여 큰 화면에서도 게이지가 들어가게 한다
        boolean gauge = y + headerStep + effects.size() * (step + barGap + barHeight) <= limitY;
        int indent = Math.max(6, labelSize * 2 / 3);
        int contentX = x + indent;
        int contentWidth = width - indent;
        int top = y;

        g.setFont(Theme.text(labelSize, true));
        g.setColor(Theme.DIM_TEXT);
        g.drawString(Messages.get("game.effects"), contentX, y += headerStep);

        g.setFont(Theme.text(labelSize, true));
        int tagWidth = 0;
        for (var effect : effects) tagWidth = Math.max(tagWidth, g.getFontMetrics().stringWidth(effectTag(effect)));
        for (var effect : effects) {
            y += step;
            g.setColor(Theme.ACCENT);
            g.setFont(Theme.text(labelSize, true));
            g.drawString(effectTag(effect), contentX, y);
            String remaining = remainingText(effect);
            g.drawString(remaining, x + width - g.getFontMetrics().stringWidth(remaining), y);
            g.setFont(Theme.text(labelSize, false));
            g.drawString(Messages.get("effect." + effect.type().name() + ".desc"), contentX + tagWidth + labelSize / 2, y);
            if (!gauge) continue;

            int gap = Math.max(1, labelSize / 6);
            int segment = (contentWidth - gap * (GAUGE_SEGMENTS - 1)) / GAUGE_SEGMENTS;
            int filled = Math.min(effect.remainingBlocks(), GAUGE_SEGMENTS);
            for (int i = 0; i < GAUGE_SEGMENTS; i++) {
                g.setColor(i < filled ? Theme.ACCENT : Theme.ACCENT.darker().darker());
                g.fillRect(contentX + i * (segment + gap), y + barGap, segment, barHeight);
            }
            y += barGap + barHeight;
        }

        // 섹션 왼쪽의 세로선으로 다른 정보와 구분한다
        g.setColor(Theme.ACCENT.darker().darker());
        int barTop = top + step / 3;
        g.fillRect(x, barTop, Math.max(2, labelSize / 5), y - barTop);
    }

    private static String effectTag(tetris.feature.item.ActiveEffect effect) {
        return Messages.get("effect." + effect.type().name() + ".tag");
    }

    // 남은 블록 수. 슬로우는 마지막 블록이 떨어지는 동안 0이라 "이번 블록"으로 보여준다
    private static String remainingText(tetris.feature.item.ActiveEffect effect) {
        return effect.remainingBlocks() == 0
                ? Messages.get("effect.thisBlock")
                : Messages.format("effect.blocks", effect.remainingBlocks());
    }

    // Next 블록을 값 있는 칸 기준으로 상자 가운데에 그린다
    private void drawNext(Graphics2D g, Block next, int boxX, int boxY, int boxWidth, int boxHeight) {
        int[][] shape = next.getShape();
        int minRow = Integer.MAX_VALUE, maxRow = -1, minCol = Integer.MAX_VALUE, maxCol = -1;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 0) continue;
                minRow = Math.min(minRow, r);
                maxRow = Math.max(maxRow, r);
                minCol = Math.min(minCol, c);
                maxCol = Math.max(maxCol, c);
            }
        }
        if (maxRow < 0) return;

        int startX = boxX + (boxWidth - (maxCol - minCol + 1) * cellSize) / 2;
        int startY = boxY + (boxHeight - (maxRow - minRow + 1) * cellSize) / 2;
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                if (shape[r][c] == 0) continue;
                drawCell(g, startX + (c - minCol) * cellSize, startY + (r - minRow) * cellSize, cellSize, next.getType(), next.itemCellAt(r, c).orElse(null));
            }
        }
    }

    /** 현재 블록·고정 블록·Next 공용: 색 + 문자를 항상 함께 그린다 (RND-4, RND-5) */
    private void drawCell(Graphics2D g, int x, int y, int size, BlockType type) {
        drawCell(g, x, y, size, type, null);
    }

    private void drawCell(Graphics2D g, int x, int y, int size, BlockType type, tetris.feature.item.ItemType item) {
        Color color = item == null ? palette.color(type, colorBlindMode) : Color.WHITE;
        g.setColor(color);
        g.fillRect(x, y, size, size);
        g.setColor(color.darker());
        g.drawRect(x, y, size - 1, size - 1);

        g.setFont(Theme.blockLetter(size * 3 / 5));
        g.setColor(item == null ? palette.textColor(color) : Color.BLACK);
        String letter = item != null ? String.valueOf(item.symbol()) : type == BlockType.W ? "W" : "O";
        int textX = x + (size - g.getFontMetrics().stringWidth(letter)) / 2;
        int textY = y + (size - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
        g.drawString(letter, textX, textY);
    }

    private static int drawLabel(Graphics2D g, String label, int x, int y, int size) {
        g.setFont(Theme.text(size, true));
        g.setColor(Theme.DIM_TEXT);
        g.drawString(label, x, y + size);
        return y + size + size / 2;
    }

    private static int drawValue(Graphics2D g, String label, String value, int x, int y, int labelSize, int valueSize) {
        y = drawLabel(g, label, x, y, labelSize);
        g.setFont(Theme.text(valueSize, true));
        g.setColor(Theme.TEXT);
        g.drawString(value, x, y + valueSize);
        return y + valueSize + labelSize;
    }
}
