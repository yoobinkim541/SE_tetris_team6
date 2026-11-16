package tetris.versus.ui;

import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.lobby.SocketLobbyClient;
import tetris.feature.network.tools.LagProxy;
import tetris.feature.network.tools.LatencyCsv;
import tetris.feature.network.tools.LatencySample;
import tetris.feature.network.tools.LatencyStats;
import tetris.feature.save.JsonRecentPeerRepository;
import tetris.feature.save.JsonScoreboardRepository;
import tetris.feature.save.JsonSettingsRepository;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.*;
import tetris.ui.MainFrame;
import tetris.ui.versus.BattlePanel;
import tetris.ui.versus.LocalBattleClient;
import tetris.ui.versus.P2pLobbyController;
import tetris.ui.versus.PaintLog;

/**
 * S11-C1 보조 측정 (R3-LATENCY): 같은 PC에서 <b>JVM 프로세스 두 개</b>가 각자 실제 게임 창을 띄우고,
 * 클라이언트가 호스트의 LAN 주소로 접속해 3모드 × 2방향 × N입력의 "키 이벤트 → 두 화면이 그린 시각"을 잰다.
 *
 * <p>두 PC 측정이 아니다. Windows는 같은 PC의 LAN 주소로 가는 TCP를 내부에서 처리하므로 랜선·공유기 지연이 없다.
 * 대신 프로세스·EDT·TCP 스택이 분리되고, 두 프로세스의 System.nanoTime(QPC) 차이를 직접 재서 시계 오차로 기록한다.
 * CI에서 돌지 않는 측정 도구다. 실행:
 * <pre>gradlew testClasses
 * java -cp build/classes/java/main;build/classes/java/test;build/resources/main tetris.versus.ui.TwoProcessLatencyHarness [입력수] [출력폴더]</pre>
 */
public final class TwoProcessLatencyHarness {
    private static final double LIMIT_MILLIS = 200;
    private static final long PAINT_TIMEOUT_MILLIS = 2_000;

    private TwoProcessLatencyHarness() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("agent")) { Agent.run(Integer.parseInt(args[1])); return; }
        int perDirection = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        Path out = Path.of(args.length > 1 ? args[1] : "build/verification/s11-two-process");
        Files.createDirectories(out);
        // 세 번째 인자 > 0이면 LagProxy를 끼워 방향마다 그만큼 지연을 주입한다. 정상 측정과 결과를 섞지 않는다
        long injectedDelay = args.length > 2 ? Long.parseLong(args[2]) : 0;
        String lan = lanAddress();
        System.out.println("LAN 주소: " + lan + ", 방향당 입력 " + perDirection + ", 주입 지연(방향당) " + injectedDelay + " ms");
        LagProxy proxy = null;
        try (Peer host = Peer.spawn(0); Peer client = Peer.spawn(1)) {
            double[] offset = new double[2];
            double clockError = measureClocks(host, client, offset);
            System.out.printf(Locale.ROOT, "프로세스 nanoTime 기준점 차이(호스트-클라이언트, 보정함) %.4f ms, 보정 불확도 ±%.4f ms%n",
                    offset[0] - offset[1], clockError);
            String port = host.ask("HOST");
            if (injectedDelay > 0) {
                proxy = new LagProxy(0, new InetSocketAddress(lan, Integer.parseInt(port)), injectedDelay, -1);
                proxy.start();
                port = String.valueOf(proxy.localPort());
            }
            client.ask("CONNECT " + lan + " " + port);
            waitFor("양쪽 로비", () -> host.ask("STATE").startsWith("LOBBY") && client.ask("STATE").startsWith("LOBBY"));

            List<String> report = new ArrayList<>();
            List<Double> allOwn = new ArrayList<>(), allPeer = new ArrayList<>();
            int lost = 0;
            long base = System.nanoTime();
            for (BattleMode mode : BattleMode.values()) {
                List<LatencySample> samples = new ArrayList<>();
                for (int side = 0; side < 2; side++) {
                    startMatch(host, client, mode);
                    Peer actor = side == 0 ? host : client, other = side == 0 ? client : host;
                    PlayerId board = side == 0 ? PlayerId.ONE : PlayerId.TWO;
                    String direction = side == 0 ? "host->client" : "client->host";
                    for (int i = 0; i < perDirection; i++) {
                        int key = i % 2 == 0 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
                        String[] input = actor.ask("INPUT " + board + " " + key).split(" ");
                        long t0 = Long.parseLong(input[0]);
                        String paint = "PAINT " + board + " " + input[1] + " " + t0 + " " + PAINT_TIMEOUT_MILLIS;
                        actor.send(paint); other.send(paint);
                        long mine = Long.parseLong(actor.read()), theirs = Long.parseLong(other.read());
                        if (mine < 0 || theirs < 0) { lost++; continue; }
                        double a = offset[actor == host ? 0 : 1], b = offset[other == host ? 0 : 1];
                        long input0 = Math.round(t0 - a) - base;
                        long own = Math.max(input0, Math.round(mine - a) - base);
                        long peer = Math.max(input0, Math.round(theirs - b) - base);
                        samples.add(new LatencySample(mode.name(), direction, i + 1, input0, own, peer));
                        Thread.sleep(30);
                    }
                    endMatch(host, client);
                }
                LatencyCsv.write(out.resolve("s11-two-process-" + mode.name().toLowerCase(Locale.ROOT) + ".csv"), samples);
                for (String direction : List.of("host->client", "client->host")) {
                    List<Double> own = new ArrayList<>(), peer = new ArrayList<>();
                    for (LatencySample s : samples) if (s.direction().equals(direction)) {
                        own.add(s.localMillis()); peer.add(s.remoteMillis());
                    }
                    allOwn.addAll(own); allPeer.addAll(peer);
                    report.add(line(mode.name(), direction, "입력한 화면", LatencyStats.of(own, LIMIT_MILLIS)));
                    report.add(line(mode.name(), direction, "상대 화면", LatencyStats.of(peer, LIMIT_MILLIS)));
                }
            }
            report.add(line("전체", "양방향", "입력한 화면", LatencyStats.of(allOwn, LIMIT_MILLIS)));
            report.add(line("전체", "양방향", "상대 화면", LatencyStats.of(allPeer, LIMIT_MILLIS)));
            List<String> summary = new ArrayList<>();
            summary.add("환경: 같은 PC, JVM 2개, 클라이언트→호스트 LAN 주소 " + lan + " (OS 내부 경로, 랜선 없음)");
            summary.add(injectedDelay > 0 ? "지연 주입: LagProxy 방향당 " + injectedDelay + " ms (정상 LAN 결과 아님)" : "지연 주입 없음");
            summary.add("Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name"));
            summary.add(String.format(Locale.ROOT, "nanoTime 기준점 차이 %.4f ms(보정함), 보정 불확도 ±%.4f ms", offset[0] - offset[1], clockError));
            summary.add("유실 표본(2초 안에 두 화면 중 하나라도 못 그림): " + lost);
            summary.add("모드,방향,화면,표본,p50ms,p95ms,최대ms,200ms초과");
            summary.addAll(report);
            Files.write(out.resolve("s11-two-process-summary.txt"), summary, StandardCharsets.UTF_8);
            summary.forEach(System.out::println);
        } finally {
            if (proxy != null) proxy.close();
        }
    }

    private static String line(String mode, String direction, String screen, LatencyStats s) {
        return String.format(Locale.ROOT, "%s,%s,%s,%d,%.2f,%.2f,%.2f,%d", mode, direction, screen, s.count(), s.p50(), s.p95(), s.max(), s.overThreshold());
    }

    /** 드라이버 nanoTime 기준 각 프로세스의 시계 차이를 50번 왕복으로 잰다. 반환값은 가장 짧은 왕복의 절반(불확도) */
    private static double measureClocks(Peer host, Peer client, double[] offset) throws IOException {
        double error = 0;
        Peer[] peers = {host, client};
        for (int p = 0; p < 2; p++) {
            long bestRoundTrip = Long.MAX_VALUE;
            for (int i = 0; i < 50; i++) {
                long before = System.nanoTime();
                long remote = Long.parseLong(peers[p].ask("NOW"));
                long after = System.nanoTime();
                if (after - before < bestRoundTrip) {
                    bestRoundTrip = after - before;
                    offset[p] = remote - (before + after) / 2.0;
                }
            }
            error += bestRoundTrip / 2.0 / 1e6;
        }
        return error;
    }

    private static void startMatch(Peer host, Peer client, BattleMode mode) throws Exception {
        host.ask("MODE " + mode);
        waitFor(mode + " 설정", () -> client.ask("STATE").equals("LOBBY " + mode) && host.ask("STATE").equals("LOBBY " + mode));
        client.ask("READY");
        waitFor("호스트가 준비 확인", () -> host.ask("PEER_READY").equals("true"));
        host.ask("READY");
        waitFor(mode + " 경기 시작", () -> host.ask("PLAYING").equals("true") && client.ask("PLAYING").equals("true"));
    }

    private static void endMatch(Peer host, Peer client) throws Exception {
        host.ask("FORFEIT");
        waitFor("양쪽 결과", () -> host.ask("SCREEN").equals("P2P_RESULT") && client.ask("SCREEN").equals("P2P_RESULT"));
        host.ask("BACK");
        client.ask("BACK");
        waitFor("양쪽 로비 복귀", () -> host.ask("STATE").startsWith("LOBBY") && client.ask("STATE").startsWith("LOBBY"));
    }

    private interface Check { boolean ok() throws Exception; }

    private static void waitFor(String what, Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (check.ok()) return;
            Thread.sleep(20);
        }
        throw new IllegalStateException("시간 초과: " + what);
    }

    /** 사설망 IPv4 중 켜져 있는 실제 인터페이스 주소 */
    static String lanAddress() throws IOException {
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
            String name = nic.getDisplayName().toLowerCase(Locale.ROOT);
            if (name.contains("virtual") || name.contains("vmware") || name.contains("hyper-v") || name.contains("wsl")) continue;
            for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) return address.getHostAddress();
            }
        }
        throw new IOException("사설망 IPv4 주소가 없습니다");
    }

    /** 드라이버 쪽에서 본 에이전트 프로세스 */
    private static final class Peer implements AutoCloseable {
        private final Process process;
        private final BufferedWriter in;
        private final BufferedReader out;

        private Peer(Process process) {
            this.process = process;
            this.in = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        }

        static Peer spawn(int index) throws IOException {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            var builder = new ProcessBuilder(java, "-Djava.awt.headless=false", "-Dfile.encoding=UTF-8",
                    "-cp", System.getProperty("java.class.path"), TwoProcessLatencyHarness.class.getName(),
                    "agent", String.valueOf(index));
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            return new Peer(builder.start());
        }

        void send(String command) throws IOException { in.write(command); in.newLine(); in.flush(); }

        String read() throws IOException {
            String line = out.readLine();
            if (line == null) throw new IOException("에이전트가 종료됨");
            if (line.startsWith("ERR ")) throw new IOException(line);
            return line.startsWith("OK ") ? line.substring(3) : line.equals("OK") ? "" : line;
        }

        String ask(String command) throws IOException { send(command); return read(); }

        @Override public void close() throws Exception {
            try { send("EXIT"); } catch (IOException ignored) { }
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }

    /** 실제 게임 창 하나를 띄우고 표준입력 명령을 EDT에서 실행한다 */
    static final class Agent {
        private final MainFrame frame;

        private Agent(MainFrame frame) { this.frame = frame; }

        static void run(int index) throws Exception {
            PrintStream reply = new PrintStream(System.out, true, StandardCharsets.UTF_8);
            System.setOut(new PrintStream(System.err, true, StandardCharsets.UTF_8)); // 앱 로그가 응답 줄을 섞지 않게
            Path directory = Files.createTempDirectory("s11-agent" + index);
            MainFrame[] holder = new MainFrame[1];
            SwingUtilities.invokeAndWait(() -> {
                var recent = new JsonRecentPeerRepository(directory.resolve("recent.json"));
                holder[0] = new MainFrame(new Settings(), new Scoreboard(List.of()),
                        new JsonSettingsRepository(directory.resolve("settings.json")),
                        new JsonScoreboardRepository(directory.resolve("scores.json")),
                        LocalBattleClient::new, () -> MainFrame.socketLobby(recent));
                holder[0].setLocation(40 + index * 560, 40);
                holder[0].setVisible(true);
                holder[0].openBattleModes();
                holder[0].openP2pLobby();
            });
            Agent agent = new Agent(holder[0]);
            var commands = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            for (String line; (line = commands.readLine()) != null; ) {
                if (line.equals("EXIT")) break;
                try { reply.println("OK " + agent.handle(line.split(" "))); }
                catch (Exception e) { reply.println("ERR " + e); }
            }
            SwingUtilities.invokeAndWait(() -> holder[0].dispose());
            System.exit(0);
        }

        private String handle(String[] c) throws Exception {
            if (c[0].equals("NOW")) return String.valueOf(System.nanoTime());
            if (c[0].equals("PAINT")) return String.valueOf(awaitPaint(PlayerId.valueOf(c[1]), Integer.parseInt(c[2]),
                    Long.parseLong(c[3]), Long.parseLong(c[4])));
            String[] result = new String[1];
            SwingUtilities.invokeAndWait(() -> {
                try { result[0] = onEdt(c); } catch (Exception e) { throw new RuntimeException(e); }
            });
            return result[0];
        }

        private String onEdt(String[] c) throws Exception {
            P2pLobbyController lobby = (P2pLobbyController) field(frame, "p2p");
            switch (c[0]) {
                case "HOST" -> {
                    lobby.host("0");
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    int port = 0;
                    while (port == 0 && System.nanoTime() < deadline) port = ((SocketLobbyClient) field(lobby, "lobby")).localPort();
                    return String.valueOf(port);
                }
                case "CONNECT" -> { lobby.connect(c[1], c[2]); return ""; }
                case "STATE" -> {
                    var s = lobby.snapshot();
                    return s.state() == State.LOBBY && s.config() != null ? "LOBBY " + s.config().mode() : s.state().name();
                }
                case "MODE" -> { lobby.selectMode(BattleMode.valueOf(c[1])); return ""; }
                case "READY" -> { lobby.toggleReady(); return ""; }
                case "PEER_READY" -> { return String.valueOf(lobby.snapshot().peerReady()); }
                case "SCREEN" -> { return field(frame, "currentScreen").toString(); }
                case "PLAYING" -> {
                    if (!field(frame, "currentScreen").toString().equals("BATTLE")) return "false";
                    var entries = panel().paintLog().entries();
                    return String.valueOf(!entries.isEmpty() && entries.getLast().snapshot().phase() == MatchPhase.PLAYING);
                }
                case "INPUT" -> {
                    PlayerId board = PlayerId.valueOf(c[1]);
                    int key = Integer.parseInt(c[2]);
                    var entries = panel().paintLog().entries();
                    int start = firstColumn(entries.getLast().snapshot().player(board).cells());
                    int expected = start + (key == KeyEvent.VK_LEFT ? -1 : 1);
                    BattlePanel target = panel();
                    var manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                    long t0 = System.nanoTime();
                    for (int id : new int[]{KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED})
                        manager.redispatchEvent(target, new KeyEvent(target, id, System.currentTimeMillis(), 0, key, KeyEvent.CHAR_UNDEFINED));
                    return t0 + " " + expected;
                }
                case "FORFEIT" -> {
                    ((BattleClient) field(field(frame, "battleController"), "client"))
                            .send(new PlayerCommand(PlayerId.ONE, GameAction.QUIT, true, 1_000_000));
                    return "";
                }
                case "BACK" -> {
                    Method back = MainFrame.class.getDeclaredMethod("backToP2pLobby");
                    back.setAccessible(true);
                    back.invoke(frame);
                    return "";
                }
                default -> throw new IllegalArgumentException("알 수 없는 명령 " + c[0]);
            }
        }

        /** t0 이후 이 화면이 board의 블록을 expected 열에 처음 그린 시각. 시간 안에 없으면 -1 */
        private long awaitPaint(PlayerId board, int expected, long t0, long timeoutMillis) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            long[] found = {-1};
            while (System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        for (PaintLog.Entry e : panel().paintLog().entries()) {
                            if (e.paintedNanos() >= t0 && firstColumn(e.snapshot().player(board).cells()) == expected) {
                                found[0] = e.paintedNanos();
                                return;
                            }
                        }
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
                if (found[0] >= 0) return found[0];
                Thread.sleep(2);
            }
            return -1;
        }

        private BattlePanel panel() throws Exception {
            return (BattlePanel) ((Map<?, ?>) field(frame, "screens")).get(MainFrame.Screen.BATTLE);
        }
    }

    static int firstColumn(int[][] cells) {
        int best = Integer.MAX_VALUE;
        for (int[] row : cells) for (int c = 0; c < row.length; c++) if (row[c] != 0) best = Math.min(best, c);
        return best;
    }

    static Object field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException next) { /* 상위 클래스 */ }
        }
        throw new NoSuchFieldException(name);
    }
}
