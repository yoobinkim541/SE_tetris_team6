package tetris.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** 공개/비공개 방 목록. v1 호스트는 목록에 없고, 짝이 지어진 방도 빠진다 */
@Timeout(30)
class RelayRoomListTest {
    private final AtomicLong now = new AtomicLong();
    private RelayServer server;
    private final List<Socket> sockets = new ArrayList<>();

    @AfterEach
    void stop() throws IOException {
        for (Socket socket : sockets) socket.close();
        if (server != null) server.close();
    }

    @Test
    void 공개_방은_목록에_보이고_번호로_바로_들어간다() throws IOException {
        start(RelayLimits.DEFAULT);
        Socket host = open();
        String[] room = hostV2(host, "public", "classic", "안녕");
        List<String> listed = list();
        assertEquals(1, listed.size());
        assertTrue(listed.getFirst().startsWith("R " + room[1] + " classic 0 1 "), listed.getFirst());
        assertFalse(listed.getFirst().contains(room[0]), "비밀 코드는 목록에 없다");
        Socket guest = open();
        send(guest, "JOINID v2 " + room[1]);
        assertEquals("OK", reply(guest));
        assertEquals("PAIRED", reply(host));
        assertTrue(list().isEmpty(), "짝이 지어지면 목록에서 빠진다");
    }

    @Test
    void 비공개는_번호_참가를_거절하고_코드로는_들어간다() throws IOException {
        start(RelayLimits.DEFAULT);
        Socket host = open();
        String[] room = hostV2(host, "private", "item", "비밀방");
        String line = list().getFirst();
        assertTrue(line.contains(" 1 1 "), line);
        Socket byId = open();
        send(byId, "JOINID v2 " + room[1]);
        assertEquals("ERR PRIVATE", reply(byId));
        Socket byCode = open();
        send(byCode, "JOIN v1 " + room[0]);
        assertEquals("OK", reply(byCode));
        assertEquals("PAIRED", reply(host));
    }

    @Test
    void v1_방은_목록에_없고_예전_줄은_그대로다() throws IOException {
        start(RelayLimits.DEFAULT);
        Socket host = open();
        send(host, "HOST v1");
        String line = reply(host);
        assertTrue(line.startsWith("ROOM "), line);
        assertEquals(2, line.split(" ").length, line);
        assertTrue(list().isEmpty());
        Socket guest = open();
        send(guest, "JOIN v1 " + line.substring(5));
        assertEquals("OK", reply(guest));
    }

    @Test
    void 잘못된_이름과_모드는_거절한다() throws IOException {
        start(RelayLimits.DEFAULT);
        Socket host = open();
        send(host, "HOST v2 public no-such " + RoomName.encode("방"));
        assertEquals("ERR BAD_REQUEST", reply(host));
        Socket empty = open();
        send(empty, "HOST v2 public classic " + RoomName.encode("   \n\t"));
        assertEquals("ERR BAD_REQUEST", reply(empty));
        Socket longName = open();
        send(longName, "HOST v2 public classic " + RoomName.encode("가".repeat(21)));
        assertEquals("ERR BAD_REQUEST", reply(longName));
    }

    @Test
    void 같은_주소의_대기_방은_세_개까지다() throws IOException {
        start(new RelayLimits(300, 64, 1000, Duration.ofSeconds(5), 100, Duration.ofMinutes(10), 100,
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10, 20, 3));
        hostV2(open(), "public", "classic", "하나");
        hostV2(open(), "public", "timed", "둘");
        hostV2(open(), "private", "raid-coop", "셋");
        Socket fourth = open();
        send(fourth, "HOST v2 public classic " + RoomName.encode("넷"));
        assertEquals("ERR ROOM_LIMIT", reply(fourth));
        assertEquals(3, list().size());
    }

    @Test
    void 목록은_최신_50개만_주고_요청이_빠르면_거절한다() throws IOException {
        start(new RelayLimits(300, 64, 10_000, Duration.ofSeconds(5), 200, Duration.ofMinutes(10), 100,
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10, 2, 80));
        for (int i = 0; i < 51; i++) {
            now.addAndGet(1);
            hostV2(open(), "public", "classic", "방" + i);
        }
        List<String> listed = list();
        assertEquals(50, listed.size());
        assertTrue(listed.getFirst().contains(RoomName.encode("방50")), listed.getFirst());
        assertFalse(listed.stream().anyMatch(line -> line.contains(RoomName.encode("방0"))));
        list();
        Socket limited = open();
        send(limited, "LIST v2");
        assertEquals("ERR SLOW_DOWN", reply(limited));
    }

    @Test
    void 기다리다_만료되면_목록에서_사라진다() throws IOException {
        start(RelayLimits.DEFAULT);
        hostV2(open(), "public", "raid-race", "곧끝");
        assertEquals(1, list().size());
        now.addAndGet(Duration.ofMinutes(10).toNanos());
        server.sweep();
        assertTrue(list().isEmpty());
    }

    @Test
    void 잘못된_베이스64와_제어문자는_거절하고_목록에_주소와_코드가_없다() throws IOException {
        start(new RelayLimits(300, 64, 1000, Duration.ofSeconds(5), 100, Duration.ofMinutes(10), 100,
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10, 20, 3));
        Socket bad = open();
        send(bad, "HOST v2 public classic %%%%");
        assertEquals("ERR BAD_REQUEST", reply(bad));
        Socket control = open();
        send(control, "HOST v2 public classic abc\u0001def");
        assertEquals("ERR BAD_REQUEST", reply(control));
        Socket host = open();
        String[] room = hostV2(host, "private", "classic", "비밀");
        String line = list().getFirst();
        assertFalse(line.contains(room[0]), line);
        assertFalse(line.contains("127.0.0.1"), line);
        assertFalse(line.contains(host.getLocalAddress().getHostAddress()), line);
        Socket byId = open();
        send(byId, "JOINID v2 " + room[1]);
        String denied = reply(byId);
        assertEquals("ERR PRIVATE", denied);
        assertFalse(denied.contains(room[0]));
    }

    @Test
    void 목록에_없는_방도_주소당_세_개까지다() throws IOException {
        start(new RelayLimits(300, 64, 1000, Duration.ofSeconds(5), 100, Duration.ofMinutes(10), 100,
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10, 20, 3));
        for (int i = 0; i < 3; i++) {
            Socket v1 = open();
            send(v1, "HOST v1");
            assertTrue(reply(v1).startsWith("ROOM "), "v1 " + i);
        }
        Socket fourth = open();
        send(fourth, "HOST v1");
        assertEquals("ERR ROOM_LIMIT", reply(fourth));
        assertTrue(list().isEmpty());
    }

    private void start(RelayLimits limits) throws IOException {
        server = new RelayServer(0, limits, now::get, new Random(1));
        server.start();
    }

    private Socket open() throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.localPort());
        socket.setSoTimeout(5_000);
        sockets.add(socket);
        return socket;
    }

    private static void send(Socket socket, String line) throws IOException {
        RelayLine.write(socket.getOutputStream(), line);
    }

    private static String reply(Socket socket) throws IOException {
        return RelayLine.read(socket.getInputStream());
    }

    /** [code, id] */
    private String[] hostV2(Socket host, String visibility, String mode, String name) throws IOException {
        send(host, "HOST v2 " + visibility + " " + mode + " " + RoomName.encode(name));
        String line = reply(host);
        assertTrue(line.startsWith("ROOM "), line);
        String[] parts = line.split(" ");
        assertEquals(3, parts.length, line);
        assertFalse(parts[1].equals(parts[2]), "공개 번호는 비밀 코드와 다르다");
        return new String[] {parts[1], parts[2]};
    }

    private List<String> list() throws IOException {
        Socket socket = open();
        send(socket, "LIST v2");
        List<String> lines = new ArrayList<>();
        while (true) {
            String line = reply(socket);
            if ("END".equals(line)) return lines;
            lines.add(line);
        }
    }
}
