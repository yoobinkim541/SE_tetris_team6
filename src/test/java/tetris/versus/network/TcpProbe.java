package tetris.versus.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import tetris.feature.network.CloseReason;
import tetris.feature.network.PeerConnection;
import tetris.feature.network.PeerListener;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;

/**
 * S7-C2 두 프로세스 실험의 클라이언트 쪽. {@link TwoProcessTcpTest}가 별도 JVM으로 실행한다.
 * 엔진 없이 HELLO·가짜 STATE·PING/PONG·LEAVE만 주고받는다.
 *
 * <pre>
 * client &lt;port&gt; &lt;states&gt; &lt;pings&gt;  왕복 실험 후 "PROBE_RESULT {...}" 한 줄 출력, 성공 시 종료 코드 0
 * hang &lt;port&gt;                     HELLO만 보내고 강제 종료될 때까지 대기
 * </pre>
 */
public final class TcpProbe {
    static final String RESULT_PREFIX = "PROBE_RESULT ";
    private static final long TIMEOUT_SECONDS = 10;

    private TcpProbe() {
    }

    /** 두 플레이어의 20×10 보드·점수를 담은 가짜 STATE. 실제 MatchSnapshot DTO 크기를 어림하는 용도 */
    static Message fakeState(int tick) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode().put("tick", tick);
        ArrayNode players = payload.putArray("players");
        for (int player = 0; player < 2; player++) {
            ObjectNode state = players.addObject().put("score", tick * 10L + player);
            ArrayNode board = state.putArray("board");
            for (int row = 0; row < 20; row++) {
                ArrayNode cells = board.addArray();
                for (int col = 0; col < 10; col++) cells.add((row + col + tick + player) % 9);
            }
        }
        return new Message(MessageType.STATE, "probe-match", 0, payload);
    }

    public static void main(String[] args) throws Exception {
        try {
            switch (args[0]) {
                case "client" -> client(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                case "hang" -> hang(Integer.parseInt(args[1]));
                default -> throw new IllegalArgumentException("unknown mode " + args[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static final class Inbox implements PeerListener {
        final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<CloseReason> closes = new LinkedBlockingQueue<>();

        @Override
        public void onMessage(Message message) {
            messages.add(message);
        }

        @Override
        public void onClosed(CloseReason reason, String detail) {
            closes.add(reason);
        }

        Message take(MessageType expected) throws InterruptedException {
            Message message = messages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (message == null) throw new IllegalStateException("timed out waiting for " + expected);
            if (message.type() != expected) throw new IllegalStateException("expected " + expected + " but got " + message.type());
            return message;
        }
    }

    private static Socket connect(int port) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), (int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        return socket;
    }

    private static void client(int port, int expectedStates, int pings) throws Exception {
        ExecutorService dispatcher = Executors.newSingleThreadExecutor();
        Inbox inbox = new Inbox();
        PeerConnection connection = PeerConnection.start(connect(port), dispatcher, inbox);

        connection.send(ControlPayloads.Hello.message(PeerRole.CLIENT));
        PeerRole hostRole = ControlPayloads.Hello.from(inbox.take(MessageType.HELLO).payload()).role();
        if (hostRole != PeerRole.HOST) throw new IllegalStateException("peer role " + hostRole);

        for (int tick = 1; tick <= expectedStates; tick++) {
            Message state = inbox.take(MessageType.STATE);
            Message expected = fakeState(tick);
            // JSON에는 int/long 구분이 없어 받은 쪽 숫자 노드 타입이 달라질 수 있으므로 값으로 비교한다
            if (state.type() != expected.type() || !state.matchId().equals(expected.matchId())
                    || !state.payload().equals(TcpProbe::compareNumbersByValue, expected.payload())) {
                throw new IllegalStateException("STATE " + tick + " differs");
            }
        }

        long[] rttNanos = new long[pings];
        for (int i = 0; i < pings; i++) {
            ControlPayloads.Ping ping = new ControlPayloads.Ping(i + 1, System.nanoTime());
            connection.send(ping.message());
            ControlPayloads.Ping pong = ControlPayloads.Ping.from(inbox.take(MessageType.PONG).payload());
            rttNanos[i] = System.nanoTime() - pong.sentAtNanos();
            if (!pong.equals(ping)) throw new IllegalStateException("PONG " + pong + " for " + ping);
        }

        connection.send(new ControlPayloads.Leave("probe done").message());
        CloseReason closed = inbox.closes.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (closed != CloseReason.PEER_CLOSED) throw new IllegalStateException("close " + closed);
        if (!connection.awaitTermination(Duration.ofSeconds(TIMEOUT_SECONDS))) {
            throw new IllegalStateException("connection threads still running");
        }
        dispatcher.shutdown();

        Arrays.sort(rttNanos);
        System.out.println(RESULT_PREFIX + JsonNodeFactory.instance.objectNode()
                .put("states", expectedStates)
                .put("pings", pings)
                .put("rttMinMicros", micros(rttNanos[0]))
                .put("rttP50Micros", micros(percentile(rttNanos, 50)))
                .put("rttP95Micros", micros(percentile(rttNanos, 95)))
                .put("rttMaxMicros", micros(rttNanos[pings - 1])));
        System.exit(0);
    }

    private static void hang(int port) throws Exception {
        PeerConnection connection = PeerConnection.start(connect(port), Runnable::run, new Inbox());
        connection.send(ControlPayloads.Hello.message(PeerRole.CLIENT));
        Thread.sleep(Long.MAX_VALUE);
    }

    private static int compareNumbersByValue(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) return a.decimalValue().compareTo(b.decimalValue());
        return a.equals(b) ? 0 : 1;
    }

    private static long percentile(long[] sorted, int percent) {
        int index = (int) Math.ceil(percent / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }

    private static long micros(long nanos) {
        return TimeUnit.NANOSECONDS.toMicros(nanos);
    }
}
