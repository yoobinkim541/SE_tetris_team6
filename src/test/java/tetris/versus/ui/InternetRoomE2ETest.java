package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.lobby.SocketLobbyClient;
import tetris.feature.network.relay.InternetRelay;
import tetris.feature.network.relay.JdkRelayTransport;
import tetris.relay.RelayLimits;
import tetris.relay.RelayRoomMode;
import tetris.relay.RelayServer;
import tetris.ui.versus.P2pLobbyController;
import tetris.ui.versus.P2pLobbyPanel;

/** 로컬 메인·예비 서버에서 공개/비공개 방이 목록에 보이고 게임 핸드셰이크까지 이어지는지 */
@Timeout(60)
class InternetRoomE2ETest {
    @Test
    void 공개_방과_비공개_방이_목록을_거쳐_로비까지_연결된다() throws Exception {
        RelayServer server = new RelayServer(0, generous(), System::nanoTime, new java.security.SecureRandom());
        server.start();
        int pythonPort = freePort();
        Process python = startPython(pythonPort);
        try {
            waitForPort(pythonPort);
            List<InternetRelay.Route> routes = List.of(
                    InternetRelay.Route.main(URI.create("tcp://127.0.0.1:" + server.localPort())),
                    InternetRelay.Route.fallback(URI.create("ws://127.0.0.1:" + pythonPort + "/relay"), new JdkRelayTransport()));
            play(routes, "공개방", true, RelayRoomMode.CLASSIC);
            play(routes, "비밀방", false, RelayRoomMode.ITEM);
        } finally {
            python.destroyForcibly();
            server.close();
        }
    }

    @Test
    void 메인이_v2를_거절하면_예비로_넘어가고_v1_방은_목록에_없다() throws Exception {
        RelayServer server = new RelayServer(0, generous(), System::nanoTime, new java.security.SecureRandom());
        server.start();
        try (Socket host = new Socket("127.0.0.1", server.localPort())) {
            host.setSoTimeout(5_000);
            tetris.relay.RelayLine.write(host.getOutputStream(), "HOST v1");
            String room = tetris.relay.RelayLine.read(host.getInputStream());
            assertTrue(room.startsWith("ROOM "), room);
            try (Socket list = new Socket("127.0.0.1", server.localPort())) {
                list.setSoTimeout(5_000);
                tetris.relay.RelayLine.write(list.getOutputStream(), "LIST v2");
                assertEquals("END", tetris.relay.RelayLine.read(list.getInputStream()));
            }
        }
        int errPort = freePort();
        Thread err = errServer(errPort);
        int pythonPort = freePort();
        Process python = startPython(pythonPort);
        try {
            waitForPort(pythonPort);
            List<InternetRelay.Route> routes = List.of(
                    InternetRelay.Route.main(URI.create("tcp://127.0.0.1:" + errPort)),
                    InternetRelay.Route.fallback(URI.create("ws://127.0.0.1:" + pythonPort + "/relay"), new JdkRelayTransport()));
            play(routes, "예비방", true, RelayRoomMode.TIMED);
        } finally {
            python.destroyForcibly();
            err.interrupt();
            server.close();
        }
    }

    private static RelayLimits generous() {
        return new RelayLimits(300, 64, 1000, java.time.Duration.ofSeconds(5), 100, java.time.Duration.ofMinutes(10), 100,
                java.time.Duration.ofSeconds(60), java.time.Duration.ofSeconds(10), java.time.Duration.ofHours(3), 100, 50, 20);
    }

    private static Thread errServer(int port) throws Exception {
        java.net.ServerSocket server = new java.net.ServerSocket(port, 16, java.net.InetAddress.getLoopbackAddress());
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !server.isClosed()) {
                try {
                    Socket socket = server.accept();
                    Thread.ofVirtual().start(() -> {
                        try {
                            tetris.relay.RelayLine.read(socket.getInputStream());
                            tetris.relay.RelayLine.write(socket.getOutputStream(), "ERR BAD_REQUEST");
                        } catch (Exception ignored) {
                            // 목록 연결은 답을 받기 전에 닫히기도 한다
                        } finally {
                            try { socket.close(); } catch (Exception ignored) { }
                        }
                    });
                } catch (Exception closed) {
                    return;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.close(); } catch (Exception ignored) { } }));
        return thread;
    }

    private static void play(List<InternetRelay.Route> routes, String name, boolean isPublic, RelayRoomMode mode) throws Exception {
        SocketLobbyClient hostLobby = new SocketLobbyClient(SwingUtilities::invokeLater);
        SocketLobbyClient guestLobby = new SocketLobbyClient(SwingUtilities::invokeLater);
        P2pLobbyControllerTest.Nav hostNav = new P2pLobbyControllerTest.Nav();
        P2pLobbyControllerTest.Nav guestNav = new P2pLobbyControllerTest.Nav();
        P2pLobbyController host = new P2pLobbyController(hostLobby, hostNav, hostLobby::localPort, List::of, List::of,
                () -> new InternetRelay(routes, SwingUtilities::invokeLater));
        P2pLobbyController guest = new P2pLobbyController(guestLobby, guestNav, () -> -1, List::of, List::of,
                () -> new InternetRelay(routes, SwingUtilities::invokeLater));
        try {
            SwingUtilities.invokeAndWait(() -> {
                host.panel().prepareCreate(name, isPublic, mode);
                host.hostInternet();
            });
            await("host room code", () -> host.panel().statusText().contains("방 코드"), () -> host.panel().statusText());
            String status = host.panel().statusText();
            assertTrue(status.contains(name) || status.contains("방 코드"), status);
            SwingUtilities.invokeAndWait(guest::refreshRooms);
            await("guest list", () -> guest.panel().listedRooms().stream().anyMatch(room -> name.equals(room.name())),
                    () -> host.panel().statusText() + " | " + guest.panel().listedRooms());
            var listed = guest.panel().listedRooms().stream().filter(room -> name.equals(room.name())).findFirst().orElseThrow();
            assertEquals(isPublic, !listed.privateRoom());
            if (isPublic) {
                SwingUtilities.invokeAndWait(() -> guest.joinPublic(listed.routeIndex(), listed.id()));
            } else {
                String code = codeFrom(host.panel().statusText());
                SwingUtilities.invokeAndWait(() -> {
                    guest.panel().codeField().setText("AAAAAA");
                    guest.panel().joinInternetButton().doClick();
                });
                await("wrong code", () -> guest.panel().isError() && guest.panel().statusText().contains("방 코드가 없거나"),
                        () -> guest.panel().statusText());
                SwingUtilities.invokeAndWait(() -> {
                    guest.panel().codeField().setText(code);
                    guest.panel().joinInternetButton().doClick();
                });
            }
            await("both in lobby", () -> host.snapshot().state() == State.LOBBY && guest.snapshot().state() == State.LOBBY,
                    () -> host.snapshot().state() + "/" + guest.snapshot().state() + " " + host.panel().statusText()
                            + " | " + guest.panel().statusText());
            assertFalse(host.panel().peerText().isBlank(), host.panel().peerText());
            assertFalse(guest.panel().peerText().isBlank(), guest.panel().peerText());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                host.close();
                guest.close();
            });
        }
    }

    private static String codeFrom(String status) {
        int start = status.indexOf("방 코드 ");
        assertTrue(start >= 0, status);
        String rest = status.substring(start + "방 코드 ".length()).trim();
        int end = rest.indexOf(' ');
        return end < 0 ? rest : rest.substring(0, end);
    }

    private static void await(String what, java.util.function.BooleanSupplier condition,
                              java.util.function.Supplier<String> detail) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what + ": " + detail.get());
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(30);
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Process startPython(int port) throws Exception {
        Path python = Path.of("relay/.venv/Scripts/python.exe");
        if (!python.toFile().exists()) python = Path.of("relay/.venv/bin/python");
        return new ProcessBuilder(python.toString(), "tetris_relay.py", "--host", "127.0.0.1", "--port", String.valueOf(port))
                .directory(Path.of("relay").toFile())
                .redirectErrorStream(true)
                .start();
    }

    private static void waitForPort(int port) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return;
            } catch (Exception ignored) {
                Thread.sleep(50);
            }
        }
        throw new AssertionError("python relay did not open " + port);
    }
}
