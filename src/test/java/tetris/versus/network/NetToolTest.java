package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.network.tools.LatencyCsv;
import tetris.feature.network.tools.LatencySample;
import tetris.feature.network.tools.NetTool;

/** S11-C1: 명령행 도구 NetTool의 종료 코드·출력·CSV 검증 (R3-LATENCY, R3-NETTEST) */
@Timeout(30)
class NetToolTest {
    private static final String RTT_ONLY_NOTICE =
            "네트워크 왕복만 측정했습니다. 입력→두 화면 표시 200ms 판정은 docs/req3/s11-lan-measurement.md 절차로 따로 합니다.";

    @TempDir
    Path tempDir;

    /** 한 번의 실행 결과. 출력 스트림을 문자열로 모아 본다 */
    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int code = NetTool.run(args, out, err);
            return new Result(
                    code, outBytes.toString(StandardCharsets.UTF_8), errBytes.toString(StandardCharsets.UTF_8));
        }
    }

    private static int awaitPort(Path portFile) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (Files.exists(portFile)) {
                String text = Files.readString(portFile, StandardCharsets.UTF_8).trim();
                if (!text.isEmpty()) {
                    try {
                        return Integer.parseInt(text);
                    } catch (NumberFormatException ignored) {
                        // 아직 쓰는 중인 파일은 다시 읽는다
                    }
                }
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        throw new AssertionError("호스트 포트 파일이 10초 안에 만들어지지 않았다: " + portFile);
    }

    private static int closedPort() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            return server.getLocalPort();
        }
    }

    @Test
    void 호스트와_클라이언트가_PING_왕복을_재고_CSV를_남긴다() throws Exception {
        Path portFile = tempDir.resolve("host-port.txt");
        Path csv = tempDir.resolve("net.csv");
        AtomicInteger hostCode = new AtomicInteger(Integer.MIN_VALUE);
        Thread host = new Thread(
                () -> hostCode.set(NetTool.run(
                        new String[] {"host", "0", "--port-file", portFile.toString()},
                        new PrintStream(new ByteArrayOutputStream()),
                        new PrintStream(new ByteArrayOutputStream()))),
                "nettool-host-test");
        host.setDaemon(true);
        host.start();
        int port = awaitPort(portFile);

        Result result = run(
                "client",
                "127.0.0.1",
                String.valueOf(port),
                "--count",
                "20",
                "--interval-ms",
                "0",
                "--csv",
                csv.toString());

        assertEquals(0, result.code(), result.err());
        assertTrue(result.out().contains("p95"), result.out());
        assertTrue(result.out().contains(RTT_ONLY_NOTICE), result.out());
        assertTrue(result.out().strip().endsWith(RTT_ONLY_NOTICE), "고정 문장은 마지막 줄이어야 한다: " + result.out());

        List<LatencySample> samples = LatencyCsv.read(csv);
        assertEquals(20, samples.size());
        assertEquals(20L, samples.get(19).sequence());
        for (LatencySample sample : samples) {
            assertEquals("NET", sample.mode());
            assertEquals("client->host", sample.direction());
            assertEquals(sample.localShownNanos(), sample.remoteShownNanos());
            assertTrue(sample.localMillis() >= 0.0);
        }

        host.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(host.isAlive(), "클라이언트가 닫히면 host도 끝나야 한다");
        assertEquals(0, hostCode.get(), "host 종료 코드");
    }

    @Test
    void 닫힌_포트로_접속하면_종료_코드_2와_한_줄_오류를_낸다() throws Exception {
        Result result = run("client", "127.0.0.1", String.valueOf(closedPort()), "--count", "1");

        assertEquals(2, result.code(), result.out());
        assertTrue(result.err().contains("연결"), result.err());
        assertEquals(1, result.err().strip().lines().count(), "오류는 한 줄이어야 한다: " + result.err());
        assertFalse(result.err().contains("\tat tetris."), "스택 트레이스를 내보내지 않는다: " + result.err());
    }

    @Test
    void 잘못된_IP_주소는_종료_코드_2로_거부한다() {
        Result result = run("client", "999.1.1.1");

        assertEquals(2, result.code(), result.out());
        assertEquals(1, result.err().strip().lines().count(), "오류는 한 줄이어야 한다: " + result.err());
    }

    @Test
    void 하위_명령이_없으면_사용법_오류로_종료_코드_1을_낸다() {
        Result result = run();

        assertEquals(1, result.code());
        assertTrue(result.err().contains("사용법"), result.err());
    }

    @Test
    void info는_포트와_bind_가능_여부를_출력한다() {
        Result result = run("info", "0");

        assertEquals(0, result.code(), result.err());
        assertTrue(result.out().contains("포트"), result.out());
        assertTrue(result.out().contains("bind"), result.out());
        assertEquals("", result.err(), "성공 시 표준 오류는 비어 있어야 한다");
    }
}
