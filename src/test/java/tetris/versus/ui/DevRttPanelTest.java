package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.tools.LatencyCsv;
import tetris.feature.network.tools.LatencySample;
import tetris.ui.versus.DevRttPanel;

/** 개발자 모드 P2P 로비 측정 패널: 표본 통계 표시, 200회 측정 요청, NetTool과 같은 CSV 저장 */
class DevRttPanelTest {
    @TempDir Path directory;

    static final class RttLobby extends P2pLobbyControllerTest.FakeLobby {
        final List<Consumer<LatencySample>> rttListeners = new ArrayList<>();
        final List<int[]> probes = new ArrayList<>();
        @Override public Subscription subscribeRtt(Consumer<LatencySample> listener) {
            rttListeners.add(listener);
            return () -> rttListeners.remove(listener);
        }
        @Override public void probeRtt(int count, int intervalMillis) { probes.add(new int[]{count, intervalMillis}); }
        void pong(long nonce, double millis) {
            long sent = 1_000_000_000L;
            long received = sent + Math.round(millis * 1_000_000);
            var sample = new LatencySample("NET", "client->host", nonce, sent, received, received);
            for (var listener : List.copyOf(rttListeners)) listener.accept(sample);
        }
    }

    @Test
    void showsStatsMeasuresTwoHundredPingsAndSavesTheNetToolCsv() throws Exception {
        var lobby = new RttLobby();
        var panel = new DevRttPanel(lobby, directory, () -> LocalDateTime.of(2026, 11, 16, 22, 30, 5));
        assertTrue(panel.statsText().startsWith("RTT 표본 없음"), panel.statsText());
        assertNull(panel.save(), "표본이 없으면 저장하지 않는다");

        lobby.pong(1, 12.0);
        lobby.pong(2, 250.0);
        assertTrue(panel.statsText().startsWith("RTT 표본 2 · 최근 250.0ms"), panel.statsText());
        assertTrue(panel.statsText().endsWith("200ms 초과 1건"), panel.statsText());

        panel.measureButton().doClick();
        assertEquals(1, lobby.probes.size());
        assertArrayEquals(new int[]{200, 50}, lobby.probes.getFirst());
        assertTrue(panel.samples().isEmpty(), "새 측정은 이전 표본을 비운다");

        for (int i = 1; i <= 3; i++) lobby.pong(i, 10.0 + i);
        Path saved = panel.save();
        assertEquals(directory.resolve("rtt-20261116-223005.csv"), saved);
        var rows = LatencyCsv.read(saved);
        assertEquals(3, rows.size());
        assertEquals(13.0, rows.getLast().localMillis(), 1e-6);
        assertTrue(panel.fileText().startsWith("저장: "), panel.fileText());

        panel.close();
        assertTrue(lobby.rttListeners.isEmpty(), "닫으면 구독을 해제한다");
    }
}
