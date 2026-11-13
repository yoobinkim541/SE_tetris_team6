package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.save.JsonScoreboardRepository;
import tetris.feature.save.JsonSettingsRepository;
import tetris.feature.score.ScoreEntry;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.*;
import tetris.ui.MainFrame;
import tetris.ui.MainMenuPanel;
import tetris.ui.versus.*;

/**
 * S11-B1 실제 창 인수: 메인 메뉴부터 키보드로 대전에 들어가 세 화면 크기·색맹 설정에서 두 사람이 동시에 조작하고,
 * 창 포커스를 잃으면 눌린 키가 풀리며 멈추고, 결과 뒤 메뉴로 돌아와도 싱글 설정·기록이 그대로인지 본다
 * (R3-REGRESSION, R3-NOSCORE, R3-MENU). guiAcceptance 태스크가 실제 창(headless=false)으로 실행한다.
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "audit.gui", matches = "true")
class VersusAcceptanceGuiTest {
    @TempDir Path directory;

    private static Object field(Object owner, String name) throws Exception {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException next) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void call(Object owner, String method) throws Exception {
        Method m = owner.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(owner);
    }

    private static void edt(ThrowingRunnable action) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { action.run(); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    private static void key(Component target, int id, int code) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(target,
                new KeyEvent(target, id, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
    }

    private static MainMenuPanel menu(MainFrame frame, MainFrame.Screen screen) throws Exception {
        return (MainMenuPanel) ((Map<?, ?>) field(frame, "screens")).get(screen);
    }

    /** 키보드 ↑↓처럼 한 칸씩 옮겨 index를 고르고 Enter처럼 실행한다 */
    private static void choose(MainMenuPanel menu, int index) throws Exception {
        Method selected = MainMenuPanel.class.getDeclaredMethod("getSelectedIndex");
        selected.setAccessible(true);
        while ((int) selected.invoke(menu) != index) menu.moveSelection(1);
        menu.activateSelected();
    }

    /** 화면 안의 버튼·입력칸이 패널 밖으로 잘리지 않는다 */
    private static void assertInside(Container panel, String where) {
        Rectangle bounds = new Rectangle(0, 0, panel.getWidth(), panel.getHeight());
        List<Component> stack = new ArrayList<>(List.of(panel.getComponents()));
        while (!stack.isEmpty()) {
            Component c = stack.removeLast();
            if (!c.isVisible() || !(c instanceof JComponent)) continue;
            if (c instanceof javax.swing.JLabel label && label.getText().isEmpty()) continue; // 아직 안내가 없는 빈 줄
            Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), panel);
            assertTrue(r.width > 0 && r.height > 0 && bounds.contains(r), where + ": " + c.getClass().getSimpleName() + " " + r + " outside " + bounds);
            if (c instanceof Container inner) stack.addAll(List.of(inner.getComponents()));
        }
    }

    @Test
    void everyScreenSizeWithColorBlindPlaysALocalBattleAndKeepsSinglePlayerData() throws Exception {
        Path settingsFile = directory.resolve("settings.json");
        Path scoreFile = directory.resolve("scores.json");
        var settings = new Settings();
        settings.setColorBlindMode(true);
        settings.setDifficulty(Difficulty.HARD);
        var scores = new Scoreboard(List.of());
        scores.add(new ScoreEntry("SEED", 777, 1, 0, GameMode.CLASSIC, Difficulty.NORMAL, Instant.parse("2026-11-01T00:00:00Z")));
        assertTrue(new JsonScoreboardRepository(scoreFile).save(scores));
        assertTrue(new JsonSettingsRepository(settingsFile).save(settings));
        byte[] settingsBefore = Files.readAllBytes(settingsFile);
        byte[] scoresBefore = Files.readAllBytes(scoreFile);

        for (ScreenSize size : ScreenSize.values()) {
            settings.setScreenSize(size);
            MainFrame[] frame = new MainFrame[1];
            List<PlayerCommand> sent = new ArrayList<>();
            try {
                edt(() -> {
                    frame[0] = new MainFrame(settings, scores, new JsonSettingsRepository(settingsFile),
                            new JsonScoreboardRepository(scoreFile), config -> {
                                var real = new LocalBattleClient(config);
                                return new BattleClient() {
                                    @Override public void send(PlayerCommand c) { sent.add(c); real.send(c); }
                                    @Override public Subscription subscribe(java.util.function.Consumer<MatchSnapshot> l) { return real.subscribe(l); }
                                    @Override public void close() { real.close(); }
                                };
                            }, () -> { throw new AssertionError("P2P is not opened here"); });
                    frame[0].setVisible(true);
                    // 메인 메뉴 "대전" → 대전 메뉴 "로컬 아이템 대전" (키보드 선택 경로)
                    choose(menu(frame[0], MainFrame.Screen.MAIN_MENU), 5);
                    assertEquals(MainFrame.Screen.BATTLE_MODES, field(frame[0], "currentScreen"));
                    choose(menu(frame[0], MainFrame.Screen.BATTLE_MODES), 1);
                    assertEquals(MainFrame.Screen.BATTLE, field(frame[0], "currentScreen"), size.name());
                });
                edt(() -> {
                    var panel = (BattlePanel) ((Map<?, ?>) field(frame[0], "screens")).get(MainFrame.Screen.BATTLE);
                    assertEquals("아이템 대전", panel.modeLabel());
                    assertNull(panel.viewer(), "로컬 대전은 한 화면 두 사람");
                    assertTrue(panel.isShowing());
                    var content = frame[0].getContentPane();
                    assertTrue(panel.getWidth() <= content.getWidth() && panel.getHeight() <= content.getHeight(),
                            size + " battle panel " + panel.getSize() + " fits " + content.getSize());
                    // 두 사람이 동시에 누른다: P1 ←와 P2 D가 겹친 채 각자 한 번씩 전달된다
                    key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT);
                    key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_D);
                    key(panel, KeyEvent.KEY_RELEASED, KeyEvent.VK_LEFT);
                    key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_A);
                    // 누른 채로 창 포커스를 잃는다: 남은 키(D, A)를 놓고 로컬 대전은 멈춘다
                    call(frame[0], "onFocusLost");
                });
                edt(() -> {
                    var p1 = sent.stream().filter(c -> c.playerId() == PlayerId.ONE && c.action() == tetris.feature.setting.GameAction.MOVE_LEFT).toList();
                    var p2Right = sent.stream().filter(c -> c.playerId() == PlayerId.TWO && c.action() == tetris.feature.setting.GameAction.MOVE_RIGHT).toList();
                    var p2Left = sent.stream().filter(c -> c.playerId() == PlayerId.TWO && c.action() == tetris.feature.setting.GameAction.MOVE_LEFT).toList();
                    assertEquals(List.of(true, false), p1.stream().map(PlayerCommand::pressed).toList(), size + " P1");
                    assertEquals(List.of(true, false), p2Right.stream().map(PlayerCommand::pressed).toList(), size + " P2 D released on focus loss");
                    assertEquals(List.of(true, false), p2Left.stream().map(PlayerCommand::pressed).toList(), size + " P2 A released on focus loss");
                    var client = (BattleClient) field(field(frame[0], "battleController"), "client");
                    MatchSnapshot[] seen = new MatchSnapshot[1];
                    try (var s = client.subscribe(snapshot -> seen[0] = snapshot)) {
                        assertEquals(MatchPhase.PAUSED, seen[0].phase(), "포커스를 잃으면 두 보드가 함께 멈춘다");
                    }
                    // 기권으로 끝내고 결과 → Enter(모드 선택) → 메인 메뉴
                    client.send(new PlayerCommand(PlayerId.ONE, tetris.feature.setting.GameAction.QUIT, true, 1_000_000));
                });
                edt(() -> {
                    assertEquals(MainFrame.Screen.BATTLE_RESULT, field(frame[0], "currentScreen"));
                    var result = (BattleResultPanel) field(frame[0], "battleResultPanel");
                    assertEquals("아이템 대전 · P2 승리 · 기권", result.getSummaryText());
                    var name = result.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
                    result.getActionMap().get(name).actionPerformed(null);
                    assertEquals(MainFrame.Screen.BATTLE_MODES, field(frame[0], "currentScreen"));
                    choose(menu(frame[0], MainFrame.Screen.BATTLE_MODES), 4);
                    assertEquals(MainFrame.Screen.MAIN_MENU, field(frame[0], "currentScreen"));
                    assertTrue(settings.isColorBlindMode());
                    assertEquals(Difficulty.HARD, settings.getDifficulty());
                    assertEquals(1, scores.entries().size());
                });
            } finally {
                edt(() -> { if (frame[0] != null) frame[0].dispose(); });
            }
        }
        assertArrayEquals(settingsBefore, Files.readAllBytes(settingsFile), "대전은 싱글 설정 파일을 쓰지 않는다");
        assertArrayEquals(scoresBefore, Files.readAllBytes(scoreFile), "대전 결과는 스코어보드에 저장하지 않는다");
    }

    @Test
    void p2pLobbyFitsEverySizeAndKeepsItsControlsReachable() throws Exception {
        for (ScreenSize size : ScreenSize.values()) {
            var settings = new Settings();
            settings.setScreenSize(size);
            MainFrame[] frame = new MainFrame[1];
            try {
                edt(() -> {
                    frame[0] = new MainFrame(settings, new Scoreboard(List.of()),
                            new JsonSettingsRepository(directory.resolve("s.json")), new JsonScoreboardRepository(directory.resolve("c.json")),
                            LocalBattleClient::new, () -> new P2pLobbyControllerTest.FakeLobby());
                    frame[0].setVisible(true);
                    frame[0].openBattleModes();
                    choose(menu(frame[0], MainFrame.Screen.BATTLE_MODES), 3);
                    assertEquals(MainFrame.Screen.P2P_LOBBY, field(frame[0], "currentScreen"));
                });
                edt(() -> {
                    var lobby = (P2pLobbyController) field(frame[0], "p2p");
                    var panel = lobby.panel();
                    panel.validate();
                    assertInside(panel, size.name());
                    assertSame(panel.addressField(), panel.focusTarget(), "첫 화면은 IP 입력부터");
                    lobby.cancelOrBack();
                    assertEquals(MainFrame.Screen.BATTLE_MODES, field(frame[0], "currentScreen"));
                });
            } finally {
                edt(() -> { if (frame[0] != null) frame[0].dispose(); });
            }
        }
    }
}
