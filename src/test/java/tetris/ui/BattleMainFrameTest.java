package tetris.ui;

import javax.swing.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import tetris.feature.setting.Settings;
import tetris.feature.score.Scoreboard;
import tetris.feature.save.*;
import tetris.feature.versus.api.*;
import tetris.ui.versus.*;

@EnabledIfSystemProperty(named="audit.gui", matches="true")
class BattleMainFrameTest {
    @TempDir Path directory;
    private Object field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    @Test
    @EnabledIfSystemProperty(named="audit.native", matches="true")
    void nativeOverlappingPlayerKeysReachOneWindow() throws Exception { overlappingKeys(true); }
    @Test void overlappingKeyEventsReachOneWindow() throws Exception { overlappingKeys(false); }
    private void key(java.awt.Robot robot, int code, boolean pressed, boolean nativeInput) throws Exception {
        if (nativeInput) {
            if (pressed) robot.keyPress(code); else robot.keyRelease(code);
        } else SwingUtilities.invokeAndWait(() -> {
            var manager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var owner = manager.getFocusOwner(); assertInstanceOf(BattlePanel.class, owner);
            manager.redispatchEvent(owner, new java.awt.event.KeyEvent(owner,
                pressed ? java.awt.event.KeyEvent.KEY_PRESSED : java.awt.event.KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), 0, code, java.awt.event.KeyEvent.CHAR_UNDEFINED));
        });
    }
    private void overlappingKeys(boolean nativeInput) throws Exception {
        MainFrame[] frame = new MainFrame[1]; var client = new FakeBattleClient();
        var robot = new java.awt.Robot(); robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new MainFrame(new Settings(), new Scoreboard(java.util.List.of()),
                    new JsonSettingsRepository(directory.resolve("settings.json")),
                    new JsonScoreboardRepository(directory.resolve("scores.json")), config -> client);
                frame[0].setVisible(true); frame[0].startBattle(BattleMode.CLASSIC);
                frame[0].toFront();
            });
            robot.waitForIdle();
            key(robot, java.awt.event.KeyEvent.VK_LEFT, true, nativeInput);
            key(robot, java.awt.event.KeyEvent.VK_A, true, nativeInput);
            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> {
                var pressed = client.commands().stream().filter(PlayerCommand::pressed).toList();
                assertTrue(pressed.stream().anyMatch(c -> c.playerId()==PlayerId.ONE), "commands=" + client.commands());
                assertTrue(pressed.stream().anyMatch(c -> c.playerId()==PlayerId.TWO));
            });
            key(robot, java.awt.event.KeyEvent.VK_A, false, nativeInput);
            key(robot, java.awt.event.KeyEvent.VK_LEFT, false, nativeInput);
            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(client.commands().stream().anyMatch(c -> !c.pressed() && c.playerId()==PlayerId.ONE));
                assertTrue(client.commands().stream().anyMatch(c -> !c.pressed() && c.playerId()==PlayerId.TWO));
            });
            SwingUtilities.invokeAndWait(() -> {
                var image = new java.awt.image.BufferedImage(frame[0].getWidth(), frame[0].getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics(); frame[0].printAll(g); g.dispose();
                var output = Path.of("build/verification/sprint8-b-window.png");
                try {
                    java.nio.file.Files.createDirectories(output.getParent());
                    javax.imageio.ImageIO.write(image,"png",output.toFile());
                } catch(java.io.IOException e) { throw new RuntimeException(e); }
            });
        } finally {
            if (nativeInput) { robot.keyRelease(java.awt.event.KeyEvent.VK_A); robot.keyRelease(java.awt.event.KeyEvent.VK_LEFT); }
            SwingUtilities.invokeAndWait(() -> { if(frame[0]!=null) frame[0].dispose(); });
        }
    }
    @Test void menuResultAndReturnNeverWriteSinglePlayerScores() throws Exception {
        MainFrame[] frame = new MainFrame[1]; var client = new FakeBattleClient();
        var scores = new Scoreboard(java.util.List.of()); Path scoreFile = directory.resolve("scores.json");
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new MainFrame(new Settings(), scores,
                    new JsonSettingsRepository(directory.resolve("settings.json")),
                    new JsonScoreboardRepository(scoreFile), config -> client);
                frame[0].setVisible(true);
                try {
                    var menu = (MainMenuPanel) field(frame[0], "mainMenuPanel");
                    for (int i=0;i<5;i++) menu.moveSelection(1);
                    menu.activateSelected();
                    assertEquals(MainFrame.Screen.BATTLE_MODES, field(frame[0], "currentScreen"));
                    frame[0].startBattle(BattleMode.CLASSIC);
                    assertEquals(MainFrame.Screen.BATTLE, field(frame[0], "currentScreen"));
                    var s = FakeBattleClient.sample();
                    client.publish(new MatchSnapshot(s.matchId(),1,s.one(),s.two(),-1,MatchPhase.FINISHED,
                        MatchResult.draw(MatchResult.EndReason.TOPPED_OUT)));
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            SwingUtilities.invokeAndWait(() -> {
                try {
                    assertEquals(MainFrame.Screen.BATTLE_RESULT, field(frame[0], "currentScreen"));
                    assertTrue(scores.entries().isEmpty());
                    assertFalse(java.nio.file.Files.exists(scoreFile));
                    var panel = (BattleResultPanel) field(frame[0], "battleResultPanel");
                    assertTrue(panel.getSummaryText().contains("무승부"));
                    frame[0].openBattleModes();
                    assertNull(frame[0].getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                        .get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A,0)));
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> { if(frame[0]!=null) frame[0].dispose(); });
        }
    }
}
