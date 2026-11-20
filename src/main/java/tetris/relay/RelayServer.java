package tetris.relay;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * 인터넷 대전 중계 서버 (challenge, 설계 v2). 호스트에게 로비 코드를 발급하고, 같은 코드로 들어온 클라이언트와 짝지은 뒤
 * 바이트를 그대로 양방향으로 넘긴다. 게임 프로토콜은 해석하지 않는다. 계산은 호스트 PC가 한다.
 *
 * <p>실행: {@code java -cp SE-Tetris.jar tetris.relay.RelayServer [port]} (기본 47123). IPv4 전용, 단일 프로세스.
 */
public final class RelayServer implements Closeable {
    public static final int DEFAULT_PORT = 47123;
    private static final int PUMP_BUFFER_BYTES = 8 * 1024;
    private static final int ISSUED_CODE_HISTORY = 100_000;
    private static final long SECOND_NANOS = 1_000_000_000L;
    private static final long MINUTE_NANOS = 60 * SECOND_NANOS;

    private final int requestedPort;
    private final RelayLimits limits;
    private final LongSupplier nanoClock;
    private final Random random;

    private ServerSocket serverSocket;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Conn> connections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger pending = new AtomicInteger();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Object roomLock = new Object();
    private final Set<String> issuedCodes = new LinkedHashSet<>();
    private final Map<InetAddress, Deque<Long>> connectTimes = new HashMap<>();
    private final Map<InetAddress, Deque<Long>> noRoomTimes = new HashMap<>();

    public RelayServer(int port, RelayLimits limits, LongSupplier nanoClock, Random random) {
        this.requestedPort = port;
        this.limits = Objects.requireNonNull(limits);
        this.nanoClock = Objects.requireNonNull(nanoClock);
        this.random = Objects.requireNonNull(random);
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        RelayServer server = new RelayServer(port, RelayLimits.DEFAULT, System::nanoTime, new SecureRandom());
        server.start();
        log("listening on " + server.localPort());
        Thread sweeper = Thread.ofPlatform().daemon().name("relay-sweep").start(() -> {
            while (!server.closed.get()) {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    return;
                }
                server.sweep();
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        try {
            sweeper.join();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public void start() throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), requestedPort), 128);
        serverSocket = socket;
        Thread.ofPlatform().daemon().name("relay-accept").start(this::acceptLoop);
    }

    public int localPort() {
        return serverSocket.getLocalPort();
    }

    public int openConnections() {
        return connections.size();
    }

    public int pendingHandshakes() {
        return pending.get();
    }

    public int waitingRooms() {
        return (int) rooms.values().stream().filter(r -> r.state == RoomState.WAITING).count();
    }

    public int pairedRooms() {
        return (int) rooms.values().stream().filter(r -> r.state == RoomState.PAIRED).count();
    }

    /** 지금 쓰기가 끝나지 않은 pump 수 (막힌 전송 감시용) */
    public int stalledWrites() {
        int n = 0;
        for (Conn c : connections) if (c.writeStartedNanos != -1) n++;
        return n;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        closeQuietly(serverSocket);
        for (Conn c : connections) c.close();
        rooms.clear();
    }

    //#region 접속 수락
    private void acceptLoop() {
        while (!closed.get()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (closed.get()) return;
                continue;
            }
            InetAddress ip = socket.getInetAddress();
            long now = nanoClock.getAsLong();
            if (connections.size() >= limits.maxConnections()
                    || pending.get() >= limits.maxPendingHandshakes()
                    || !allowNewConnection(ip, now)) {
                closeQuietly(socket);
                continue;
            }
            Conn conn = new Conn(socket, ip, now);
            connections.add(conn);
            pending.incrementAndGet();
            Thread.ofVirtual().name("relay-conn").start(() -> handshake(conn));
        }
    }

    private boolean allowNewConnection(InetAddress ip, long now) {
        synchronized (connectTimes) {
            return withinRate(connectTimes, ip, now, SECOND_NANOS, limits.newConnectionsPerIpPerSecond());
        }
    }

    /** 창(window) 안의 기록이 limit 미만이면 지금을 기록하고 true */
    private static boolean withinRate(Map<InetAddress, Deque<Long>> table, InetAddress ip, long now, long window, int limit) {
        Deque<Long> times = table.computeIfAbsent(ip, k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() >= window) times.pollFirst();
        if (times.size() >= limit) return false;
        times.addLast(now);
        return true;
    }
    //#endregion

    //#region 핸드셰이크
    private void handshake(Conn conn) {
        String line;
        try {
            line = RelayLine.read(conn.in);
        } catch (IOException e) {
            reply(conn, "ERR BAD_REQUEST");
            finishPending(conn);
            conn.close();
            return;
        }
        String[] parts = line.trim().split(" ");
        if (parts.length == 2 && parts[0].equals("HOST") && parts[1].equals("v1")) {
            host(conn);
        } else if (parts.length == 3 && parts[0].equals("JOIN") && parts[1].equals("v1")) {
            Optional<String> code = LobbyCode.normalize(parts[2]);
            if (code.isEmpty()) {
                failHandshake(conn, "ERR BAD_REQUEST");
            } else {
                join(conn, code.get());
            }
        } else {
            failHandshake(conn, "ERR BAD_REQUEST");
        }
    }

    private void host(Conn conn) {
        Room room;
        synchronized (roomLock) {
            if (waitingRooms() >= limits.maxWaitingRooms()) {
                failHandshake(conn, "ERR FULL");
                return;
            }
            String code;
            do {
                code = LobbyCode.generate(random);
            } while (issuedCodes.contains(code));
            issuedCodes.add(code);
            if (issuedCodes.size() > ISSUED_CODE_HISTORY) {
                issuedCodes.remove(issuedCodes.iterator().next());
            }
            room = new Room(code, conn, nanoClock.getAsLong());
            rooms.put(code, room);
        }
        conn.room = room;
        if (!reply(conn, "ROOM " + room.code)) {
            finishPending(conn);
            closeRoom(room, "host write failed");
            return;
        }
        finishPending(conn);
        log("room " + room.code + " waiting");
        pump(conn, room, true);
    }

    private void join(Conn conn, String code) {
        Room room = rooms.get(code);
        String error = null;
        if (room == null) {
            boolean allowed;
            synchronized (noRoomTimes) {
                allowed = withinRate(noRoomTimes, conn.ip, nanoClock.getAsLong(), MINUTE_NANOS,
                        limits.noRoomPerIpPerMinute());
            }
            error = allowed ? "ERR NO_ROOM" : "ERR SLOW_DOWN";
        } else {
            synchronized (room) {
                if (room.state == RoomState.CLOSED) {
                    error = "ERR NO_ROOM";
                } else if (room.state == RoomState.PAIRED) {
                    error = "ERR ROOM_FULL";
                } else if (pairedRooms() >= limits.maxPairedRooms()) {
                    error = "ERR FULL";
                } else {
                    room.client = conn;
                    room.state = RoomState.PAIRED;
                    room.pairedAt = nanoClock.getAsLong();
                    conn.room = room;
                }
            }
        }
        if (error != null) {
            failHandshake(conn, error);
            return;
        }
        // 클라이언트에 OK를 먼저 써야 호스트가 PAIRED를 받고 보낸 첫 프레임보다 OK가 앞선다
        boolean ok = reply(conn, "OK") && reply(room.host, "PAIRED");
        finishPending(conn);
        if (!ok) {
            closeRoom(room, "pair write failed");
            return;
        }
        room.touch(nanoClock.getAsLong());
        log("room " + room.code + " paired");
        pump(conn, room, false);
    }

    private void failHandshake(Conn conn, String error) {
        reply(conn, error);
        finishPending(conn);
        conn.close();
    }

    private void finishPending(Conn conn) {
        if (conn.handshakeDone.compareAndSet(false, true)) pending.decrementAndGet();
    }

    private static boolean reply(Conn conn, String line) {
        try {
            synchronized (conn.out) {
                RelayLine.write(conn.out, line);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    //#endregion

    //#region 전달
    /** from에서 읽어 상대에게 쓴다. 호스트 pump는 짝 맺기 전에 바이트가 오면 위반으로 닫는다 */
    private void pump(Conn from, Room room, boolean fromHost) {
        byte[] buffer = new byte[PUMP_BUFFER_BYTES];
        try {
            while (true) {
                int n = from.in.read(buffer);
                if (n < 0) break;
                long now = nanoClock.getAsLong();
                Conn to;
                synchronized (room) {
                    if (room.state != RoomState.PAIRED) break;
                    to = fromHost ? room.client : room.host;
                }
                room.touch(now);
                to.writeStartedNanos = now;
                try {
                    synchronized (to.out) {
                        to.out.write(buffer, 0, n);
                        to.out.flush();
                    }
                } finally {
                    to.writeStartedNanos = -1;
                }
            }
        } catch (IOException ignored) {
            // 소켓이 닫혔다
        }
        closeRoom(room, fromHost ? "host closed" : "client closed");
    }

    private void closeRoom(Room room, String why) {
        synchronized (room) {
            if (room.state == RoomState.CLOSED) return;
            room.state = RoomState.CLOSED;
        }
        rooms.remove(room.code, room);
        room.host.close();
        if (room.client != null) room.client.close();
        log("room " + room.code + " closed: " + why);
    }
    //#endregion

    //#region 주기 점검
    /** 핸드셰이크 기한·대기 만료·무활동·막힌 쓰기·최대 수명을 검사한다. 운영에서는 1초마다 부른다 */
    void sweep() {
        long now = nanoClock.getAsLong();
        for (Conn c : connections) {
            if (!c.handshakeDone.get() && now - c.acceptedNanos >= limits.handshakeDeadline().toNanos()) {
                finishPending(c);
                c.close();
            }
        }
        for (Room room : rooms.values()) {
            RoomState state = room.state;
            if (state == RoomState.WAITING && now - room.createdAt >= limits.roomWaitLimit().toNanos()) {
                closeRoom(room, "wait expired");
            } else if (state == RoomState.PAIRED) {
                if (now - room.lastActivity >= limits.idleLimit().toNanos()) {
                    closeRoom(room, "idle");
                } else if (now - room.pairedAt >= limits.maxSessionLength().toNanos()) {
                    closeRoom(room, "session length");
                } else if (stalled(room.host, now) || stalled(room.client, now)) {
                    closeRoom(room, "stalled write");
                }
            }
        }
        synchronized (connectTimes) {
            connectTimes.values().removeIf(d -> d.isEmpty() || now - d.peekLast() >= SECOND_NANOS);
        }
        synchronized (noRoomTimes) {
            noRoomTimes.values().removeIf(d -> d.isEmpty() || now - d.peekLast() >= MINUTE_NANOS);
        }
    }

    private boolean stalled(Conn c, long now) {
        long started = c.writeStartedNanos;
        return started != -1 && now - started >= limits.stalledWriteLimit().toNanos();
    }
    //#endregion

    private static void log(String message) {
        System.out.println(java.time.Instant.now() + " relay " + message);
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
            // 이미 닫힘
        }
    }

    private enum RoomState { WAITING, PAIRED, CLOSED }

    private static final class Room {
        final String code;
        final Conn host;
        final long createdAt;
        volatile Conn client;
        volatile RoomState state = RoomState.WAITING;
        volatile long pairedAt;
        volatile long lastActivity;

        Room(String code, Conn host, long createdAt) {
            this.code = code;
            this.host = host;
            this.createdAt = createdAt;
            this.lastActivity = createdAt;
        }

        void touch(long now) {
            lastActivity = now;
        }
    }

    private final class Conn {
        final Socket socket;
        final InetAddress ip;
        final long acceptedNanos;
        final InputStream in;
        final OutputStream out;
        final AtomicBoolean handshakeDone = new AtomicBoolean();
        final AtomicBoolean done = new AtomicBoolean();
        volatile long writeStartedNanos = -1;
        volatile Room room;

        Conn(Socket socket, InetAddress ip, long acceptedNanos) {
            this.socket = socket;
            this.ip = ip;
            this.acceptedNanos = acceptedNanos;
            InputStream i = null;
            OutputStream o = null;
            try {
                socket.setTcpNoDelay(true);
                i = socket.getInputStream();
                o = socket.getOutputStream();
            } catch (IOException e) {
                closeQuietly(socket);
            }
            this.in = i == null ? InputStream.nullInputStream() : i;
            this.out = o == null ? OutputStream.nullOutputStream() : o;
        }

        void close() {
            if (!done.compareAndSet(false, true)) return;
            closeQuietly(socket);
            connections.remove(this);
        }
    }
}
