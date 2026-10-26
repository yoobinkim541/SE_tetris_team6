package tetris.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.rule.WeightedBlockGenerator;
import tetris.feature.save.JsonScoreboardRepository;
import tetris.feature.save.JsonSettingsRepository;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.Settings;

/** Opt-in, real-window wiring diagnostics. Run with audit.gui=true and headless=false. */
@EnabledIfSystemProperty(named = "audit.gui", matches = "true")
class ProductionWiringAuditTest {
    @TempDir Path directory;

    @Test
    void actualMainMenuContainsFiveEntries() throws Exception {
        withFrame(new Settings(), frame -> assertEquals(5, menuItems(frame).size(),
                "Production menu must expose classic, item, settings, scoreboard and exit"));
    }

    @Test
    void focusLossCallbackPausesRealGameAndClearsHeldRotate() throws Exception {
        withFrame(new Settings(), frame -> {
            menuItems(frame).get(0).action().run();
            GameSession game = session(frame);
            javax.swing.JComponent panel = (javax.swing.JComponent) field(frame, "gamePanel");
            // 보드 중앙에서 모든 일반 블록을 회전 가능하게 내려 둔다.
            for (int i = 0; i < 5; i++) game.moveDown();
            Object rotate = panel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0));
            panel.getActionMap().get(rotate).actionPerformed(null);
            int rotation = game.snapshot().currentBlock().getRotation();
            var event = new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_DEACTIVATED);
            for (var listener : frame.getWindowListeners()) listener.windowDeactivated(event);
            assertEquals(GameState.PAUSED, game.getState());
            game.resume();
            panel.getActionMap().get(rotate).actionPerformed(null);
            // O는 모양이 그대로여도 회전 카운터는 순환한다.
            assertEquals((rotation + 1) % 4, game.snapshot().currentBlock().getRotation());
        });
    }

    @Test
    void allConfiguredSizesFitCurrentScaledDesktop() throws Exception {
        withFrame(new Settings(), frame -> {
            java.awt.Rectangle desktop = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int previousHeight = 0;
            for (var size : tetris.feature.setting.ScreenSize.values()) {
                frame.applyScreenSize(size);
                assertTrue(frame.getWidth() <= desktop.width, size + " width exceeds desktop");
                assertTrue(frame.getHeight() <= desktop.height, size + " height exceeds desktop");
                assertTrue(frame.getHeight() > previousHeight, "세 크기를 구분해야 함");
                previousHeight = frame.getHeight();
            }
        });
    }

    @Test
    void actualItemMenuActionStartsItemAtNormalDifficulty() throws Exception {
        Settings settings = new Settings();
        settings.setDifficulty(Difficulty.HARD);
        withFrame(settings, frame -> {
            MenuItem item = menuItems(frame).stream()
                    .filter(entry -> entry.label().contains("아이템")
                            || entry.label().toUpperCase(Locale.ROOT).contains("ITEM"))
                    .findFirst().orElseThrow(() -> new AssertionError("Production menu has no item action"));
            item.action().run();
            GameSnapshot snapshot = session(frame).snapshot();
            assertAll(() -> assertEquals(MainFrame.Screen.GAME, field(frame, "currentScreen")),
                    () -> assertEquals(GameState.PLAYING, snapshot.state()),
                    () -> assertEquals(GameMode.ITEM, snapshot.mode()),
                    () -> assertEquals(Difficulty.NORMAL, snapshot.difficulty()));
        });
    }

    @Test
    void classicMenuRespectsEasyAndUsesWeightedGenerator() throws Exception {
        assertClassicDifficulty(Difficulty.EASY);
    }

    @Test
    void classicMenuRespectsHardAndUsesWeightedGenerator() throws Exception {
        assertClassicDifficulty(Difficulty.HARD);
    }

    private void assertClassicDifficulty(Difficulty difficulty) throws Exception {
        Settings settings = new Settings();
        settings.setDifficulty(difficulty);
        withFrame(settings, frame -> {
            // First entry is the actual production classic action, not a test-created menu.
            menuItems(frame).get(0).action().run();
            GameSession session = session(frame);
            assertAll(() -> assertEquals(GameState.PLAYING, session.snapshot().state()),
                    () -> assertEquals(GameMode.CLASSIC, session.snapshot().mode()),
                    () -> assertEquals(difficulty, session.snapshot().difficulty()),
                    () -> assertInstanceOf(WeightedBlockGenerator.class, field(session, "generator"),
                            "Production classic game must use the weighted generator"));
        });
    }

    @Test
    void submittingItemResultPreservesItsCategoryInMemoryAndOnDisk() throws Exception {
        withFrame(new Settings(), frame -> {
            GameSnapshot result = new GameSnapshot(new int[20][10], null, null, 1234L, 2, 3,
                    GameState.GAME_OVER, GameMode.ITEM, Difficulty.NORMAL, List.of(), 7, List.of());
            Field finalResult = MainFrame.class.getDeclaredField("finalResult");
            finalResult.setAccessible(true);
            finalResult.set(frame, result);
            Method submit = MainFrame.class.getDeclaredMethod("submitName", String.class);
            submit.setAccessible(true);
            submit.invoke(frame, "Audit");
            Scoreboard inMemory = (Scoreboard) field(frame, "scoreboard");
            Scoreboard reloaded = new JsonScoreboardRepository(directory.resolve("scoreboard.json"))
                    .load().value();
            assertAll(() -> assertEquals(GameMode.ITEM, inMemory.entries().get(0).mode()),
                    () -> assertEquals(GameMode.ITEM, reloaded.entries().get(0).mode()));
        });
    }

    private void withFrame(Settings settings, FrameCheck check) throws Exception {
        Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = null;
            try {
                frame = new MainFrame(settings, new Scoreboard(List.of()),
                        new JsonSettingsRepository(directory.resolve("settings.json")),
                        new JsonScoreboardRepository(directory.resolve("scoreboard.json")));
                check.run(frame);
            } catch (Throwable thrown) {
                failure[0] = thrown;
            } finally {
                if (frame != null) {
                    try { session(frame).end(); }
                    catch (Throwable cleanup) {
                        if (failure[0] == null) failure[0] = cleanup;
                        else failure[0].addSuppressed(cleanup);
                    } finally { frame.dispose(); }
                }
            }
        });
        if (failure[0] instanceof Error error) throw error;
        if (failure[0] instanceof Exception exception) throw exception;
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    @SuppressWarnings("unchecked")
    private static List<MenuItem> menuItems(MainFrame frame) throws Exception {
        return (List<MenuItem>) field(field(frame, "mainMenuPanel"), "items");
    }

    private static GameSession session(MainFrame frame) throws Exception {
        return (GameSession) field(frame, "session");
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    @FunctionalInterface private interface FrameCheck { void run(MainFrame frame) throws Exception; }
}
