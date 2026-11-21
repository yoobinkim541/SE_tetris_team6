package tetris.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;
import tetris.ui.GameOverPanel;
import tetris.ui.GamePanel;
import tetris.ui.MainMenuPanel;
import tetris.ui.MenuItem;
import tetris.ui.NameInputPanel;
import tetris.ui.PausePanel;
import tetris.ui.ScoreBoardPanel;
import tetris.ui.SettingPanel;
import tetris.ui.fx.AssetStore;

/** 전체 화면 배치·마우스 클릭 뒤에도 방향키와 Enter가 동작한다 */
@Timeout(60)
class WindowMouseTest {
    private static final Path CAPTURES = Path.of("C:/Users/asus/Desktop/SE-tetris/assets-work/window");

    @Test
    void portraitIsCenteredOnWidescreenWithoutBlackBars() throws Exception {
        Dimension logical = GamePanel.preferredSizeFor(ScreenSize.MEDIUM.cellPixels());
        assertSideMargins(1920, 1080, logical);
        assertSideMargins(2560, 1440, logical);
        capture("menu-1920x1080", 1920, 1080, logical);
        capture("menu-2560x1440", 2560, 1440, logical);
    }

    @Test
    void fullscreenKeysIgnoreEscape() {
        assertTrue(FullscreenKeys.isToggle(key(KeyEvent.VK_F11, 0)));
        assertTrue(FullscreenKeys.isToggle(key(KeyEvent.VK_ENTER, KeyEvent.ALT_DOWN_MASK)));
        assertFalse(FullscreenKeys.isToggle(key(KeyEvent.VK_ESCAPE, 0)));
        assertFalse(FullscreenKeys.isToggle(key(KeyEvent.VK_ENTER, 0)));
        assertFalse(FullscreenKeys.isToggle(key(KeyEvent.VK_F11, 0, KeyEvent.KEY_RELEASED)));
    }

    @Test
    void clickThenArrowAndEnterStillWork() {
        List<String> calls = new ArrayList<>();
        MainMenuPanel menu = menu(calls);
        paint(menu);
        Point hit = find(menu, "1");
        click(menu, hit.x, hit.y);
        assertEquals(List.of("1"), calls);

        press(menu, KeyEvent.VK_DOWN);
        press(menu, KeyEvent.VK_ENTER);
        assertEquals(List.of("1", "2"), calls);
        assertTrue(menu.isFocusable());
    }

    @Test
    void settingsArrowsAndTogglesKeepKeyboard() {
        Settings settings = new Settings();
        SettingPanel panel = new SettingPanel(settings, () -> true, () -> true, () -> { });
        paint(panel);
        Point inc = find(panel, "inc:0");
        click(panel, inc.x, inc.y);
        assertEquals(ScreenSize.LARGE, settings.getScreenSize());
        press(panel, KeyEvent.VK_RIGHT);
        assertTrue(settings.isFullscreen());
        press(panel, KeyEvent.VK_DOWN);
        press(panel, KeyEvent.VK_ENTER);
        assertEquals(tetris.feature.game.Difficulty.HARD, settings.getDifficulty());
    }

    @Test
    void scoreboardArrowsAndCloseKeepKeyboard() {
        List<String> calls = new ArrayList<>();
        ScoreBoardPanel board = new ScoreBoardPanel(() -> calls.add("menu"), () -> calls.add("exit"));
        board.show(List.of(), -1);
        paint(board);
        Point next = find(board, "next");
        click(board, next.x, next.y);
        press(board, KeyEvent.VK_LEFT);
        Point close = find(board, "close");
        click(board, close.x, close.y);
        assertEquals(List.of("menu"), calls);
    }

    @Test
    void gameOverClickThenEnterStillBound() {
        List<String> calls = new ArrayList<>();
        GameOverPanel panel = new GameOverPanel(() -> calls.add("next"));
        paint(panel);
        Point box = find(panel, "continue");
        click(panel, box.x, box.y);
        assertEquals(List.of("next"), calls);
        press(panel, KeyEvent.VK_ENTER);
        assertEquals(List.of("next", "next"), calls);
    }

    @Test
    void pauseClickThenArrowAndEnter() {
        List<String> calls = new ArrayList<>();
        PausePanel panel = new PausePanel(() -> calls.add("resume"), () -> calls.add("restart"), () -> calls.add("quit"));
        paint(panel);
        Point restart = find(panel, "1");
        click(panel, restart.x, restart.y);
        assertEquals(List.of("restart"), calls);
        press(panel, KeyEvent.VK_DOWN);
        press(panel, KeyEvent.VK_ENTER);
        assertEquals(List.of("restart", "quit"), calls);
    }
    @Test
    void nameFieldClickFocusesAndSaveSubmits() {
        List<String> names = new ArrayList<>();
        NameInputPanel panel = new NameInputPanel(names::add);
        panel.prepare(1200);
        panel.setSize(640, 480);
        panel.doLayout();
        MouseEvent press = mouse(panel.nameField(), MouseEvent.MOUSE_PRESSED, 20, 20);
        panel.nameField().dispatchEvent(press);
        panel.nameField().setText("테스터");
        panel.clickSave();
        assertEquals(List.of("테스터"), names);
    }

    @Test
    void stageClickUsesInverseOfPaintTransform() {
        List<String> calls = new ArrayList<>();
        JPanel deck = new JPanel(new java.awt.CardLayout());
        MainMenuPanel menu = menu(calls);
        deck.add(menu, "MENU");
        ((java.awt.CardLayout) deck.getLayout()).show(deck, "MENU");
        Stage stage = new Stage(deck);
        Dimension logical = GamePanel.preferredSizeFor(ScreenSize.MEDIUM.cellPixels());
        stage.setLogicalSize(logical.width, logical.height);
        stage.setBackdrop(AssetStore.BG_MENU);
        stage.setSize(1920, 1080);
        stage.doLayout();
        paint(stage);
        Point logicalHit = find(menu, "0");
        Fit fit = stage.fit();
        Point2D.Double screen = new Point2D.Double();
        fit.transform().transform(new Point2D.Double(logicalHit.x + 4, logicalHit.y + 4), screen);
        click(stage, (int) screen.x, (int) screen.y);
        assertEquals(List.of("0"), calls);
        press(menu, KeyEvent.VK_DOWN);
        press(menu, KeyEvent.VK_ENTER);
        assertEquals(List.of("0", "1"), calls);
    }

    private static void assertSideMargins(int screenW, int screenH, Dimension logical) {
        Fit fit = Fit.contain(screenW, screenH, logical.width, logical.height);
        assertTrue(fit.hasSideMargin(), screenW + "x" + screenH);
        assertEquals(screenH, logical.height * fit.scale(), 1.5);
        assertTrue(fit.offsetY() < 1.5);
        Point2D.Double side = fit.toPaint(4, screenH / 2.0);
        assertTrue(side.x < 0, "side click is outside the portrait");
        Point2D.Double center = fit.toPaint(screenW / 2.0, screenH / 2.0);
        assertTrue(center.x >= 0 && center.x < logical.width);
    }

    private static void capture(String name, int width, int height, Dimension logical) throws Exception {
        JPanel deck = new JPanel(new java.awt.CardLayout());
        MainMenuPanel menu = menu(new ArrayList<>());
        deck.add(menu, "MENU");
        ((java.awt.CardLayout) deck.getLayout()).show(deck, "MENU");
        Stage stage = new Stage(deck);
        stage.setLogicalSize(logical.width, logical.height);
        stage.setBackdrop(AssetStore.BG_MENU);
        stage.setSize(width, height);
        stage.doLayout();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        stage.paint(g);
        g.dispose();
        int side = image.getRGB(6, height / 2) & 0xFFFFFF;
        int center = image.getRGB(width / 2, height / 2) & 0xFFFFFF;
        assertNotEquals(0, side, name + " side is black");
        assertNotEquals(0, center, name + " center is black");
        Files.createDirectories(CAPTURES);
        ImageIO.write(image, "png", CAPTURES.resolve(name + ".png").toFile());
    }

    private static MainMenuPanel menu(List<String> calls) {
        return new MainMenuPanel(List.of(
                new MenuItem("하나", () -> calls.add("0")),
                new MenuItem("둘", () -> calls.add("1")),
                new MenuItem("셋", () -> calls.add("2"))));
    }

    private static void paint(JComponent panel) {
        Dimension size = GamePanel.preferredSizeFor(ScreenSize.MEDIUM.cellPixels());
        panel.setPreferredSize(size);
        panel.setSize(size);
        panel.doLayout();
        BufferedImage image = new BufferedImage(Math.max(1, panel.getWidth()), Math.max(1, panel.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();
    }

    private static Point find(tetris.ui.MainMenuPanel panel, String id) {
        return find((x, y) -> panel.hitAt(x, y), panel.getWidth(), panel.getHeight(), id);
    }

    private static Point find(SettingPanel panel, String id) {
        return find(panel::hitAt, panel.getWidth(), panel.getHeight(), id);
    }

    private static Point find(ScoreBoardPanel panel, String id) {
        return find(panel::hitAt, panel.getWidth(), panel.getHeight(), id);
    }

    private static Point find(GameOverPanel panel, String id) {
        return find(panel::hitAt, panel.getWidth(), panel.getHeight(), id);
    }

    private static Point find(PausePanel panel, String id) {
        return find(panel::hitAt, panel.getWidth(), panel.getHeight(), id);
    }

    private static Point find(HitProbe probe, int width, int height, String id) {
        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                if (id.equals(probe.hit(x, y))) return new Point(x, y);
            }
        }
        throw new AssertionError("no hit " + id + " in " + width + "x" + height);
    }

    private static void click(JComponent panel, int x, int y) {
        panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_MOVED, x, y));
        panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
    }

    private static MouseEvent mouse(JComponent panel, int id, int x, int y) {
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private static void press(JComponent panel, int keyCode) {
        Object name = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(keyCode, 0));
        assertNotNull(name, "missing key " + keyCode);
        panel.getActionMap().get(name).actionPerformed(null);
    }

    private static KeyEvent key(int code, int modifiers) {
        return key(code, modifiers, KeyEvent.KEY_PRESSED);
    }

    private static KeyEvent key(int code, int modifiers, int id) {
        return new KeyEvent(new JPanel(), id, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }

    private interface HitProbe {
        String hit(int x, int y);
    }

    private record Point(int x, int y) {
    }
}
