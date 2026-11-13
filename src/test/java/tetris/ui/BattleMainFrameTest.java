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
    private static int firstVisibleColumn(int[][] cells) {
        for (int[] row : cells) for (int col=0; col<row.length; col++) if(row[col]!=0) return col;
        return -1;
    }
    @Test void defaultFactoryRunsRealMatchAndReachesResultWithoutSaving() throws Exception {
        MainFrame[] frame = new MainFrame[1]; var scores = new Scoreboard(java.util.List.of());
        var moved = new java.util.concurrent.CountDownLatch(1);
        int[] initialColumns = {-1, -1};
        var client = new java.util.concurrent.atomic.AtomicReference<LocalBattleClient>();
        Path scoreFile = directory.resolve("real-scores.json");
        var robot = new java.awt.Robot(); robot.setAutoDelay(60);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new MainFrame(new Settings(), scores,
                    new JsonSettingsRepository(directory.resolve("settings.json")), new JsonScoreboardRepository(scoreFile));
                frame[0].setVisible(true); frame[0].startBattle(BattleMode.CLASSIC); frame[0].toFront();
                try {
                    Object controller = field(frame[0], "battleController");
                    client.set((LocalBattleClient) field(controller, "client"));
                    client.get().subscribe(state -> {
                        int one=firstVisibleColumn(state.one().cells()), two=firstVisibleColumn(state.two().cells());
                        if(initialColumns[0]<0) { initialColumns[0]=one; initialColumns[1]=two; }
                        else if(one==initialColumns[0]-1 && two==initialColumns[1]+1) moved.countDown();
                    });
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            robot.waitForIdle();
            // Always exercise the real factory. OS injection is explicitly opt-in;
            // the default GUI run dispatches KeyEvents through the same focused panel.
            boolean nativeInput=Boolean.getBoolean("audit.native");
            key(robot,java.awt.event.KeyEvent.VK_LEFT,true,nativeInput);
            key(robot,java.awt.event.KeyEvent.VK_D,true,nativeInput);
            key(robot,java.awt.event.KeyEvent.VK_D,false,nativeInput);
            key(robot,java.awt.event.KeyEvent.VK_LEFT,false,nativeInput);
            assertTrue(moved.await(3, java.util.concurrent.TimeUnit.SECONDS), "input must move both real sessions horizontally");
            SwingUtilities.invokeAndWait(() -> client.get().send(new PlayerCommand(PlayerId.TWO,
                tetris.feature.setting.GameAction.QUIT,true,10000)));
            SwingUtilities.invokeAndWait(() -> {
                try {
                    assertEquals(MainFrame.Screen.BATTLE_RESULT, field(frame[0], "currentScreen"));
                    assertTrue(((BattleResultPanel)field(frame[0],"battleResultPanel")).getSummaryText().contains("P1"));
                    assertTrue(scores.entries().isEmpty()); assertFalse(java.nio.file.Files.exists(scoreFile));
                } catch(Exception e) { throw new RuntimeException(e); }
            });
        } finally {
            if(Boolean.getBoolean("audit.native")) {
                robot.keyRelease(java.awt.event.KeyEvent.VK_LEFT); robot.keyRelease(java.awt.event.KeyEvent.VK_D);
            }
            SwingUtilities.invokeAndWait(() -> { if(frame[0]!=null)frame[0].dispose(); });
        }
    }
    private static void until(String what, java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        boolean[] ok = new boolean[1];
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> {
                try { ok[0] = condition.call(); } catch (Exception e) { throw new RuntimeException(e); }
            });
            if (ok[0]) return;
            Thread.sleep(20);
        }
        fail("timed out waiting for " + what);
    }
    private void shot(MainFrame frame, String name) {
        var image = new java.awt.image.BufferedImage(frame.getWidth(), frame.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics(); frame.printAll(g); g.dispose();
        var output = Path.of("build/verification/" + name + ".png");
        try {
            java.nio.file.Files.createDirectories(output.getParent());
            javax.imageio.ImageIO.write(image, "png", output.toFile());
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    /** S9-B2: 두 창이 실제 소켓으로 접속 → 양쪽 준비 → 실제 키 입력 → 같은 결과 → 같은 연결의 로비 */
    @Test void twoWindowsPlayARealSocketMatchFromLobbyToTheSameResult() throws Exception {
        MainFrame[] frames = new MainFrame[2];
        Object[] controllers = new Object[2];
        var scores = new Scoreboard(java.util.List.of());
        Path scoreFile = directory.resolve("p2p-scores.json");
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (int i = 0; i < 2; i++) {
                    // 실제 소켓 로비. 최근 IP는 사용자 %APPDATA%가 아니라 임시 파일에 남긴다
                    var recent = new JsonRecentPeerRepository(directory.resolve("recent" + i + ".json"));
                    frames[i] = new MainFrame(new Settings(), scores,
                        new JsonSettingsRepository(directory.resolve("settings" + i + ".json")), new JsonScoreboardRepository(scoreFile),
                        LocalBattleClient::new, () -> MainFrame.socketLobby(recent));
                    frames[i].setVisible(true);
                    frames[i].setLocation(40 + i * 520, 40);
                    frames[i].openBattleModes();
                    frames[i].openP2pLobby();
                    try { controllers[i] = field(frames[i], "p2p"); } catch (Exception e) { throw new RuntimeException(e); }
                }
                var hostPanel = ((P2pLobbyController) controllers[0]).panel();
                hostPanel.portField().setText("0");
                hostPanel.hostButton().doClick();
            });
            int[] port = new int[1];
            until("host listening", () -> {
                var lobby = (tetris.feature.network.lobby.SocketLobbyClient) field(controllers[0], "lobby");
                port[0] = lobby.localPort();
                return port[0] > 0;
            });
            SwingUtilities.invokeAndWait(() -> {
                var clientPanel = ((P2pLobbyController) controllers[1]).panel();
                clientPanel.addressField().setText("127.0.0.1");
                clientPanel.portField().setText(String.valueOf(port[0]));
                clientPanel.connectButton().doClick();
            });
            until("both configured in LOBBY", () -> {
                var host = ((P2pLobbyController) controllers[0]).snapshot();
                var client = ((P2pLobbyController) controllers[1]).snapshot();
                return host.state() == tetris.feature.network.lobby.LobbySnapshot.State.LOBBY && host.config() != null
                    && client.state() == tetris.feature.network.lobby.LobbySnapshot.State.LOBBY && client.config() != null;
            });
            SwingUtilities.invokeAndWait(() -> {
                var clientPanel = ((P2pLobbyController) controllers[1]).panel();
                assertTrue(clientPanel.statusText().startsWith("연결 성공!"), clientPanel.statusText());
                assertFalse(clientPanel.modeBox().isEnabled(), "client cannot edit the host's mode");
                shot(frames[0], "s9-b2-host-lobby");
                shot(frames[1], "s9-b2-client-lobby");
                clientPanel.readyButton().doClick();
            });
            until("host sees the client's vote", () -> ((P2pLobbyController) controllers[0]).snapshot().peerReady());
            SwingUtilities.invokeAndWait(() -> ((P2pLobbyController) controllers[0]).panel().readyButton().doClick());
            until("both on the battle screen", () -> field(frames[0], "currentScreen") == MainFrame.Screen.BATTLE
                && field(frames[1], "currentScreen") == MainFrame.Screen.BATTLE);

            // the client's real key event moves only the right board on the host
            Object[] hostClient = new Object[1];
            int[] start = new int[2];
            until("first STATE on the client", () -> {
                hostClient[0] = field(field(frames[0], "battleController"), "client");
                var clientView = (tetris.feature.network.match.ClientBattleClient) field(field(frames[1], "battleController"), "client");
                return clientView.snapshot().phase() == MatchPhase.PLAYING;
            });
            SwingUtilities.invokeAndWait(() -> { try {
                var state = ((tetris.feature.network.match.HostBattleClient) hostClient[0]).snapshot();
                start[0] = firstVisibleColumn(state.one().cells());
                start[1] = firstVisibleColumn(state.two().cells());
                var panel = (BattlePanel) ((java.util.Map<?, ?>) field(frames[1], "screens")).get(MainFrame.Screen.BATTLE);
                var manager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
                for (int id : new int[]{java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.KEY_RELEASED}) {
                    manager.redispatchEvent(panel, new java.awt.event.KeyEvent(panel, id, System.currentTimeMillis(), 0,
                        java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.CHAR_UNDEFINED));
                }
            } catch (Exception e) { throw new RuntimeException(e); } });
            until("client input applied by the host", () -> firstVisibleColumn(
                ((tetris.feature.network.match.HostBattleClient) hostClient[0]).snapshot().two().cells()) == start[1] - 1);
            until("client screen shows its own move", () -> firstVisibleColumn(
                ((tetris.feature.network.match.ClientBattleClient) field(field(frames[1], "battleController"), "client"))
                    .snapshot().two().cells()) == start[1] - 1);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(start[0], firstVisibleColumn(((tetris.feature.network.match.HostBattleClient) hostClient[0])
                    .snapshot().one().cells()), "the client cannot move the host's board");
                shot(frames[0], "s9-b2-host-match");
                shot(frames[1], "s9-b2-client-match");
                ((BattleClient) hostClient[0]).send(new PlayerCommand(PlayerId.ONE, tetris.feature.setting.GameAction.QUIT, true, 99));
            });
            until("both on the result screen", () -> field(frames[0], "currentScreen") == MainFrame.Screen.P2P_RESULT
                && field(frames[1], "currentScreen") == MainFrame.Screen.P2P_RESULT);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    var hostResult = (BattleResultPanel) field(frames[0], "onlineResultPanel");
                    var clientResult = (BattleResultPanel) field(frames[1], "onlineResultPanel");
                    // 같은 결과(P2 승리·기권)를 각자 관점으로 본다 (S10-B1)
                    assertTrue(hostResult.getSummaryText().endsWith("패배 · 기권"), hostResult.getSummaryText());
                    assertTrue(clientResult.getSummaryText().endsWith("승리! · 기권"), clientResult.getSummaryText());
                    assertEquals(hostResult.getScoresText().replace("(나)", "?").replace("(상대)", "?"),
                        clientResult.getScoresText().replace("(나)", "?").replace("(상대)", "?"), "두 PC의 점수는 같다");
                    assertTrue(hostResult.getScoresText().startsWith("P1 (나)"), hostResult.getScoresText());
                    assertTrue(clientResult.getScoresText().contains("P2 (나)"), clientResult.getScoresText());
                    shot(frames[1], "s9-b2-client-result");
                    var back = MainFrame.class.getDeclaredMethod("backToP2pLobby"); back.setAccessible(true);
                    back.invoke(frames[1]);
                    assertEquals(MainFrame.Screen.P2P_LOBBY, field(frames[1], "currentScreen"));
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            until("client back in the same lobby", () -> ((P2pLobbyController) controllers[1]).snapshot().state()
                == tetris.feature.network.lobby.LobbySnapshot.State.LOBBY);
            assertTrue(scores.entries().isEmpty());
            assertFalse(java.nio.file.Files.exists(scoreFile), "P2P results are never saved");
            // 접속에 성공한 클라이언트만 상대 주소를 남긴다 (R3-RECENT, 디스크 쓰기는 별도 스레드)
            until("client recorded the host address", () ->
                new JsonRecentPeerRepository(directory.resolve("recent1.json")).load().equals(java.util.List.of("127.0.0.1")));
            assertTrue(new JsonRecentPeerRepository(directory.resolve("recent0.json")).load().isEmpty(), "the host records nothing");
        } finally {
            SwingUtilities.invokeAndWait(() -> { for (var f : frames) if (f != null) f.dispose(); });
        }
    }

    /** S9-B1: 세 모드 모두 메뉴에서 실제 엔진으로 진입하고, 결과까지 가도 스코어보드 파일이 그대로다 */
    @Test void everyLocalModeReachesItsResultWithoutTouchingTheScoreboardFile() throws Exception {
        Path scoreFile = directory.resolve("modes-scores.json");
        var seeded = new Scoreboard(java.util.List.of());
        seeded.add(new tetris.feature.score.ScoreEntry("SEED", 1234, 1, 0, tetris.feature.game.GameMode.CLASSIC,
            tetris.feature.game.Difficulty.NORMAL, java.time.Instant.parse("2026-11-01T00:00:00Z")));
        assertTrue(new JsonScoreboardRepository(scoreFile).save(seeded));
        byte[] before = java.nio.file.Files.readAllBytes(scoreFile);
        MainFrame[] frame = new MainFrame[1];
        var screens = new java.util.ArrayList<Object>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new MainFrame(new Settings(), seeded,
                    new JsonSettingsRepository(directory.resolve("settings.json")), new JsonScoreboardRepository(scoreFile));
                frame[0].setVisible(true);
            });
            String[] labels = {"일반 대전", "아이템 대전", "시간제한 대전"};
            for (int index = 0; index < 3; index++) {
                int menuIndex = index;
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        frame[0].openBattleModes();
                        var modes = (MainMenuPanel) ((java.util.Map<?, ?>) field(frame[0], "screens"))
                            .get(MainFrame.Screen.BATTLE_MODES);
                        while (modes.getSelectedIndex() != menuIndex) modes.moveSelection(1);
                        modes.activateSelected();
                        assertEquals(MainFrame.Screen.BATTLE, field(frame[0], "currentScreen"));
                        var panel = (BattlePanel) ((java.util.Map<?, ?>) field(frame[0], "screens")).get(MainFrame.Screen.BATTLE);
                        assertEquals(labels[menuIndex], panel.modeLabel());
                        var client = (LocalBattleClient) field(field(frame[0], "battleController"), "client");
                        client.send(new PlayerCommand(PlayerId.TWO, tetris.feature.setting.GameAction.QUIT, true, 1_000_000));
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        screens.add(field(frame[0], "currentScreen"));
                        var result = (BattleResultPanel) field(frame[0], "battleResultPanel");
                        assertTrue(result.getSummaryText().startsWith(labels[menuIndex]), result.getSummaryText());
                        assertTrue(result.getSummaryText().contains("기권"));
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
            assertEquals(java.util.List.of(MainFrame.Screen.BATTLE_RESULT, MainFrame.Screen.BATTLE_RESULT,
                MainFrame.Screen.BATTLE_RESULT), screens, "no NAME_INPUT or SCOREBOARD after a battle");
            assertArrayEquals(before, java.nio.file.Files.readAllBytes(scoreFile), "battle results are never saved");
            assertEquals(1, seeded.entries().size());
        } finally {
            SwingUtilities.invokeAndWait(() -> { if(frame[0]!=null) frame[0].dispose(); });
        }
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
