package tetris.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tetris.component.block.TBlock;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameState;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.Settings;

/** Software input/EDT/render latency; does not claim native keyboard or minimum-hardware certification. */
class ChecklistInputLatencyTest {
    private static final long INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(334);
    private static final long MAX_LATENCY_NANOS = TimeUnit.SECONDS.toNanos(1);

    @ParameterizedTest
    @EnumSource(GameMode.class)
    @DisplayName("2-12/13, 6-5/10: 3Hz 이하 반복 키를 EDT·실제 바인딩·렌더링까지 1초 미만 처리")
    void repeatedInputsUpdateStateAndRenderWithinOneSecond(GameMode mode) throws Exception {
        // A fixed ordinary block avoids item generation/auto-clock hiding a lost input.
        Settings settings = new Settings();
        GamePanel[] panel = new GamePanel[1];
        GameSession[] session = new GameSession[1];
        int[] quitRequests = {0};
        long maxLatency = 0;
        try {
            for (GameAction command : GameAction.values()) {
                onEdt(() -> {
                    if (session[0] != null) session[0].end();
                    panel[0] = new GamePanel(settings, new PausePanel(() -> {}, () -> {}, () -> {}), s -> {});
                    panel[0].setCellSize(20);
                    panel[0].setSize(panel[0].getPreferredSize());
                    session[0] = new GameSession(mode, Difficulty.NORMAL, TBlock::new, new GameClock() {
                        public void start(int millis) {}
                        public void stop() {}
                    }, panel[0]);
                    new KeyBindingManager(() -> quitRequests[0]++)
                            .bindGameKeys(panel[0], settings.getKeyMap(), session[0]);
                    session[0].start();
                    for (int row = 0; row < 5; row++) session[0].moveDown();
                    // Warm up font/render setup before measuring the repeating-input requirement.
                    render(panel[0]);
                });
                long previousInput = 0;
                for (int repetition = 0; repetition < 3; repetition++) {
                    long wait = INTERVAL_NANOS - (System.nanoTime() - previousInput);
                    if (previousInput != 0 && wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
                    previousInput = System.nanoTime();
                    long submitted = previousInput;
                    int currentRepetition = repetition;
                    FutureTask<Long> event = new FutureTask<>(() -> {
                        var before = session[0].snapshot();
                        int quitsBefore = quitRequests[0];
                        int key = settings.getKeyMap().keyOf(command);
                        dispatch(panel[0], key, false);
                        dispatch(panel[0], key, true);
                        var after = session[0].snapshot();
                        switch (command) {
                            case MOVE_LEFT -> assertEquals(before.currentBlock().getCol() - 1, after.currentBlock().getCol());
                            case MOVE_RIGHT -> assertEquals(before.currentBlock().getCol() + 1, after.currentBlock().getCol());
                            case SOFT_DROP -> assertEquals(before.currentBlock().getRow() + 1, after.currentBlock().getRow());
                            case ROTATE -> assertEquals((before.currentBlock().getRotation() + 1) % 4, after.currentBlock().getRotation());
                            case HARD_DROP -> {
                                assertTrue(after.score() > before.score());
                                assertTrue(java.util.Arrays.stream(after.cells()).flatMapToInt(java.util.Arrays::stream)
                                        .filter(value -> value > 0).count() >= (currentRepetition + 1) * 4);
                            }
                            case PAUSE -> assertEquals(before.state() == GameState.PAUSED ? GameState.PLAYING : GameState.PAUSED, after.state());
                            case QUIT -> assertEquals(quitsBefore + 1, quitRequests[0]);
                        }
                        render(panel[0]);
                        return System.nanoTime() - submitted;
                    });
                    SwingUtilities.invokeLater(event);
                    long latency = event.get(1, TimeUnit.SECONDS);
                    assertTrue(latency < MAX_LATENCY_NANOS, mode + " " + command + " latency=" + latency);
                    maxLatency = Math.max(maxLatency, latency);
                }
            }
            System.out.printf("[checklist-input] mode=%s inputs=21 rate<=3Hz maxLatencyMs=%.3f%n", mode, maxLatency / 1_000_000.0);
        } finally {
            onEdt(() -> { if (session[0] != null) session[0].end(); });
        }
    }

    private static void dispatch(JComponent panel, int key, boolean release) {
        Object binding = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(key, 0, release));
        Action action = panel.getActionMap().get(binding);
        assertNotNull(action);
        assertTrue(action.isEnabled());
        action.actionPerformed(null);
    }

    private static void render(GamePanel panel) {
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { panel.doLayout(); panel.paint(graphics); }
        finally { graphics.dispose(); }
    }

    private static void onEdt(Runnable runnable) throws Exception {
        FutureTask<Void> task = new FutureTask<>(runnable, null);
        SwingUtilities.invokeLater(task);
        task.get(5, TimeUnit.SECONDS);
    }
}
