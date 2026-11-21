package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.game.GameState;
import tetris.feature.save.JsonScoreboardRepository;
import tetris.feature.save.JsonSettingsRepository;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.Settings;
import tetris.feature.setting.ScreenSize;

/**
 * F11과 설정 전체 화면이 현재 모니터 논리 좌표를 덮고, 전환이 게임을 일시정지하지 않는다.
 */
@Timeout(40)
class FullscreenFrameTest {
    @TempDir Path directory;

    @Test
    void f11CoversTheMonitorAndDoesNotPause() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        java.util.concurrent.atomic.AtomicReference<MainFrame> holder = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> holder.set(frame(new Settings())));
        MainFrame frame = holder.get();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.setVisible(true);
                frame.showScreen(MainFrame.Screen.MAIN_MENU);
            });
            press(frame, KeyEvent.VK_ENTER);
            press(frame, KeyEvent.VK_ENTER);
            awaitGame(frame);
            SwingUtilities.invokeAndWait(() -> {
                frame.toFront();
                frame.requestFocus();
                java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new KeyEvent(
                        frame, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_F11, KeyEvent.CHAR_UNDEFINED));
            });
            Thread.sleep(500);
            assertTrue(frame.isFullscreen(), "F11이 전체 화면 플래그를 못 올림");
            assertTrue(frame.isUndecorated(), "전체 화면인데 테두리가 남음");
            Rectangle monitor = frame.getGraphicsConfiguration().getBounds();
            assertEquals(monitor, frame.getBounds(), "창이 모니터 논리 좌표와 다름");
            java.util.concurrent.atomic.AtomicReference<java.awt.Component> focus = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<GameState> state = new java.util.concurrent.atomic.AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                focus.set(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
                try {
                    state.set(gameState(frame));
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
            assertNotNull(focus.get(), "전환 뒤 포커스가 없음");
            assertFalse(state.get() == GameState.PAUSED, "전체 화면 전환이 게임을 멈춤");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    @Test
    void savedFullscreenAppliesWhenTheWindowOpens() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        Settings settings = new Settings();
        settings.setScreenSize(ScreenSize.SMALL);
        settings.setFullscreen(true);
        MainFrame frame = frame(settings);
        try {
            SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
            Thread.sleep(200);
            assertTrue(frame.isFullscreen());
            assertTrue(frame.isUndecorated());
            assertEquals(frame.getGraphicsConfiguration().getBounds(), frame.getBounds());
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    private MainFrame frame(Settings settings) {
        return new MainFrame(settings, new Scoreboard(java.util.List.of()),
                new JsonSettingsRepository(directory.resolve("settings.json")),
                new JsonScoreboardRepository(directory.resolve("scores.json")));
    }

    private static void press(MainFrame frame, int code) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focus == null) focus = frame.getRootPane();
            long when = System.currentTimeMillis();
            focus.dispatchEvent(new KeyEvent(focus, KeyEvent.KEY_PRESSED, when, 0, code, KeyEvent.CHAR_UNDEFINED));
            focus.dispatchEvent(new KeyEvent(focus, KeyEvent.KEY_RELEASED, when, 0, code, KeyEvent.CHAR_UNDEFINED));
        });
        Thread.sleep(200);
    }

    private static void awaitGame(MainFrame frame) throws Exception {
        long deadline = System.currentTimeMillis() + 4_000;
        while (System.currentTimeMillis() < deadline) {
            if (gameState(frame) == GameState.PLAYING) return;
            Thread.sleep(40);
        }
        throw new AssertionError("게임이 시작되지 않음");
    }

    private static GameState gameState(MainFrame frame) throws Exception {
        var field = MainFrame.class.getDeclaredField("session");
        field.setAccessible(true);
        return ((tetris.feature.game.GameSession) field.get(frame)).getState();
    }
}
