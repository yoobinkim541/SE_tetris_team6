package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.network.tools.LatencyCsv;
import tetris.feature.network.tools.LatencySample;
import tetris.feature.network.tools.LatencyStats;

/** S11-C1: 지연 표본·백분위수 통계·CSV 직렬화 단위 테스트 (R3-LATENCY) */
@Timeout(30)
class LatencyStatsTest {

    @TempDir
    Path tempDir;

    @Test
    void 열개_표본의_p50_p95_최대_초과건수를_계산한다() {
        List<Double> samples = List.of(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);
        LatencyStats stats = LatencyStats.of(samples, 50.0);

        assertEquals(10, stats.count());
        assertEquals(50.0, stats.p50(), 1e-6);
        assertEquals(100.0, stats.p95(), 1e-6);
        assertEquals(100.0, stats.max(), 1e-6);
        assertEquals(5, stats.overThreshold());
        assertEquals(50.0, stats.thresholdMillis(), 1e-6);
    }

    @Test
    void 표본이_한개면_p50_p95_최대가_모두_같다() {
        LatencyStats stats = LatencyStats.of(List.of(7.0), 200.0);

        assertEquals(1, stats.count());
        assertEquals(7.0, stats.p50(), 1e-6);
        assertEquals(7.0, stats.p95(), 1e-6);
        assertEquals(7.0, stats.max(), 1e-6);
        assertEquals(0, stats.overThreshold());
    }

    @Test
    void 빈_표본_목록은_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> LatencyStats.of(List.of(), 200.0));
    }

    @Test
    void 임계값과_정확히_같은_200ms는_초과가_아니다() {
        LatencyStats stats = LatencyStats.of(List.of(120.0, 200.0, 200.5), 200.0);

        assertEquals(3, stats.count());
        assertEquals(1, stats.overThreshold());
    }

    @Test
    void 표시_시각이_입력_시각보다_이르면_거부한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LatencySample("CLASSIC", "host->client", 1L, 1_000_000L, 999_999L, 1_500_000L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LatencySample("CLASSIC", "host->client", 1L, 1_000_000L, 1_500_000L, 999_999L));

        LatencySample unrecordedRemote =
                new LatencySample("CLASSIC", "host->client", 2L, 1_000_000L, 2_500_000L, -1L);
        assertEquals(1.5, unrecordedRemote.localMillis(), 1e-6);
        assertEquals(-1.0, unrecordedRemote.remoteMillis(), 1e-6);
    }

    @Test
    void CSV_쓰기와_읽기_왕복_결과가_동일하다() throws Exception {
        Path csv = tempDir.resolve("samples.csv");
        List<LatencySample> expected = List.of(
                new LatencySample("CLASSIC", "host->client", 1L, 1_000_000_000L, 1_012_500_000L, 1_045_000_000L),
                new LatencySample("ITEM", "client->host", 2L, 2_000_000_000L, 2_008_000_000L, -1L));

        LatencyCsv.write(csv, expected);
        List<LatencySample> actual = LatencyCsv.read(csv);

        assertEquals(expected, actual);
        assertEquals(12.5, actual.get(0).localMillis(), 1e-6);
        assertEquals(45.0, actual.get(0).remoteMillis(), 1e-6);
        assertEquals(8.0, actual.get(1).localMillis(), 1e-6);
        assertEquals(-1.0, actual.get(1).remoteMillis(), 1e-6);
    }

    @Test
    void 헤더가_다른_CSV는_IOException으로_거부한다() throws Exception {
        Path badCsv = tempDir.resolve("bad.csv");
        Files.writeString(
                badCsv,
                "wrong,header,columns\nCLASSIC,host->client,1,100,200,300\n",
                StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> LatencyCsv.read(badCsv));
    }
}
