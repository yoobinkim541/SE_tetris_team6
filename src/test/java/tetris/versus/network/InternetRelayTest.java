package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.protocol.WireFormat;
import tetris.feature.network.relay.InternetRelay;
import tetris.feature.network.relay.RelayException;
import tetris.feature.network.relay.RelayTransport;

/** 인터넷 대전 중계 연결: 방 코드로 짝을 지은 뒤 두 PC의 게임 TCP 바이트가 그대로 오가는지 (메모리 안의 가짜 중계 서버) */
@Timeout(30)
class InternetRelayTest {
    private static final URI RELAY = URI.create("ws://relay.test/relay");
    private static final long WAIT = 10;

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final AtomicInteger dispatched = new AtomicInteger();
    private final FakeRelayServer server = new FakeRelayServer(network);

    @AfterEach
    void tearDown() {
        network.shutdownNow();
    }

    private InternetRelay relay() {
        return new InternetRelay(RELAY, task -> {
            dispatched.incrementAndGet();
            task.run();
        }, server);
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
    }

    private static byte[] readExactly(InputStream in, int count) throws IOException {
        byte[] bytes = in.readNBytes(count);
        assertEquals(count, bytes.length, "상대가 보낸 바이트가 모두 와야 한다");
        return bytes;
    }

    @Test
    void 방_코드로_짝을_지으면_두_게임_사이에_바이트가_양방향으로_순서대로_오간다() throws Exception {
        try (ServerSocket hostGame = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             InternetRelay host = relay();
             InternetRelay guest = relay()) {
            String code = await(host.openRoom(hostGame.getLocalPort()));
            assertTrue(code.matches("\\d{4}"));

            int loopback = await(guest.join(code));
            try (Socket guestGame = new Socket(InetAddress.getLoopbackAddress(), loopback);
                 Socket hostSide = hostGame.accept()) {
                hostSide.setSoTimeout((int) TimeUnit.SECONDS.toMillis(WAIT));
                guestGame.setSoTimeout((int) TimeUnit.SECONDS.toMillis(WAIT));
                byte[] hello = new byte[20_000];
                for (int i = 0; i < hello.length; i++) hello[i] = (byte) i;
                guestGame.getOutputStream().write(hello);
                guestGame.getOutputStream().flush();
                assertArrayEquals(hello, readExactly(hostSide.getInputStream(), hello.length));

                hostSide.getOutputStream().write("WELCOME".getBytes());
                hostSide.getOutputStream().flush();
                assertArrayEquals("WELCOME".getBytes(), readExactly(guestGame.getInputStream(), 7));
            }
        }
        assertTrue(dispatched.get() >= 2, "결과는 주입한 dispatcher에서 완료된다");
    }

    @Test
    void 참가자가_게임을_닫으면_호스트_게임은_EOF를_받는다() throws Exception {
        try (ServerSocket hostGame = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             InternetRelay host = relay();
             InternetRelay guest = relay()) {
            String code = await(host.openRoom(hostGame.getLocalPort()));
            int loopback = await(guest.join(code));
            Socket guestGame = new Socket(InetAddress.getLoopbackAddress(), loopback);
            try (Socket hostSide = hostGame.accept()) {
                hostSide.setSoTimeout((int) TimeUnit.SECONDS.toMillis(WAIT));
                guestGame.close();
                assertEquals(-1, hostSide.getInputStream().read());
            }
        }
    }

    @Test
    void 없는_방_코드는_NO_ROOM으로_실패한다() {
        try (InternetRelay guest = relay()) {
            ExecutionException failure = assertThrows(ExecutionException.class, () -> await(guest.join("0000")));
            RelayException cause = assertInstanceOf(RelayException.class, failure.getCause());
            assertEquals(RelayException.Reason.NO_ROOM, cause.reason());
        }
    }

    @Test
    void 중계_서버에_접속하지_못하면_UNREACHABLE로_실패한다() {
        RelayTransport down = (uri, timeout, listener) -> CompletableFuture.failedFuture(new IOException("down"));
        try (InternetRelay guest = new InternetRelay(RELAY, Runnable::run, down)) {
            ExecutionException failure = assertThrows(ExecutionException.class, () -> await(guest.join("1234")));
            assertEquals(RelayException.Reason.UNREACHABLE,
                    assertInstanceOf(RelayException.class, failure.getCause()).reason());
        }
    }

    @Test
    void 짝이_지어지기_전에_닫으면_대기_중인_결과는_CLOSED로_끝난다() throws Exception {
        try (ServerSocket hostGame = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            InternetRelay host = relay();
            CompletionStage<String> code = host.openRoom(hostGame.getLocalPort());
            await(code);
            host.close();
            network.submit(() -> { }).get(WAIT, TimeUnit.SECONDS); // 가짜 서버가 닫기 처리를 끝낼 때까지
            assertEquals(0, server.rooms.size(), "호스트가 닫으면 서버의 방도 사라진다");
        }
        InternetRelay guest = relay();
        CompletionStage<Integer> port = guest.join("5555");
        guest.close();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> await(port));
        assertInstanceOf(RelayException.class, failure.getCause());
    }

    @Test
    void 방_코드는_숫자_4자리만_받고_한_객체는_한_번만_쓴다() throws Exception {
        try (InternetRelay guest = relay()) {
            assertThrows(IllegalArgumentException.class, () -> guest.join("12a4"));
            assertThrows(IllegalArgumentException.class, () -> guest.join("123"));
        }
        try (ServerSocket hostGame = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             InternetRelay host = relay()) {
            await(host.openRoom(hostGame.getLocalPort()));
            assertThrows(IllegalStateException.class, () -> host.join("1234"));
            assertThrows(IllegalArgumentException.class, () -> relay().openRoom(0));
        }
    }

    @Test
    void 요청에는_중계_규약_버전과_게임_프레임_버전이_담긴다() throws Exception {
        try (ServerSocket hostGame = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             InternetRelay host = relay()) {
            await(host.openRoom(hostGame.getLocalPort()));
            JsonNode request = server.lastRequest;
            assertEquals("host", request.path("op").asText());
            assertEquals(1, request.path("relay").asInt());
            assertEquals(WireFormat.VERSION, request.path("proto").asInt());
        }
    }

    @Test
    void 메인_중계_서버로_방을_만들고_6글자_코드로_참가해_바이트가_오간다() throws Exception {
        try (tetris.relay.RelayServer main = new tetris.relay.RelayServer(0, tetris.relay.RelayLimits.DEFAULT,
                System::nanoTime, new java.util.Random(7))) {
            main.start();
            var routes = java.util.List.of(InternetRelay.Route.main(URI.create("tcp://127.0.0.1:" + main.localPort())));
            try (ServerSocket hostGame = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                 InternetRelay host = new InternetRelay(routes, Runnable::run);
                 InternetRelay guest = new InternetRelay(routes, Runnable::run)) {
                String shown = await(host.openRoom(hostGame.getLocalPort()));
                assertTrue(shown.matches("[A-Z2-9]{3}-[A-Z2-9]{3}"), "화면에는 3+3으로 보인다: " + shown);

                int loopback = await(guest.join(shown.toLowerCase())); // 소문자·하이픈 그대로 입력해도 된다
                try (Socket guestGame = new Socket(InetAddress.getLoopbackAddress(), loopback);
                     Socket hostSide = hostGame.accept()) {
                    hostSide.setSoTimeout((int) TimeUnit.SECONDS.toMillis(WAIT));
                    guestGame.setSoTimeout((int) TimeUnit.SECONDS.toMillis(WAIT));
                    guestGame.getOutputStream().write("HELLO".getBytes());
                    guestGame.getOutputStream().flush();
                    assertArrayEquals("HELLO".getBytes(), readExactly(hostSide.getInputStream(), 5));
                    hostSide.getOutputStream().write("WELCOME".getBytes());
                    hostSide.getOutputStream().flush();
                    assertArrayEquals("WELCOME".getBytes(), readExactly(guestGame.getInputStream(), 7));
                }
            }
        }
    }

    @Test
    void 메인_서버에_닿지_않으면_호스트는_예비_서버에서_방을_만든다() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        var routes = java.util.List.of(
                InternetRelay.Route.main(URI.create("tcp://127.0.0.1:" + closedPort)),
                InternetRelay.Route.fallback(RELAY, server));
        try (ServerSocket hostGame = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             InternetRelay host = new InternetRelay(routes, Runnable::run)) {
            assertTrue(await(host.openRoom(hostGame.getLocalPort())).matches("\\d{4}"),
                    "예비 서버 코드는 숫자 4자리라 참가자도 같은 서버로 간다");
        }
    }

    @Test
    void 참가자는_코드_모양으로_서버를_고른다() {
        java.util.List<URI> opened = new java.util.ArrayList<>();
        RelayTransport recording = (uri, timeout, listener) -> {
            opened.add(uri);
            return CompletableFuture.failedFuture(new IOException("stop here"));
        };
        URI mainUri = URI.create("tcp://main.test:47123");
        var routes = java.util.List.of(
                new InternetRelay.Route("메인", mainUri, java.time.Duration.ofSeconds(1), recording,
                        tetris.relay.LobbyCode::normalize, tetris.relay.LobbyCode::display),
                InternetRelay.Route.fallback(RELAY, recording));
        try (InternetRelay a = new InternetRelay(routes, Runnable::run);
             InternetRelay b = new InternetRelay(routes, Runnable::run)) {
            assertTrue(a.accepts("k7q-m2x") && a.accepts("4821"));
            assertTrue(!a.accepts("12a") && !a.accepts("K7QM2"));
            a.join("K7Q-M2X");
            b.join("4821");
            assertEquals(java.util.List.of(mainUri, RELAY), opened);
        }
    }

    /** tetris_relay.py와 같은 규칙의 메모리 안 중계 서버. 콜백은 network 스레드 하나에서 순서대로 보낸다 */
    private static final class FakeRelayServer implements RelayTransport {
        private static final ObjectMapper JSON = new ObjectMapper();
        private final ExecutorService network;
        final Map<String, FakeChannel> rooms = new HashMap<>();
        volatile JsonNode lastRequest;
        private int nextCode = 4821;

        FakeRelayServer(ExecutorService network) {
            this.network = network;
        }

        @Override
        public CompletableFuture<Channel> open(URI uri, java.time.Duration timeout, Listener listener) {
            return CompletableFuture.completedFuture(new FakeChannel(listener));
        }

        private final class FakeChannel implements Channel {
            final Listener listener;
            FakeChannel peer;
            String hostedCode;
            boolean closed;

            FakeChannel(Listener listener) {
                this.listener = listener;
            }

            @Override
            public CompletableFuture<Void> sendText(String text) {
                return CompletableFuture.runAsync(() -> {
                    JsonNode request;
                    try {
                        request = JSON.readTree(text);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    lastRequest = request;
                    if (request.path("op").asText().equals("host")) {
                        hostedCode = String.valueOf(nextCode++);
                        rooms.put(hostedCode, this);
                        listener.onText("{\"op\":\"room\",\"code\":\"" + hostedCode + "\",\"expiresIn\":600}");
                        return;
                    }
                    FakeChannel host = rooms.remove(request.path("code").asText());
                    if (host == null) {
                        listener.onText("{\"op\":\"error\",\"reason\":\"NO_ROOM\"}");
                        closeFromServer();
                        return;
                    }
                    host.peer = this;
                    peer = host;
                    host.listener.onText("{\"op\":\"paired\"}");
                    listener.onText("{\"op\":\"paired\"}");
                }, network);
            }

            @Override
            public CompletableFuture<Void> sendBinary(byte[] bytes) {
                byte[] copy = bytes.clone();
                return CompletableFuture.runAsync(() -> {
                    if (peer != null && !peer.closed) peer.listener.onBinary(copy);
                }, network);
            }

            @Override
            public void close() {
                network.execute(() -> {
                    if (closed) return;
                    closeFromServer();
                    if (peer != null) peer.closeFromServer();
                });
            }

            private void closeFromServer() {
                if (closed) return;
                closed = true;
                if (hostedCode != null) rooms.remove(hostedCode);
                listener.onClosed("closed");
            }
        }
    }
}
