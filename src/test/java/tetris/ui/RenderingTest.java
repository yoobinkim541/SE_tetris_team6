package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.feature.data.Board;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.score.ScoreEntry;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.Settings;

/**
 * 화면 없이(headless) BufferedImage에 그려 렌더링 규칙을 픽셀로 확인한다.
 * RND-4(종류별 색으로 그림)·RND-6(색맹 팔레트)·Pause 오버레이·신규 기록 강조·Next 표시, 그 외 화면은 예외 없이 그려지는지.
 */
public class RenderingTest {
    private static final int CELL = 30;
    private static final int PADDING = CELL / 2;
    private final BlockPalette palette = new BlockPalette();
    private final List<String> calls = new ArrayList<>();

    // ── 도우미 ───────────────────────────────
    private static BufferedImage render(JComponent component, Dimension size) {
        component.setSize(size);
        layoutTree(component);
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        component.paint(g);
        g.dispose();
        return image;
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    private static Color pixel(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, y));
    }

    /** 보드 칸 (row, col)의 왼쪽 위 모서리 근처 픽셀. 가운데의 글자와 1px 테두리를 피한다 */
    private static Color boardCell(BufferedImage image, int row, int col) {
        return pixel(image, PADDING + col * CELL + 4, PADDING + row * CELL + 4);
    }

    private static boolean contains(BufferedImage image, Color color, int x0, int y0, int x1, int y1) {
        int rgb = color.getRGB();
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (image.getRGB(x, y) == rgb) return true;
            }
        }
        return false;
    }

    private static boolean contains(BufferedImage image, Color color) {
        return contains(image, color, 0, 0, image.getWidth(), image.getHeight());
    }

    private static long countNot(BufferedImage image, Color background) {
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != background.getRGB()) count++;
            }
        }
        return count;
    }

    private static Dimension screen() {
        return GamePanel.preferredSizeFor(CELL);
    }

    private GamePanel gamePanel(Settings settings) {
        PausePanel pause = new PausePanel(() -> calls.add("resume"), () -> calls.add("restart"), () -> calls.add("quit"));
        GamePanel panel = new GamePanel(settings, pause, snapshot -> calls.add("gameOver"));
        panel.setCellSize(CELL);
        return panel;
    }

    private static GameSnapshot snapshot(int[][] cells, Block current, Block next, GameState state) {
        return new GameSnapshot(cells, current, next, 1234, 3, 12, state);
    }

    private static int[][] emptyCells() {
        return new int[Board.HEIGHT][Board.WIDTH];
    }

    // ── GamePanel ────────────────────────────
    @Test
    void lockedCellsAreDrawnInTheirBlockColor() { // RND-4
        int[][] cells = emptyCells();
        cells[19][0] = BlockType.I.cellValue();
        cells[19][9] = BlockType.Z.cellValue();
        GamePanel panel = gamePanel(new Settings());
        panel.onChanged(snapshot(cells, null, null, GameState.PLAYING));

        BufferedImage image = render(panel, screen());
        assertEquals(palette.color(BlockType.I, false), boardCell(image, 19, 0));
        assertEquals(palette.color(BlockType.Z, false), boardCell(image, 19, 9));
        assertEquals(Theme.PANEL, boardCell(image, 0, 0));
    }

    @Test
    void colorBlindModeUsesTheColorBlindPalette() { // RND-6
        int[][] cells = emptyCells();
        cells[19][4] = BlockType.L.cellValue();
        Settings settings = new Settings();
        settings.setColorBlindMode(true);
        GamePanel panel = gamePanel(settings);
        panel.onChanged(snapshot(cells, null, null, GameState.PLAYING));

        Color drawn = boardCell(render(panel, screen()), 19, 4);
        assertEquals(palette.color(BlockType.L, true), drawn);
        assertNotEquals(palette.color(BlockType.L, false), drawn);
    }

    @Test
    void currentAndNextBlocksAreDrawnButHiddenRowsAreNot() { // RND-4, G-4
        Block current = new TBlock();
        current.setPosition(5, 3); // T 0행: (5,4)
        Block hidden = new OBlock();
        hidden.setPosition(-2, 0); // 숨은 줄에만 있으면 그리지 않는다
        GamePanel panel = gamePanel(new Settings());
        panel.onChanged(snapshot(emptyCells(), current, new OBlock(), GameState.PLAYING));
        BufferedImage image = render(panel, screen());

        assertEquals(palette.color(BlockType.T, false), boardCell(image, 5, 4));
        int infoX = PADDING * 2 + CELL * Board.WIDTH;
        assertTrue(contains(image, palette.color(BlockType.O, false), infoX, 0, image.getWidth(), CELL * 6),
                "Next 상자에 O 블록 색이 있어야 한다");

        panel.onChanged(snapshot(emptyCells(), hidden, null, GameState.PLAYING));
        BufferedImage hiddenImage = render(panel, screen());
        assertFalse(contains(hiddenImage, palette.color(BlockType.O, false)), "숨은 줄의 칸은 그리지 않는다");
    }

    @Test
    void pauseShowsOverlayAndConfirmReplacesIt() { // §9 Pause, CNF-1
        int[][] cells = emptyCells();
        cells[10][5] = BlockType.S.cellValue();
        GamePanel panel = gamePanel(new Settings());
        panel.onChanged(snapshot(cells, null, null, GameState.PAUSED));
        BufferedImage paused = render(panel, screen());
        assertNotEquals(palette.color(BlockType.S, false), boardCell(paused, 10, 5), "Pause 오버레이가 보드를 덮는다");

        panel.showConfirm(new ConfirmDialog("정말?\n두 줄", () -> calls.add("yes"), () -> calls.add("no")));
        assertTrue(panel.isConfirmShowing());
        render(panel, screen());
        panel.closeConfirm();
        assertFalse(panel.isConfirmShowing());

        panel.showConfirm(new ConfirmDialog("?", () -> { }, () -> { }));
        panel.onChanged(snapshot(cells, null, null, GameState.PLAYING)); // 게임이 다시 돌면 확인창도 닫힌다
        assertFalse(panel.isConfirmShowing());
        assertEquals(palette.color(BlockType.S, false), boardCell(render(panel, screen()), 10, 5));
    }

    @Test
    void gameOverIsForwardedToScreenFlow() {
        GamePanel panel = gamePanel(new Settings());
        panel.onGameOver(snapshot(emptyCells(), null, null, GameState.GAME_OVER));
        assertEquals(List.of("gameOver"), calls);
        render(panel, screen());
    }

    @Test
    void keyGuideFollowsRebindingAndEmptyBoardDrawsBeforeFirstSnapshot() {
        Settings settings = new Settings();
        settings.getKeyMap().assign(GameAction.HARD_DROP, java.awt.event.KeyEvent.VK_X);
        GamePanel panel = gamePanel(settings);
        panel.applySettings(settings);
        BufferedImage image = render(panel, screen()); // snapshot 없이도 그려진다
        assertEquals(Theme.PANEL, boardCell(image, 10, 5));
    }

    // ── 다른 화면 ─────────────────────────────
    @Test
    void scoreboardHighlightsOnlyTheNewRecord() { // §9 Scoreboard, SBD
        ScoreBoardPanel board = new ScoreBoardPanel(() -> calls.add("menu"), () -> calls.add("exit"));
        List<ScoreEntry> entries = List.of(
                new ScoreEntry("홍길동", 900, 4, 20, Instant.EPOCH),
                new ScoreEntry("Kim", 500, 2, 8, Instant.EPOCH));

        board.show(entries, 1, true);
        board.showNotice("저장 실패");
        assertTrue(contains(render(board, screen()), Theme.HIGHLIGHT_ROW));

        board.show(entries, -1);
        assertFalse(contains(render(board, screen()), Theme.HIGHLIGHT_ROW));

        board.show(List.of(), -1);
        assertTrue(countNot(render(board, screen()), Theme.BACKGROUND) > 0, "기록 없음 화면도 그려진다");
    }

    @Test
    void menuGameOverAndSettingScreensRender() {
        MainMenuPanel menu = new MainMenuPanel(List.of(new MenuItem("a", () -> { }), new MenuItem("b", () -> { })));
        menu.showWarnings(List.of("설정 파일이 손상되어 기본 설정으로 시작합니다."));
        assertTrue(countNot(render(menu, screen()), Theme.BACKGROUND) > 0);

        GameOverPanel gameOver = new GameOverPanel(() -> calls.add("continue"));
        assertTrue(countNot(render(gameOver, screen()), Theme.BACKGROUND) > 0); // 결과 전
        gameOver.show(snapshot(emptyCells(), null, null, GameState.GAME_OVER));
        assertTrue(countNot(render(gameOver, screen()), Theme.BACKGROUND) > 0);

        Settings settings = new Settings();
        SettingPanel setting = new SettingPanel(settings, () -> true, () -> false, () -> calls.add("back"));
        render(setting, screen());
        setting.moveSelection(3);           // Reset Scoreboard → 확인창
        setting.activateSelected();
        render(setting, screen());
        setting.reset();
        setting.openControlKeys();
        setting.beginKeyCapture(GameAction.ROTATE);
        render(setting, screen());
        setting.cancelKeyCapture();
        assertFalse(setting.isCapturing());
    }

    @Test
    void nameInputSubmitsOnlyValidTrimmedNames() { // NAM-3
        List<String> submitted = new ArrayList<>();
        NameInputPanel panel = new NameInputPanel(submitted::add);
        panel.prepare(4321);
        JTextField field = findTextField(panel);

        field.setText("   ");
        field.postActionEvent();
        assertTrue(submitted.isEmpty(), "공백만 있는 이름은 저장하지 않는다");

        field.setText(" 홍길동 ");
        field.postActionEvent();
        assertEquals(List.of("홍길동"), submitted);

        panel.prepare(1);
        assertEquals("", field.getText());
        render(panel, screen());
    }

    private static JTextField findTextField(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTextField field) return field;
            if (child instanceof Container nested) {
                JTextField found = findTextField(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void swingClockTicksAfterTheIntervalAndStops() throws InterruptedException { // TMR-1, TMR-3
        CountDownLatch ticked = new CountDownLatch(1);
        SwingGameClock clock = new SwingGameClock(ticked::countDown);
        clock.start(20);
        assertTrue(ticked.await(2, TimeUnit.SECONDS), "간격 뒤에 tick이 와야 한다");
        clock.stop();
    }
}
