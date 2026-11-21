package tetris.play;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import tetris.component.block.Block;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.raid.LocalRaidClient;
import tetris.feature.raid.api.RaidConfig;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.save.ErrorLog;
import tetris.feature.save.JsonScoreboardRepository;
import tetris.feature.save.JsonSettingsRepository;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.ControlStyle;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.BattleMode;
import tetris.ui.MainFrame;
import tetris.ui.raid.RaidPanel;

/**
 * 실제 MainFrame을 키·마우스로 돌린다. 긴 시나리오는 {@code -Dtetris.play.long=true}.
 * 헤드리스에서는 창 시나리오를 건너뛴다.
 */
public final class PlayHarness {
    public static final String LONG = "tetris.play.long";
    private static final long RAID_SEED = 7L;

    private final List<String> failures = new ArrayList<>();
    private final List<Throwable> errors = new CopyOnWriteArrayList<>();
    private final PaintWatch paint = new PaintWatch();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private PrintStream previousErr;
    private MainFrame frame;
    private Path directory;

    public static void main(String[] args) throws Exception {
        System.setProperty(LONG, "true");
        ErrorLog.install();
        PlayHarness harness = new PlayHarness();
        int failed = harness.runAll();
        System.out.println(harness.report());
        if (failed > 0) System.exit(1);
    }

    public int runAll() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            failures.add("headless: MainFrame 시나리오를 건너뜀");
            return 0;
        }
        installWatch();
        try {
            open();
            singleGravityPauseAndQuit();
            itemBrief();
            localVersusGravity();
            raidSoloTwoKills();
            settingsThenPlay();
            fullscreenDoesNotStall();
            reenterDoesNotLeakTimers();
            if (Boolean.getBoolean(LONG)) {
                raidSoloThreeKills();
                coopThroughBoss();
                raceSeveralKills();
                onlineLoopback();
            }
        } catch (Throwable failure) {
            failures.add("harness: " + failure);
            failure.printStackTrace();
        } finally {
            closeFrame();
            restoreWatch();
        }
        return failures.size();
    }

    public String report() {
        StringBuilder text = new StringBuilder();
        text.append("failures=").append(failures.size()).append('\n');
        for (String failure : failures) text.append("FAIL ").append(failure).append('\n');
        text.append("exceptions=").append(errors.size()).append('\n');
        for (Throwable error : errors) text.append(error).append('\n');
        text.append("stderr=").append(errBytes.size()).append("B\n");
        text.append("paintMs=").append(String.format("%.2f", paint.averageMillis())).append('\n');
        text.append(census());
        return text.toString();
    }

    public List<String> failures() {
        return List.copyOf(failures);
    }

    private void open() throws Exception {
        directory = Files.createTempDirectory("play-harness");
        SwingUtilities.invokeAndWait(() -> {
            frame = new MainFrame(new Settings(), new Scoreboard(List.of()),
                    new JsonSettingsRepository(directory.resolve("settings.json")),
                    new JsonScoreboardRepository(directory.resolve("scores.json")),
                    tetris.ui.versus.LocalBattleClient::new,
                    () -> MainFrame.socketLobby(new tetris.feature.save.JsonRecentPeerRepository(directory.resolve("recent.json"))),
                    config -> new LocalRaidClient(new RaidConfig(config.mode(), RAID_SEED, config.controlStyle())));
            frame.setLocation(40, 40);
            frame.setVisible(true);
            frame.showScreen(MainFrame.Screen.MAIN_MENU);
        });
        flush();
    }

    private void singleGravityPauseAndQuit() throws Exception {
        begin();
        choose(0, 0);
        await("single playing", () -> screen() == MainFrame.Screen.GAME && game().getState() == GameState.PLAYING);
        assertAdvances("single", this::game);
        key(KeyEvent.VK_P);
        await("paused", () -> game().getState() == GameState.PAUSED);
        key(KeyEvent.VK_P);
        await("resumed", () -> game().getState() == GameState.PLAYING);
        quitToMenu();
    }

    private void itemBrief() throws Exception {
        begin();
        if (broken()) return;
        choose(1);
        await("item", () -> screen() == MainFrame.Screen.GAME);
        key(KeyEvent.VK_SPACE);
        sleep(200);
        quitToMenu();
    }

    private void localVersusGravity() throws Exception {
        begin();
        if (broken()) return;
        choose(2, 0);
        await("versus", () -> screen() == MainFrame.Screen.BATTLE && battleSession(0).getState() == GameState.PLAYING);
        if (broken()) return;
        assertAdvances("versus", () -> battleSession(0));
        leaveVersus();
    }

    private void leaveVersus() throws Exception {
        SwingUtilities.invokeLater(() -> dispatch(KeyEvent.VK_ESCAPE));
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline && !dialogOpen()) sleep(40);
        SwingUtilities.invokeLater(() -> {
            Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focus == null) return;
            long when = System.currentTimeMillis();
            focus.dispatchEvent(new KeyEvent(focus, KeyEvent.KEY_PRESSED, when, 0, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
            focus.dispatchEvent(new KeyEvent(focus, KeyEvent.KEY_RELEASED, when, 0, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
            focus.dispatchEvent(new KeyEvent(focus, KeyEvent.KEY_PRESSED, when, 0, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
            focus.dispatchEvent(new KeyEvent(focus, KeyEvent.KEY_RELEASED, when, 0, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
            focus.dispatchEvent(new KeyEvent(focus, KeyEvent.KEY_PRESSED, when, 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED));
            focus.dispatchEvent(new KeyEvent(focus, KeyEvent.KEY_RELEASED, when, 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED));
        });
        await("versus menu", () -> screen() == MainFrame.Screen.BATTLE_MODES || screen() == MainFrame.Screen.MAIN_MENU);
        if (screen() == MainFrame.Screen.BATTLE_MODES) {
            key(KeyEvent.VK_ESCAPE);
            await("main from versus", () -> screen() == MainFrame.Screen.MAIN_MENU);
        }
    }

    private boolean dialogOpen() {
        for (java.awt.Window window : java.awt.Window.getWindows()) {
            if (window.isShowing() && window != frame) return true;
        }
        return false;
    }

    private void raidSoloTwoKills() throws Exception {
        begin();
        if (broken()) return;
        choose(0, 1);
        await("raid countdown", () -> raid() != null);
        await("raid playing", () -> raid().phase() == RaidPhase.PLAYING);
        if (broken()) return;
        assertAdvances("raid", this::raidSession);
        int kills = raid().kills();
        playUntilKills(kills + 1, 12_000);
        await("card", () -> raid().phase() == RaidPhase.CARD_PICK);
        key(KeyEvent.VK_SPACE);
        await("after key pick", () -> raid().phase() == RaidPhase.PLAYING && raid().kills() >= kills + 1);
        assertBossVisible("after-key-pick");
        playUntilKills(raid().kills() + 1, 12_000);
        await("card mouse", () -> raid().phase() == RaidPhase.CARD_PICK);
        clickCard();
        await("after mouse pick", () -> raid().phase() == RaidPhase.PLAYING);
        assertBossVisible("after-mouse-pick");
        quitRaid();
    }

    private void raidSoloThreeKills() throws Exception {
        if (broken()) return;
        choose(0, 1);
        await("raid3", () -> raid() != null && raid().phase() == RaidPhase.PLAYING);
        playUntilKills(3, 40_000);
        if (raid() != null && raid().kills() < 3) fail("solo 3 kills", "kills=" + raid().kills() + " phase=" + raid().phase());
        else assertBossVisible("three-kills");
        quitRaid();
    }

    private void coopThroughBoss() throws Exception {
        choose(2, 4);
        await("coop", () -> raid() != null && raid().phase() == RaidPhase.PLAYING);
        playUntilKills(1, 30_000);
        if (raid().kills() < 1) fail("coop kill", "kills=" + raid().kills());
        quitRaid();
    }

    private void raceSeveralKills() throws Exception {
        choose(2, 5);
        await("race", () -> raid() != null && raid().phase() == RaidPhase.PLAYING);
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 20_000 && raid().phase() == RaidPhase.PLAYING) {
            if (!raidSession().isSpawnPending()) DropBot.place(raidSession(), this::key);
            sleep(40);
        }
        quitRaid();
    }

    private void onlineLoopback() {
        failures.add("online: 긴 시나리오 자리. 소켓 경로는 BattleMainFrameTest가 맡는다");
    }

    private void settingsThenPlay() throws Exception {
        begin();
        choose(3);
        await("settings", () -> screen() == MainFrame.Screen.SETTINGS);
        key(KeyEvent.VK_RIGHT);
        key(KeyEvent.VK_RIGHT);
        move(4);
        key(KeyEvent.VK_RIGHT);
        move(1);
        key(KeyEvent.VK_RIGHT);
        move(1);
        key(KeyEvent.VK_RIGHT);
        key(KeyEvent.VK_ESCAPE);
        await("menu after settings", () -> screen() == MainFrame.Screen.MAIN_MENU);
        choose(0, 0);
        await("play after settings", () -> screen() == MainFrame.Screen.GAME && game().getState() == GameState.PLAYING);
        assertAdvances("after-settings", this::game);
        quitToMenu();
    }

    private void fullscreenDoesNotStall() throws Exception {
        begin();
        choose(0, 0);
        await("fs game", () -> game().getState() == GameState.PLAYING);
        SwingUtilities.invokeAndWait(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchKeyEvent(
                new KeyEvent(frame, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_F11, KeyEvent.CHAR_UNDEFINED)));
        flush();
        sleep(400);
        if (!frame.isFullscreen() || !frame.isUndecorated()) {
            fail("fullscreen", "fullscreen=" + frame.isFullscreen() + " undecorated=" + frame.isUndecorated()
                    + " bounds=" + frame.getBounds() + " monitor=" + monitor());
        } else {
            Rectangle monitor = monitor();
            if (monitor != null && !monitor.equals(frame.getBounds())) {
                fail("fullscreen bounds", "window=" + frame.getBounds() + " monitor=" + monitor);
            }
        }
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus == null) fail("fullscreen focus", "focusOwner=null");
        if (game().getState() == GameState.PAUSED) fail("fullscreen pause", "toggle paused the game");
        else assertAdvances("fullscreen", this::game);
        SwingUtilities.invokeAndWait(frame::toggleFullscreen);
        flush();
        quitToMenu();
    }

    private void reenterDoesNotLeakTimers() throws Exception {
        begin();
        int before = runningTimers().size();
        choose(0, 0);
        await("reenter game", () -> screen() == MainFrame.Screen.GAME);
        quitToMenu();
        choose(0, 1);
        await("reenter raid", () -> screen() == MainFrame.Screen.RAID);
        quitRaid();
        int after = runningTimers().size();
        if (after > before + 2) fail("timer leak", "before=" + before + " after=" + after + " " + timerNames());
    }

    private void assertAdvances(String name, SessionSource source) throws Exception {
        int row = source.get().snapshot().currentBlock().getRow();
        Block before = source.get().snapshot().currentBlock();
        sleep(2200);
        GameSnapshot after = source.get().snapshot();
        if (source.get().getState() != GameState.PLAYING) {
            fail(name + " gravity", "state=" + source.get().getState() + " " + census());
            return;
        }
        Block piece = after.currentBlock();
        boolean moved = piece == null || piece.getRow() != row || piece.getCol() != before.getCol()
                || piece.getType() != before.getType();
        if (!moved) fail(name + " gravity stall", "row=" + row + " " + census());
    }

    private void assertBossVisible(String where) throws Exception {
        RaidSnapshot snap = raid();
        if (snap.boss() == null || snap.boss().hp() <= 0) {
            fail("boss missing " + where, String.valueOf(snap.phase()));
            return;
        }
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            RaidPanel panel = raidPanel();
            if (panel == null) return;
            panel.setSize(Math.max(panel.getWidth(), 600), Math.max(panel.getHeight(), 720));
            BufferedImage buffer = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            panel.paint(buffer.createGraphics());
            image.set(buffer);
        });
        if (image.get() == null) fail("boss paint " + where, "no panel");
    }

    private void playUntilKills(int target, long budgetMillis) throws Exception {
        if (stopped) return;
        long start = System.currentTimeMillis();
        RaidPhase stuck = null;
        long stuckAt = start;
        while (System.currentTimeMillis() - start < budgetMillis) {
            RaidSnapshot snap = raid();
            if (snap == null) return;
            if (snap.kills() >= target && snap.phase() == RaidPhase.PLAYING) return;
            if (snap.phase() != stuck) {
                stuck = snap.phase();
                stuckAt = System.currentTimeMillis();
            } else if (snap.phase() == RaidPhase.CARD_PICK && System.currentTimeMillis() - stuckAt > 8_000) {
                fail("card stuck", census());
                return;
            } else if (snap.phase() == RaidPhase.PLAYING && System.currentTimeMillis() - stuckAt > 12_000
                    && snap.kills() < target) {
                // 보스가 안 줄고 조각도 안 내려가면 멈춤
            }
            if (snap.phase() == RaidPhase.CARD_PICK) {
                key(KeyEvent.VK_SPACE);
            } else if (snap.phase() == RaidPhase.PLAYING && !raidSession().isSpawnPending()
                    && raidSession().getState() == GameState.PLAYING) {
                DropBot.place(raidSession(), this::key);
            }
            sleep(30);
        }
        fail("raid budget", "kills=" + (raid() == null ? -1 : raid().kills()) + " phase="
                + (raid() == null ? null : raid().phase()) + " " + census());
    }

    private void clickCard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RaidPanel panel = raidPanel();
            if (panel == null) return;
            int x = panel.getWidth() / 2;
            int y = panel.getHeight() / 2;
            panel.click(x, y);
            panel.click(x, y);
        });
    }

    private void choose(int... downs) throws Exception {
        for (int count : downs) {
            resetSelection();
            move(count);
            key(KeyEvent.VK_ENTER);
            sleep(150);
        }
    }

    private void resetSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                @SuppressWarnings("unchecked")
                var screens = (java.util.Map<MainFrame.Screen, JComponent>) field(frame, "screens");
                JComponent panel = screens.get((MainFrame.Screen) field(frame, "currentScreen"));
                if (panel instanceof tetris.ui.MainMenuPanel menu) menu.resetSelection();
            } catch (Exception ignored) {
                // 메뉴가 아니면 선택 초기화가 필요 없다
            }
        });
    }

    private void move(int steps) throws Exception {
        for (int i = 0; i < steps; i++) key(KeyEvent.VK_DOWN);
    }

    private void quitToMenu() throws Exception {
        key(KeyEvent.VK_Q);
        sleep(100);
        key(KeyEvent.VK_LEFT);
        key(KeyEvent.VK_ENTER);
        await("menu", () -> screen() == MainFrame.Screen.MAIN_MENU);
    }

    private void quitRaid() throws Exception {
        key(KeyEvent.VK_Q);
        sleep(100);
        key(KeyEvent.VK_RIGHT);
        key(KeyEvent.VK_ENTER);
        await("left raid", () -> screen() != MainFrame.Screen.RAID);
        if (screen() != MainFrame.Screen.MAIN_MENU) {
            key(KeyEvent.VK_ESCAPE);
            await("main", () -> screen() == MainFrame.Screen.MAIN_MENU);
        }
    }

    private void key(int code) {
        if (SwingUtilities.isEventDispatchThread()) {
            dispatch(code);
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> dispatch(code));
        } catch (Exception failure) {
            fail("key " + code, failure.toString());
        }
    }

    private void dispatch(int code) {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus == null && frame != null) focus = frame.getRootPane();
        if (focus == null) return;
        long when = System.currentTimeMillis();
        focus.dispatchEvent(new KeyEvent(focus, KeyEvent.KEY_PRESSED, when, 0, code, KeyEvent.CHAR_UNDEFINED));
        focus.dispatchEvent(new KeyEvent(focus, KeyEvent.KEY_RELEASED, when, 0, code, KeyEvent.CHAR_UNDEFINED));
    }

    private void await(String what, Check check) throws Exception {
        if (stopped) return;
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(onEdt(check))) return;
            if (!errors.isEmpty()) {
                fail(what, "exception " + errors.get(0));
                return;
            }
            sleep(40);
        }
        fail(what, "timeout " + census());
    }

    private MainFrame.Screen screen() throws Exception {
        return (MainFrame.Screen) field(frame, "currentScreen");
    }

    private GameSession game() throws Exception {
        return (GameSession) field(frame, "session");
    }

    private RaidSnapshot raid() throws Exception {
        Object controller = field(frame, "raidController");
        return controller == null ? null : (RaidSnapshot) field(controller, "current");
    }

    private GameSession raidSession() throws Exception {
        Object controller = field(frame, "raidController");
        if (controller == null) return null;
        Object client = field(controller, "client");
        Object engine = field(client, "engine");
        return ((GameSession[]) field(engine, "sessions"))[0];
    }

    private GameSession battleSession(int index) throws Exception {
        Object controller = field(frame, "battleController");
        if (controller == null) return null;
        Object client = field(controller, "client");
        Object engine = field(client, "engine");
        return ((GameSession[]) field(engine, "sessions"))[index];
    }

    private RaidPanel raidPanel() {
        try {
            @SuppressWarnings("unchecked")
            var screens = (java.util.Map<MainFrame.Screen, JComponent>) field(frame, "screens");
            JComponent panel = screens.get(MainFrame.Screen.RAID);
            return panel instanceof RaidPanel raid ? raid : null;
        } catch (Exception failure) {
            return null;
        }
    }

    private Rectangle monitor() {
        var gc = frame.getGraphicsConfiguration();
        return gc == null ? null : gc.getBounds();
    }

    private boolean stopped;

    private void begin() {
        stopped = false;
    }

    private boolean broken() {
        return stopped;
    }

    private void fail(String what, String detail) {
        failures.add(what + ": " + detail);
        stopped = true;
    }

    private String census() {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return "focus=" + (focus == null ? "null" : focus.getClass().getSimpleName())
                + " timers=" + timerNames()
                + " paintMs=" + String.format("%.2f", paint.averageMillis())
                + " stderr=" + errBytes.size();
    }

    static List<Timer> runningTimers() {
        List<Timer> found = new ArrayList<>();
        try {
            Class<?> queueType = Class.forName("javax.swing.TimerQueue");
            Object shared = queueType.getMethod("sharedInstance").invoke(null);
            Field queueField = queueType.getDeclaredField("queue");
            queueField.setAccessible(true);
            Object queue = queueField.get(shared);
            for (Object delayed : (Object[]) queue.getClass().getMethod("toArray").invoke(queue)) {
                Timer timer = (Timer) delayed.getClass().getMethod("getTimer").invoke(delayed);
                if (timer != null && timer.isRunning()) found.add(timer);
            }
        } catch (ReflectiveOperationException failure) {
            return List.of();
        }
        return found;
    }

    private String timerNames() {
        StringBuilder text = new StringBuilder();
        for (Timer timer : runningTimers()) text.append(timer.getDelay()).append("ms ");
        return text.toString().trim();
    }

    private void installWatch() {
        previousErr = System.err;
        PrintStream tee = new PrintStream(new Tee(previousErr, errBytes), true);
        System.setErr(tee);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            errors.add(error);
            ErrorLog.shared().report(thread.getName(), error);
        });
        paint.install();
    }

    private void restoreWatch() {
        if (previousErr != null) System.setErr(previousErr);
    }

    private void closeFrame() {
        if (frame == null) return;
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.dispose();
                frame = null;
            });
        } catch (Exception ignored) {
            frame = null;
        }
    }

    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void sleep(long millis) throws Exception {
        Thread.sleep(millis);
    }

    private static Object onEdt(Check check) throws Exception {
        AtomicReference<Object> value = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                value.set(check.get());
            } catch (Exception error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) throw failure.get();
        return value.get();
    }

    private static Object field(Object owner, String name) throws Exception {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found.get(owner);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @FunctionalInterface
    private interface Check {
        Object get() throws Exception;
    }

    @FunctionalInterface
    private interface SessionSource {
        GameSession get() throws Exception;
    }

    private static final class PaintWatch {
        private final LongAdder nanos = new LongAdder();
        private final LongAdder frames = new LongAdder();

        void install() {
            RepaintManager.setCurrentManager(new RepaintManager() {
                @Override public void paintDirtyRegions() {
                    long started = System.nanoTime();
                    super.paintDirtyRegions();
                    nanos.add(System.nanoTime() - started);
                    frames.increment();
                }
            });
        }

        double averageMillis() {
            long count = frames.sum();
            return count == 0 ? 0 : nanos.sum() / 1_000_000.0 / count;
        }
    }

    private static final class Tee extends OutputStream {
        private final PrintStream primary;
        private final ByteArrayOutputStream copy;

        Tee(PrintStream primary, ByteArrayOutputStream copy) {
            this.primary = primary;
            this.copy = copy;
        }

        @Override public void write(int b) {
            primary.write(b);
            copy.write(b);
        }
    }

    /** 줄 삭제를 우선하는 짧은 배치. 시드가 같으면 같은 열을 고른다 */
    static final class DropBot {
        static void place(GameSession session, java.util.function.IntConsumer key) {
            GameSnapshot snap = session.snapshot();
            Block piece = snap.currentBlock();
            if (piece == null) return;
            int[][] cells = snap.cells();
            int[][] shape = piece.getShape();
            int bestRot = 0;
            int bestCol = piece.getCol();
            int best = Integer.MIN_VALUE;
            for (int rot = 0; rot < 4; rot++) {
                for (int col = -2; col < 10; col++) {
                    int row = landing(cells, shape, col);
                    if (row == Integer.MIN_VALUE) continue;
                    int score = value(cells, shape, row, col);
                    if (score > best) {
                        best = score;
                        bestRot = rot;
                        bestCol = col;
                    }
                }
                shape = turn(shape);
            }
            int turns = Math.floorMod(bestRot - piece.getRotation(), 4);
            for (int i = 0; i < turns; i++) key.accept(KeyEvent.VK_UP);
            int col = session.snapshot().currentBlock().getCol();
            while (col < bestCol) {
                key.accept(KeyEvent.VK_RIGHT);
                col++;
            }
            while (col > bestCol) {
                key.accept(KeyEvent.VK_LEFT);
                col--;
            }
            key.accept(KeyEvent.VK_SPACE);
        }

        private static int landing(int[][] cells, int[][] shape, int col) {
            int last = Integer.MIN_VALUE;
            for (int row = -2; row < 22; row++) {
                if (!fits(cells, shape, row, col)) break;
                last = row;
            }
            return last;
        }

        private static boolean fits(int[][] cells, int[][] shape, int row, int col) {
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] == 0) continue;
                    int y = row + r;
                    int x = col + c;
                    if (x < 0 || x >= 10 || y >= cells.length) return false;
                    if (y >= 0 && cells[y][x] != 0) return false;
                }
            }
            return true;
        }

        private static int value(int[][] cells, int[][] shape, int row, int col) {
            int lines = 0;
            int holes = 0;
            for (int y = 0; y < cells.length; y++) {
                boolean full = true;
                for (int x = 0; x < 10; x++) {
                    boolean filled = cells[y][x] != 0 || covers(shape, row, col, y, x);
                    if (!filled) full = false;
                }
                if (full) lines++;
            }
            for (int x = 0; x < 10; x++) {
                boolean seen = false;
                for (int y = 0; y < cells.length; y++) {
                    boolean filled = cells[y][x] != 0 || covers(shape, row, col, y, x);
                    if (filled) seen = true;
                    else if (seen) holes++;
                }
            }
            return lines * 100 - holes * 8 - (cells.length - row);
        }

        private static boolean covers(int[][] shape, int row, int col, int y, int x) {
            int r = y - row;
            int c = x - col;
            return r >= 0 && c >= 0 && r < shape.length && c < shape[r].length && shape[r][c] != 0;
        }

        private static int[][] turn(int[][] shape) {
            int n = shape.length;
            int[][] rotated = new int[n][n];
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) rotated[c][n - 1 - r] = shape[r][c];
            }
            return rotated;
        }
    }
}
