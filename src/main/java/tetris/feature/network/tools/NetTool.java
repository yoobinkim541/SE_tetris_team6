package tetris.feature.network.tools;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tetris.feature.network.CloseReason;
import tetris.feature.network.EstablishException;
import tetris.feature.network.LanAddresses;
import tetris.feature.network.NetworkDefaults;
import tetris.feature.network.PeerAddress;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.PeerLink;
import tetris.feature.network.PeerListener;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.ProtocolException;

/**
 * 두 PC LAN 검증에 쓰는 명령행 도구 (S11-C1·S12-C1, R3-LATENCY).
 *
 * <pre>
 * java -cp SE-Tetris.jar tetris.feature.network.tools.NetTool info [port]
 * java -cp SE-Tetris.jar tetris.feature.network.tools.NetTool host [port]
 * java -cp SE-Tetris.jar tetris.feature.network.tools.NetTool client &lt;ip&gt; [port] [--count N] [--interval-ms M] [--csv path]
 * java -cp SE-Tetris.jar tetris.feature.network.tools.NetTool lag &lt;listenPort&gt; &lt;targetIp&gt; &lt;targetPort&gt; [--delay-ms D] [--stall-after-ms S]
 * </pre>
 *
 * <p>종료 코드: 0 성공, 1 사용법 오류, 2 연결 실패.
 *
 * <p><b>측정 범위</b>: {@code client}는 PING/PONG 왕복 시간만 잰다. 입력이 발생해 호스트가 반영하고
 * 두 화면에 표시되기까지의 200ms 판정은 {@code docs/req3/s11-lan-measurement.md}의 영상 측정 절차로
 * 따로 한다. 이 도구의 출력만으로 합격을 선언하지 않는다.
 */
public final class NetTool {
    /** RTT 출력 마지막 줄. 이 도구만으로 200ms 합격을 선언하지 못하게 한다 */
    public static final String RTT_ONLY_NOTICE =
            "네트워크 왕복만 측정했습니다. 입력→두 화면 표시 200ms 판정은 docs/req3/s11-lan-measurement.md 절차로 따로 합니다.";
    /** 200ms 판정 기준 (decisions.md F-08) */
    public static final double THRESHOLD_MILLIS = 200.0;

    private static final int DEFAULT_COUNT = 200;
    private static final int DEFAULT_INTERVAL_MILLIS = 50;
    private static final long WAIT_SECONDS = 10;

    private NetTool() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** 하위 명령을 실행하고 종료 코드를 돌려준다 */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        if (args == null || args.length == 0) {
            printUsage(err);
            return 1;
        }
        try {
            return switch (args[0]) {
                case "info" -> info(args, out, err);
                case "host" -> host(args, out, err);
                case "client" -> client(args, out, err);
                case "lag" -> lag(args, out, err);
                default -> {
                    err.println("알 수 없는 하위 명령입니다: " + args[0]);
                    printUsage(err);
                    yield 1;
                }
            };
        } catch (UsageException e) {
            err.println(e.getMessage());
            printUsage(err);
            return 1;
        }
    }

    // ── info ────────────────────────────────────────────────────────────
    private static int info(String[] args, PrintStream out, PrintStream err) throws UsageException {
        int port = parsePort(args.length > 1 ? args[1] : null, NetworkDefaults.DEFAULT_PORT, "포트");
        out.println("LAN 주소 후보: " + describeAddresses());
        out.println("기본 포트: " + NetworkDefaults.DEFAULT_PORT);
        out.println("포트 " + port + ": bind " + (canBind(port) ? "가능" : "불가(다른 프로세스가 사용 중)"));
        out.println("방화벽은 전체 해제하지 말고 TCP " + NetworkDefaults.DEFAULT_PORT
                + " 인바운드 LocalSubnet 예외만 추가합니다 (scripts/firewall-rule.ps1).");
        return 0;
    }

    private static String describeAddresses() {
        List<String> addresses = LanAddresses.current();
        return addresses.isEmpty() ? "없음(유선·무선 어댑터 확인)" : String.join(", ", addresses);
    }

    private static boolean canBind(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("0.0.0.0", port), 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ── host ────────────────────────────────────────────────────────────
    private static int host(String[] args, PrintStream out, PrintStream err) throws UsageException {
        int port = NetworkDefaults.DEFAULT_PORT;
        Path portFile = null;
        for (int i = 1; i < args.length; i++) {
            if ("--port-file".equals(args[i])) {
                portFile = Path.of(requireValue(args, ++i, "--port-file"));
            } else {
                port = parsePort(args[i], NetworkDefaults.DEFAULT_PORT, "포트");
            }
        }

        ExecutorService dispatcher = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "nettool-host-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        PeerEstablisher establisher = new PeerEstablisher(dispatcher);
        PeerEstablisher.Attempt attempt = establisher.host(port);
        if (attempt.localPort() <= 0) {
            dispatcher.shutdownNow();
            err.println("호스트 대기 실패: 포트 " + port + "를 열 수 없습니다 (사용 중인 포트인지 확인하세요)");
            return 2;
        }
        int boundPort = attempt.localPort();
        writePortFile(portFile, boundPort, err);

        out.println("호스트 대기 중입니다. 포트 " + boundPort + ", 주소 " + describeAddresses());
        out.println("상대 PC에서 client 명령으로 이 주소와 포트를 지정하세요.");

        CountDownLatch finished = new CountDownLatch(1);
        PeerLink[] linkHolder = new PeerLink[1];
        try {
            PeerLink link = awaitLink(attempt);
            linkHolder[0] = link;
            attach(dispatcher, link, new PeerListener() {
                @Override
                public void onMessage(Message message) {
                    if (message.type() == MessageType.PING) {
                        ControlPayloads.Ping ping = decodePing(message, err);
                        if (ping != null) {
                            link.send(ping.pong());
                        }
                        return;
                    }
                    if (message.type() == MessageType.LEAVE) {
                        out.println("상대가 연결을 종료했습니다.");
                        link.close();
                        finished.countDown();
                    }
                }

                @Override
                public void onClosed(CloseReason reason, String detail) {
                    out.println("연결이 닫혔습니다: " + reason);
                    finished.countDown();
                }
            });
            out.println("상대 접속: " + link.peerAddress());
            out.println("상대가 나가거나 연결이 닫힐 때까지 대기합니다 (Ctrl+C로 종료).");
            // 상대 LEAVE·연결 종료까지 기다린다. 사용자는 Ctrl+C로 언제든 끝낼 수 있다
            finished.await();
            return 0;
        } catch (EstablishException | ConnectionFailure e) {
            err.println("연결 실패: " + e.getMessage());
            return 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("호스트 대기가 중단되었습니다.");
            return 2;
        } finally {
            if (linkHolder[0] != null) {
                linkHolder[0].close();
            }
            attempt.cancel();
            dispatcher.shutdownNow();
        }
    }

    private static void writePortFile(Path portFile, int port, PrintStream err) {
        if (portFile == null) return;
        try {
            Path parent = portFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(portFile, Integer.toString(port) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("포트 파일을 쓰지 못했습니다: " + e.getMessage());
        }
    }

    // ── client ──────────────────────────────────────────────────────────
    private static int client(String[] args, PrintStream out, PrintStream err) throws UsageException {
        if (args.length < 2) {
            throw new UsageException("client에는 상대 IP가 필요합니다.");
        }
        String address = args[1];
        int port = NetworkDefaults.DEFAULT_PORT;
        int count = DEFAULT_COUNT;
        int intervalMillis = DEFAULT_INTERVAL_MILLIS;
        Path csv = null;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--count" -> count = parsePositiveInt(requireValue(args, ++i, "--count"), "--count");
                case "--interval-ms" -> intervalMillis =
                        parseNonNegativeInt(requireValue(args, ++i, "--interval-ms"), "--interval-ms");
                case "--csv" -> csv = Path.of(requireValue(args, ++i, "--csv"));
                default -> port = parsePort(args[i], NetworkDefaults.DEFAULT_PORT, "포트");
            }
        }

        ExecutorService dispatcher = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "nettool-client-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        PeerEstablisher establisher = new PeerEstablisher(dispatcher);
        PeerEstablisher.Attempt attempt = establisher.connect(address, port);
        try {
            PeerLink link = awaitLink(attempt);
            try {
                return measure(dispatcher, link, count, intervalMillis, csv, out, err);
            } finally {
                link.send(new ControlPayloads.Leave("measurement finished").message());
                link.close();
            }
        } catch (EstablishException | ConnectionFailure e) {
            err.println("연결 실패: " + e.getMessage());
            return 2;
        } finally {
            attempt.cancel();
            dispatcher.shutdownNow();
        }
    }

    private static int measure(
            ExecutorService dispatcher,
            PeerLink link,
            int count,
            int intervalMillis,
            Path csv,
            PrintStream out,
            PrintStream err)
            throws ConnectionFailure {
        CountDownLatch received = new CountDownLatch(count);
        ConcurrentLinkedQueue<LatencySample> samples = new ConcurrentLinkedQueue<>();
        Map<Long, long[]> sentAtByNonce = new ConcurrentHashMap<>();

        attach(dispatcher, link, new PeerListener() {
            @Override
            public void onMessage(Message message) {
                if (message.type() != MessageType.PONG) return;
                ControlPayloads.Ping pong = decodePing(message, err);
                if (pong == null) return;
                long[] sent = sentAtByNonce.remove(pong.nonce());
                if (sent == null) return;
                long receivedNanos = System.nanoTime();
                samples.add(new LatencySample(
                        "NET", "client->host", pong.nonce(), sent[0], receivedNanos, receivedNanos));
                received.countDown();
            }

            @Override
            public void onClosed(CloseReason reason, String detail) {
                while (received.getCount() > 0) {
                    received.countDown();
                }
            }
        });

        for (int i = 1; i <= count; i++) {
            long sentNanos = System.nanoTime();
            sentAtByNonce.put((long) i, new long[] {sentNanos});
            if (!link.send(new ControlPayloads.Ping(i, sentNanos).message())) {
                break;
            }
            if (intervalMillis > 0) {
                try {
                    Thread.sleep(intervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        try {
            received.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<LatencySample> ordered = new ArrayList<>(samples);
        ordered.sort(Comparator.comparingLong(LatencySample::sequence));
        if (ordered.isEmpty()) {
            err.println("응답을 받지 못했습니다. 호스트가 실행 중인지, 방화벽 예외가 있는지 확인하세요.");
            return 2;
        }
        List<Double> millis = new ArrayList<>(ordered.size());
        for (LatencySample sample : ordered) {
            millis.add(sample.localMillis());
        }
        LatencyStats stats = LatencyStats.of(millis, THRESHOLD_MILLIS);

        out.printf("네트워크 왕복(RTT) 표본 %d개, 요청 %d개%n", stats.count(), count);
        out.printf(
                "p50=%.2fms p95=%.2fms 최대=%.2fms, %.0fms 초과 %d건%n",
                stats.p50(), stats.p95(), stats.max(), stats.thresholdMillis(), stats.overThreshold());

        if (csv != null) {
            try {
                LatencyCsv.write(csv, ordered);
                out.println("표본 CSV: " + csv.toAbsolutePath());
            } catch (IOException e) {
                err.println("CSV를 쓰지 못했습니다: " + e.getMessage());
                return 2;
            }
        }
        out.println(RTT_ONLY_NOTICE);
        return 0;
    }

    // ── lag ─────────────────────────────────────────────────────────────
    private static int lag(String[] args, PrintStream out, PrintStream err) throws UsageException {
        if (args.length < 4) {
            throw new UsageException("lag에는 <listenPort> <targetIp> <targetPort>가 필요합니다.");
        }
        int listenPort = parsePort(args[1], 0, "수신 포트");
        String targetIp = args[2];
        int targetPort = parsePort(args[3], NetworkDefaults.DEFAULT_PORT, "대상 포트");
        long delayMillis = 0;
        long stallAfterMillis = -1;
        for (int i = 4; i < args.length; i++) {
            switch (args[i]) {
                case "--delay-ms" -> delayMillis =
                        parseNonNegativeLong(requireValue(args, ++i, "--delay-ms"), "--delay-ms");
                case "--stall-after-ms" -> stallAfterMillis =
                        parseStallValue(requireValue(args, ++i, "--stall-after-ms"));
                default -> throw new UsageException("알 수 없는 옵션입니다: " + args[i]);
            }
        }

        PeerAddress target;
        try {
            target = PeerAddress.parse(targetIp, targetPort);
        } catch (EstablishException e) {
            err.println("대상 주소 오류: " + e.getMessage());
            return 1;
        }

        CountDownLatch stopped = new CountDownLatch(1);
        try (LagProxy proxy = new LagProxy(
                listenPort,
                new InetSocketAddress(target.host(), target.port()),
                delayMillis,
                stallAfterMillis)) {
            proxy.start();
            out.println("지연 주입 중계: 로컬 포트 " + proxy.localPort() + " → " + target.host() + ":" + target.port());
            out.println("방향별 지연 " + delayMillis + "ms, 정지 "
                    + (stallAfterMillis < 0 ? "없음" : stallAfterMillis + "ms 뒤부터") + " (Ctrl+C로 종료)");
            Runtime.getRuntime().addShutdownHook(new Thread(stopped::countDown, "nettool-lag-shutdown"));
            while (stopped.getCount() > 0) {
                try {
                    stopped.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return 0;
        } catch (IOException e) {
            err.println("지연 주입 중계를 시작하지 못했습니다: " + e.getMessage());
            return 2;
        }
    }

    // ── 공통 ────────────────────────────────────────────────────────────
    /** 연결 수립 결과를 기다린다. 실패는 원인 코드와 함께 {@link ConnectionFailure}로 올린다 */
    private static PeerLink awaitLink(PeerEstablisher.Attempt attempt) throws EstablishException, ConnectionFailure {
        try {
            return attempt.result().get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof EstablishException establishException) {
                throw establishException;
            }
            throw new ConnectionFailure(String.valueOf(cause));
        } catch (TimeoutException e) {
            throw new ConnectionFailure("제한 시간 안에 연결이 수립되지 않았습니다");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionFailure("연결 대기가 중단되었습니다");
        }
    }

    /** 링크의 dispatcher에서 리스너를 붙인다. 붙이기 전 도착분은 링크가 버퍼에 두었다가 순서대로 넘긴다 */
    private static void attach(ExecutorService dispatcher, PeerLink link, PeerListener listener)
            throws ConnectionFailure {
        try {
            dispatcher.submit(() -> link.attach(listener)).get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new ConnectionFailure(String.valueOf(e.getCause()));
        } catch (TimeoutException e) {
            throw new ConnectionFailure("리스너 등록이 제한 시간 안에 끝나지 않았습니다");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionFailure("리스너 등록이 중단되었습니다");
        }
    }

    private static ControlPayloads.Ping decodePing(Message message, PrintStream err) {
        try {
            return ControlPayloads.Ping.from(message.payload());
        } catch (ProtocolException e) {
            err.println("PING payload를 읽지 못했습니다: " + e.getMessage());
            return null;
        }
    }

    private static int parsePort(String text, int fallback, String what) throws UsageException {
        if (text == null) return fallback;
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new UsageException(what + " 값이 숫자가 아닙니다: " + text);
        }
        if (value < 0 || value > 65535) {
            throw new UsageException(what + "는 0~65535여야 합니다: " + value);
        }
        return value;
    }

    private static int parsePositiveInt(String text, String option) throws UsageException {
        int value = parseNonNegativeInt(text, option);
        if (value <= 0) {
            throw new UsageException(option + "는 1 이상이어야 합니다: " + value);
        }
        return value;
    }

    private static int parseNonNegativeInt(String text, String option) throws UsageException {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < 0) {
                throw new UsageException(option + "는 0 이상이어야 합니다: " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new UsageException(option + " 값이 숫자가 아닙니다: " + text);
        }
    }

    private static long parseNonNegativeLong(String text, String option) throws UsageException {
        try {
            long value = Long.parseLong(text.trim());
            if (value < 0) {
                throw new UsageException(option + "는 0 이상이어야 합니다: " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new UsageException(option + " 값이 숫자가 아닙니다: " + text);
        }
    }

    private static long parseStallValue(String text) throws UsageException {
        try {
            long value = Long.parseLong(text.trim());
            if (value < -1) {
                throw new UsageException("--stall-after-ms는 -1(정지 없음) 이상이어야 합니다: " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new UsageException("--stall-after-ms 값이 숫자가 아닙니다: " + text);
        }
    }

    private static String requireValue(String[] args, int index, String option) throws UsageException {
        if (index >= args.length) {
            throw new UsageException(option + "에 값이 필요합니다.");
        }
        return args[index];
    }

    private static void printUsage(PrintStream err) {
        err.println("사용법: NetTool <info|host|client|lag> [옵션]");
        err.println("  info [port] : LAN 주소 후보와 포트 bind 가능 여부");
        err.println("  host [port] : 한 번 접속을 받아 PING에 PONG 응답");
        err.println("  client <ip> [port] [--count N] [--interval-ms M] [--csv path] : RTT 측정");
        err.println("  lag <listenPort> <targetIp> <targetPort> [--delay-ms D] [--stall-after-ms S] : 지연 주입");
    }

    /** 연결 수립 실패 (원인 코드 없이 끝난 경우 포함) */
    static final class ConnectionFailure extends Exception {
        ConnectionFailure(String message) {
            super(message);
        }
    }

    /** 인자 해석 실패. 종료 코드 1로 이어진다 */
    static final class UsageException extends Exception {
        UsageException(String message) {
            super(message);
        }
    }
}
