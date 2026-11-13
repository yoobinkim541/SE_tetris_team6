package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.match.HostBattleClient;
import tetris.feature.save.JsonRecentPeerRepository;
import tetris.feature.save.JsonScoreboardRepository;
import tetris.feature.save.JsonSettingsRepository;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.*;
import tetris.ui.MainFrame;
import tetris.ui.versus.*;

/**
 * S11-B2 렌더 지연 실측 (R3-LATENCY). 실제 창 두 개가 loopback TCP로 3모드를 차례로 한 판씩 하며,
 * 호스트 입력과 클라이언트 입력 각각에 대해 "키 이벤트 디스패치 → 두 화면이 움직인 블록을 그린 시각"을 잰다.
 * 같은 PC loopback이라 LAN 전송 지연은 없다. 두 PC LAN 값은 S11-C1 측정 절차로 따로 잰다.
 * 결과는 build/verification/s11-loopback-latency.json에 남긴다.
 */
@EnabledIfSystemProperty(named = "audit.gui", matches = "true")
class LoopbackLatencyAcceptanceTest {
    private static final int SAMPLES_PER_DIRECTION = 8;
    private static final long LIMIT_MILLIS = 200;

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

    private static void until(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean[] ok = new boolean[1];
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> {
                try { ok[0] = condition.call(); } catch (Exception e) { throw new RuntimeException(e); }
            });
            if (ok[0]) return;
            Thread.sleep(5);
        }
        fail("timed out waiting for " + what);
    }

    private interface Edt { void run() throws Exception; }

    private static void edt(Edt action) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { action.run(); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private static int firstColumn(int[][] cells) {
        int best = Integer.MAX_VALUE;
        for (int[] row : cells) for (int c = 0; c < row.length; c++) if (row[c] != 0) best = Math.min(best, c);
        return best;
    }

    private static BattlePanel panel(MainFrame frame) throws Exception {
        return (BattlePanel) ((Map<?, ?>) field(frame, "screens")).get(MainFrame.Screen.BATTLE);
    }

    private static P2pLobbyController lobby(MainFrame frame) throws Exception {
        return (P2pLobbyController) field(frame, "p2p");
    }

    private static MainFrame.Screen screen(MainFrame frame) throws Exception {
        return (MainFrame.Screen) field(frame, "currentScreen");
    }

    /** 첫 입력 이후 그 화면이 board의 블록을 expected 열에 처음 그린 시각 */
    private static long paintedAt(BattlePanel panel, PlayerId board, int expected, long after) {
        for (PaintLog.Entry e : panel.paintLog().entries()) {
            if (e.paintedNanos() < after) continue;
            if (firstColumn(e.snapshot().player(board).cells()) == expected) return e.paintedNanos();
        }
        return -1;
    }

    private static Map<String, Object> stats(List<Double> millis) {
        var sorted = new ArrayList<>(millis);
        sorted.sort(null);
        var out = new LinkedHashMap<String, Object>();
        out.put("count", sorted.size());
        out.put("medianMs", sorted.get(sorted.size() / 2));
        out.put("p95Ms", sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1)));
        out.put("maxMs", sorted.getLast());
        out.put("over200", sorted.stream().filter(v -> v > LIMIT_MILLIS).count());
        out.put("samplesMs", millis);
        return out;
    }

    private static String json(Object value) {
        if (value instanceof Map<?, ?> map) {
            var parts = new ArrayList<String>();
            for (var e : map.entrySet()) parts.add("\"" + e.getKey() + "\": " + json(e.getValue()));
            return "{" + String.join(", ", parts) + "}";
        }
        if (value instanceof List<?> list) return "[" + String.join(", ", list.stream().map(LoopbackLatencyAcceptanceTest::json).toList()) + "]";
        if (value instanceof Double d) return String.format(Locale.ROOT, "%.2f", d);
        if (value instanceof String s) return "\"" + s + "\"";
        return String.valueOf(value);
    }

    @Test
    void inputReachesBothScreensWithin200msInEveryModeAndDirection() throws Exception {
        MainFrame[] frames = new MainFrame[2];
        var report = new LinkedHashMap<String, Object>();
        var failures = new ArrayList<String>();
        try {
            edt(() -> {
                for (int i = 0; i < 2; i++) {
                    var recent = new JsonRecentPeerRepository(directory.resolve("recent" + i + ".json"));
                    frames[i] = new MainFrame(new Settings(), new Scoreboard(List.of()),
                            new JsonSettingsRepository(directory.resolve("settings" + i + ".json")),
                            new JsonScoreboardRepository(directory.resolve("scores.json")),
                            LocalBattleClient::new, () -> MainFrame.socketLobby(recent));
                    frames[i].setVisible(true);
                    frames[i].setLocation(40 + i * 520, 40);
                    frames[i].openBattleModes();
                    frames[i].openP2pLobby();
                }
                lobby(frames[0]).host("0");
            });
            int[] port = new int[1];
            until("host listening", () -> {
                port[0] = ((tetris.feature.network.lobby.SocketLobbyClient) field(lobby(frames[0]), "lobby")).localPort();
                return port[0] > 0;
            });
            edt(() -> lobby(frames[1]).connect("127.0.0.1", String.valueOf(port[0])));
            until("both in LOBBY", () -> lobby(frames[0]).snapshot().state() == State.LOBBY
                    && lobby(frames[1]).snapshot().state() == State.LOBBY && lobby(frames[1]).snapshot().config() != null);

            for (BattleMode mode : BattleMode.values()) {
                edt(() -> lobby(frames[0]).selectMode(mode));
                until(mode + " config on both", () -> lobby(frames[1]).snapshot().config() != null
                        && lobby(frames[1]).snapshot().config().mode() == mode
                        && lobby(frames[0]).snapshot().config().mode() == mode);
                edt(() -> lobby(frames[1]).toggleReady());
                until("host sees the vote", () -> lobby(frames[0]).snapshot().peerReady());
                edt(() -> lobby(frames[0]).toggleReady());
                until(mode + " battle on both", () -> screen(frames[0]) == MainFrame.Screen.BATTLE
                        && screen(frames[1]) == MainFrame.Screen.BATTLE
                        && panel(frames[1]).paintLog().entries().stream()
                            .anyMatch(e -> e.snapshot().phase() == MatchPhase.PLAYING));

                var modeReport = new LinkedHashMap<String, Object>();
                for (int side = 0; side < 2; side++) {
                    PlayerId board = side == 0 ? PlayerId.ONE : PlayerId.TWO;
                    MainFrame actor = frames[side];
                    var own = new ArrayList<Double>();
                    var peer = new ArrayList<Double>();
                    for (int i = 0; i < SAMPLES_PER_DIRECTION; i++) {
                        int code = i % 2 == 0 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
                        long[] t0 = new long[1];
                        int[] expected = new int[1];
                        edt(() -> {
                            var host = (HostBattleClient) field(field(frames[0], "battleController"), "client");
                            int start = firstColumn(host.snapshot().player(board).cells());
                            expected[0] = start + (code == KeyEvent.VK_LEFT ? -1 : 1);
                            var target = panel(actor);
                            var manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                            t0[0] = System.nanoTime();
                            for (int id : new int[]{KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED}) {
                                manager.redispatchEvent(target, new KeyEvent(target, id, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
                            }
                        });
                        until(mode + " " + board + " move painted on both", () ->
                                paintedAt(panel(frames[0]), board, expected[0], t0[0]) > 0
                                && paintedAt(panel(frames[1]), board, expected[0], t0[0]) > 0);
                        edt(() -> {
                            long mine = paintedAt(panel(actor), board, expected[0], t0[0]);
                            long other = paintedAt(panel(frames[1 - actorIndex(frames, actor)]), board, expected[0], t0[0]);
                            own.add((mine - t0[0]) / 1e6);
                            peer.add((other - t0[0]) / 1e6);
                        });
                    }
                    String who = side == 0 ? "hostInput" : "clientInput";
                    var ownStats = stats(own);
                    var peerStats = stats(peer);
                    modeReport.put(who + "_ownScreen", ownStats);
                    modeReport.put(who + "_peerScreen", peerStats);
                    for (var entry : List.of(Map.entry("own", ownStats), Map.entry("peer", peerStats))) {
                        double p95 = (Double) entry.getValue().get("p95Ms");
                        if (p95 > LIMIT_MILLIS) failures.add(mode + " " + who + " " + entry.getKey() + " p95 " + p95 + "ms");
                    }
                }
                var receive = new ArrayList<Double>();
                edt(() -> {
                    for (MainFrame f : frames) {
                        for (PaintLog.Entry e : panel(f).paintLog().entries()) receive.add(e.delayNanos() / 1e6);
                    }
                });
                modeReport.put("stateReceiveToPaint_bothScreens", stats(receive));
                report.put(mode.name(), modeReport);

                // 기권으로 끝내고 같은 연결의 로비로 돌아간다
                edt(() -> ((BattleClient) field(field(frames[0], "battleController"), "client"))
                        .send(new PlayerCommand(PlayerId.ONE, GameAction.QUIT, true, 1_000_000)));
                until(mode + " result on both", () -> screen(frames[0]) == MainFrame.Screen.P2P_RESULT
                        && screen(frames[1]) == MainFrame.Screen.P2P_RESULT);
                edt(() -> {
                    for (MainFrame f : frames) {
                        Method back = MainFrame.class.getDeclaredMethod("backToP2pLobby");
                        back.setAccessible(true);
                        back.invoke(f);
                    }
                });
                until("both back in LOBBY", () -> lobby(frames[0]).snapshot().state() == State.LOBBY
                        && lobby(frames[1]).snapshot().state() == State.LOBBY);
            }
        } finally {
            edt(() -> { for (var f : frames) if (f != null) f.dispose(); });
        }
        var out = new LinkedHashMap<String, Object>();
        out.put("environment", "same PC loopback TCP, Swing key dispatch to paint (no OS input or LAN latency)");
        out.put("javaVersion", System.getProperty("java.version"));
        out.put("os", System.getProperty("os.name"));
        out.put("limitMs", LIMIT_MILLIS);
        out.put("modes", report);
        Path file = Path.of("build/verification/s11-loopback-latency.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json(out));
        assertTrue(failures.isEmpty(), "200ms 초과: " + failures + " (" + file.toAbsolutePath() + ")");
    }

    private static int actorIndex(MainFrame[] frames, MainFrame actor) {
        return frames[0] == actor ? 0 : 1;
    }
}
