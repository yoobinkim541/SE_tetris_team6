package tetris.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** 로비 코드 중계 서버: 짝짓기, 바이트 보존, 오류, 자원 제한, 만료 (실제 loopback 소켓, 가짜 시계) */
@Timeout(30)
class RelayServerTest {
    private final AtomicLong now = new AtomicLong();
    private RelayServer server;
    private final List<Socket> sockets = new ArrayList<>();

    private RelayServer start(RelayLimits limits) throws IOException {
        server = new RelayServer(0, limits, now::get, new Random(1));
        server.start();
        return server;
    }

    @BeforeEach
    void startDefault() throws IOException {
        start(RelayLimits.DEFAULT);
    }

    @AfterEach
    void stop() throws IOException {
        for (Socket s : sockets) s.close();
        if (server != null) server.close();
    }

    private Socket open() throws IOException {
        Socket s = new Socket(InetAddress.getLoopbackAddress(), server.localPort());
        s.setSoTimeout(5_000);
        sockets.add(s);
        return s;
    }

    private static void send(Socket s, String line) throws IOException {
        RelayLine.write(s.getOutputStream(), line);
    }

    private static String reply(Socket s) throws IOException {
        return RelayLine.read(s.getInputStream());
    }

    private static void eventually(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("timed out waiting for " + what);
            Thread.sleep(5);
        }
    }

    private static boolean closedByPeer(Socket s) {
        try {
            return s.getInputStream().read() == -1;
        } catch (IOException e) {
            return true;
        }
    }

    private String hostRoom(Socket host) throws IOException {
        send(host, "HOST v1");
        String line = reply(host);
        assertTrue(line.startsWith("ROOM "), line);
        return line.substring(5);
    }

    private Socket[] pair() throws IOException {
        Socket host = open();
        String code = hostRoom(host);
        Socket client = open();
        send(client, "JOIN v1 " + code);
        assertEquals("OK", reply(client));
        assertEquals("PAIRED", reply(host));
        return new Socket[]{host, client};
    }

    private void advance(Duration d) {
        now.addAndGet(d.toNanos());
        server.sweep();
    }

    //#region 짝짓기
    @Test
    void 호스트는_로비_코드를_받는다() throws IOException {
        String code = hostRoom(open());
        assertTrue(LobbyCode.normalize(code).isPresent(), code);
        assertEquals(1, server.waitingRooms());
    }

    @Test
    void 코드로_입장하면_짝지어지고_바이트를_양방향으로_그대로_넘긴다() throws Exception {
        Socket[] p = pair();
        p[0].getOutputStream().write(new byte[]{1, 2, 3});
        p[1].getOutputStream().write(new byte[]{9, 8});
        assertArrayEquals(new byte[]{1, 2, 3}, p[1].getInputStream().readNBytes(3));
        assertArrayEquals(new byte[]{9, 8}, p[0].getInputStream().readNBytes(2));
        eventually(() -> server.pairedRooms() == 1 && server.waitingRooms() == 0, "paired");
    }

    @Test
    void 소문자_하이픈_코드도_입장한다() throws IOException {
        Socket host = open();
        String code = hostRoom(host);
        Socket client = open();
        send(client, "JOIN v1 " + LobbyCode.display(code).toLowerCase());
        assertEquals("OK", reply(client));
    }

    @Test
    void JOIN_줄_뒤에_붙어_온_바이트도_잃지_않는다() throws IOException {
        Socket host = open();
        String code = hostRoom(host);
        Socket client = open();
        OutputStream out = client.getOutputStream();
        byte[] join = ("JOIN v1 " + code + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] both = new byte[join.length + 4];
        System.arraycopy(join, 0, both, 0, join.length);
        both[join.length] = 4; both[join.length + 1] = 5; both[join.length + 2] = 6; both[join.length + 3] = 7;
        out.write(both);
        out.flush();
        assertEquals("OK", reply(client));
        assertEquals("PAIRED", reply(host));
        assertArrayEquals(new byte[]{4, 5, 6, 7}, host.getInputStream().readNBytes(4));
    }

    @Test
    void 한_바이트씩_나눠_온_JOIN도_받는다() throws Exception {
        Socket host = open();
        String code = hostRoom(host);
        Socket client = open();
        OutputStream out = client.getOutputStream();
        for (byte b : ("JOIN v1 " + code + "\n").getBytes(StandardCharsets.UTF_8)) {
            out.write(b);
            out.flush();
        }
        assertEquals("OK", reply(client));
        assertEquals("PAIRED", reply(host));
    }

    @Test
    void 없는_코드는_NO_ROOM이고_연결을_닫는다() throws IOException {
        Socket client = open();
        send(client, "JOIN v1 AAAAAA");
        assertEquals("ERR NO_ROOM", reply(client));
        assertTrue(closedByPeer(client));
    }

    @Test
    void 이미_짝지어진_방은_ROOM_FULL() throws IOException {
        Socket host = open();
        String code = hostRoom(host);
        Socket first = open();
        send(first, "JOIN v1 " + code);
        assertEquals("OK", reply(first));
        Socket second = open();
        send(second, "JOIN v1 " + code);
        assertEquals("ERR ROOM_FULL", reply(second));
    }

    @Test
    void 동시에_두_명이_입장하면_한_명만_OK() throws Exception {
        Socket host = open();
        String code = hostRoom(host);
        Socket a = open();
        Socket b = open();
        CountDownLatch go = new CountDownLatch(1);
        String[] replies = new String[2];
        Thread[] threads = new Thread[2];
        Socket[] joiners = {a, b};
        for (int i = 0; i < 2; i++) {
            int k = i;
            threads[i] = Thread.ofVirtual().start(() -> {
                try {
                    go.await();
                    send(joiners[k], "JOIN v1 " + code);
                    replies[k] = reply(joiners[k]);
                } catch (Exception e) {
                    replies[k] = "EX " + e;
                }
            });
        }
        go.countDown();
        for (Thread th : threads) th.join();
        assertEquals(1, List.of(replies).stream().filter("OK"::equals).count(), List.of(replies).toString());
        assertEquals(1, List.of(replies).stream().filter("ERR ROOM_FULL"::equals).count(), List.of(replies).toString());
    }

    @Test
    void 호스트가_나가면_방이_사라진다() throws Exception {
        Socket host = open();
        String code = hostRoom(host);
        host.close();
        eventually(() -> server.waitingRooms() == 0, "room removed");
        Socket client = open();
        send(client, "JOIN v1 " + code);
        assertEquals("ERR NO_ROOM", reply(client));
    }

    @Test
    void 한쪽이_끊기면_상대도_닫히고_방이_정리된다() throws Exception {
        Socket[] p = pair();
        p[0].close();
        assertTrue(closedByPeer(p[1]));
        eventually(() -> server.pairedRooms() == 0 && server.openConnections() == 0, "cleanup");
    }

    @Test
    void 짝_맺기_전에_호스트가_데이터를_보내면_방을_닫는다() throws Exception {
        Socket host = open();
        hostRoom(host);
        host.getOutputStream().write(1);
        assertTrue(closedByPeer(host));
        eventually(() -> server.waitingRooms() == 0, "room removed");
    }
    //#endregion

    //#region 잘못된 요청
    @Test
    void 모르는_명령과_버전은_BAD_REQUEST() throws IOException {
        Socket a = open();
        send(a, "HELLO");
        assertEquals("ERR BAD_REQUEST", reply(a));
        Socket b = open();
        send(b, "HOST v2");
        assertEquals("ERR BAD_REQUEST", reply(b));
        Socket c = open();
        send(c, "JOIN v1 0O1IL0");
        assertEquals("ERR BAD_REQUEST", reply(c));
    }

    @Test
    void 줄이_64바이트를_넘으면_바로_끊는다() throws IOException {
        Socket a = open();
        a.getOutputStream().write("X".repeat(RelayLine.MAX_BYTES + 10).getBytes(StandardCharsets.UTF_8));
        a.getOutputStream().flush();
        String line;
        try {
            line = reply(a);
        } catch (IOException closedWithoutReply) {
            line = null;
        }
        assertTrue(line == null || line.equals("ERR BAD_REQUEST"), line);
        assertTrue(closedByPeer(a));
    }
    //#endregion

    //#region 시간 제한 (가짜 시계)
    @Test
    void 핸드셰이크는_한_바이트씩_보내도_절대_5초에_끊긴다() throws Exception {
        Socket a = open();
        a.getOutputStream().write('H');
        a.getOutputStream().flush();
        eventually(() -> server.pendingHandshakes() == 1, "pending");
        advance(Duration.ofSeconds(3));
        a.getOutputStream().write('O');
        a.getOutputStream().flush();
        advance(Duration.ofSeconds(3));
        assertTrue(closedByPeer(a));
        eventually(() -> server.openConnections() == 0, "closed");
    }

    @Test
    void 대기_방은_10분_뒤_만료된다() throws Exception {
        Socket host = open();
        String code = hostRoom(host);
        advance(Duration.ofMinutes(9));
        assertEquals(1, server.waitingRooms());
        advance(Duration.ofMinutes(2));
        assertTrue(closedByPeer(host));
        eventually(() -> server.waitingRooms() == 0, "expired");
        Socket client = open();
        send(client, "JOIN v1 " + code);
        assertEquals("ERR NO_ROOM", reply(client));
    }

    @Test
    void 만료된_방_정리는_나중에_만든_방을_지우지_않는다() throws Exception {
        Socket old = open();
        hostRoom(old);
        advance(Duration.ofMinutes(11));
        assertTrue(closedByPeer(old));
        Socket fresh = open();
        String code = hostRoom(fresh);
        advance(Duration.ofMinutes(1));
        assertEquals(1, server.waitingRooms());
        Socket client = open();
        send(client, "JOIN v1 " + code);
        assertEquals("OK", reply(client));
    }

    @Test
    void 짝_맺은_뒤_60초_무활동이면_닫는다() throws Exception {
        Socket[] p = pair();
        advance(Duration.ofSeconds(30));
        p[0].getOutputStream().write(1);
        assertEquals(1, p[1].getInputStream().read());
        // 상대가 바이트를 받은 뒤에도 pump가 쓰기 표시를 지우기 전일 수 있다. 시계를 50초 넘기기 전에 기다린다
        eventually(() -> server.stalledWrites() == 0, "write finished");
        advance(Duration.ofSeconds(50));
        assertEquals(1, server.pairedRooms(), "활동이 있었으므로 유지");
        advance(Duration.ofSeconds(61));
        assertTrue(closedByPeer(p[0]));
        assertTrue(closedByPeer(p[1]));
    }

    @Test
    void 상대가_읽지_않아_쓰기가_막히면_정리한다() throws Exception {
        Socket[] p = pair();
        Thread writer = Thread.ofVirtual().start(() -> {
            byte[] chunk = new byte[64 * 1024];
            try {
                OutputStream out = p[0].getOutputStream();
                for (int i = 0; i < 1024; i++) out.write(chunk);
            } catch (IOException expected) {
                // 서버가 닫으면 끝난다
            }
        });
        eventually(() -> server.stalledWrites() > 0, "a stalled write");
        advance(Duration.ofSeconds(11));
        eventually(() -> server.pairedRooms() == 0, "stalled room closed");
        writer.join(5_000);
    }
    //#endregion

    //#region 자원 제한
    @Test
    void 같은_IP의_없는_코드_시도는_1분에_10번까지() throws IOException {
        for (int i = 0; i < 10; i++) {
            Socket c = open();
            send(c, "JOIN v1 AAAAAA");
            assertEquals("ERR NO_ROOM", reply(c));
            advance(Duration.ofMillis(300));
        }
        Socket c = open();
        send(c, "JOIN v1 AAAAAA");
        assertEquals("ERR SLOW_DOWN", reply(c));
        advance(Duration.ofMinutes(1));
        Socket d = open();
        send(d, "JOIN v1 AAAAAA");
        assertEquals("ERR NO_ROOM", reply(d));
    }

    @Test
    void 대기_방_한도를_넘으면_FULL() throws Exception {
        server.close();
        start(new RelayLimits(300, 64, 1000, Duration.ofSeconds(5), 2, Duration.ofMinutes(10), 100,
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10));
        hostRoom(open());
        hostRoom(open());
        Socket third = open();
        send(third, "HOST v1");
        assertEquals("ERR FULL", reply(third));
    }

    @Test
    void 연결_수_한도를_넘으면_바로_닫는다() throws Exception {
        server.close();
        start(new RelayLimits(2, 64, 1000, Duration.ofSeconds(5), 100, Duration.ofMinutes(10), 100,
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10));
        open();
        open();
        eventually(() -> server.openConnections() == 2, "two open");
        Socket third = open();
        assertTrue(closedByPeer(third));
    }

    @Test
    void 같은_IP의_새_연결은_1초에_정해진_수까지() throws Exception {
        server.close();
        start(new RelayLimits(300, 64, 3, Duration.ofSeconds(5), 100, Duration.ofMinutes(10), 100,
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10));
        java.util.List<Socket> hosts = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Socket host = open();
            hostRoom(host);
            hosts.add(host);
        }
        Socket fourth = open();
        assertTrue(closedByPeer(fourth));
        // 대기 방은 IP당 3개까지라, 연결 속도 제한만 보려고 앞의 방을 닫고 시계를 넘긴다
        for (Socket host : hosts) host.close();
        eventually(() -> server.waitingRooms() == 0, "rooms closed");
        advance(Duration.ofSeconds(2));
        hostRoom(open());
    }

    @Test
    void 반복_접속_후_연결과_방이_남지_않는다() throws Exception {
        server.close();
        start(new RelayLimits(300, 64, 10_000, Duration.ofSeconds(5), 100, Duration.ofMinutes(10), 100,
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10_000));
        for (int i = 0; i < 50; i++) {
            Socket[] p = pair();
            p[0].close();
            p[1].close();
        }
        eventually(() -> server.openConnections() == 0 && server.pairedRooms() == 0 && server.waitingRooms() == 0,
                "all released");
    }

    @Test
    void 한_번_쓴_코드는_다시_발급하지_않는다() throws Exception {
        java.util.Set<String> seen = new java.util.HashSet<>();
        server.close();
        start(new RelayLimits(300, 64, 10_000, Duration.ofSeconds(5), 100, Duration.ofMinutes(10), 100,
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10));
        for (int i = 0; i < 200; i++) {
            Socket h = open();
            assertTrue(seen.add(hostRoom(h)), "duplicate code");
            h.close();
            // IP당 대기 방 상한이 있어, 닫은 방이 정리된 뒤 다음 방을 연다
            eventually(() -> server.waitingRooms() == 0, "room closed");
        }
    }
    //#endregion
}
