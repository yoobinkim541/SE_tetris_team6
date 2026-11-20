package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.lobby.LinkHealth;
import tetris.feature.network.lobby.LinkStats;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.ui.versus.NetDebugText;

/** 개발자 모드 연결 상태 표시 문구 (dev-net-overlay). 화면 없이 순수 함수만 검증한다 */
@Timeout(30)
class NetDebugTextTest {
    private static LinkStats stats(Role role, LinkHealth.Status status, long last, long min, long avg, long max,
                                   int samples, long sinceFrame, int frames, int states, long pings, long pongs) {
        return new LinkStats(role, "192.168.0.11", status, last, min, avg, max, samples, sinceFrame, frames, states,
                pings, pongs);
    }

    @Test
    void 연결이_없으면_한_줄() {
        assertEquals(List.of("[DEV] 연결 없음"), NetDebugText.lines(Optional.empty()));
    }

    @Test
    void 호스트_정상이면_네_줄_전체() {
        LinkStats connected = stats(Role.HOST, LinkHealth.Status.NORMAL, 12L, 3L, 8L, 21L, 20,
                120L, 31, 30, 140L, 139L);

        assertEquals(List.of(
                "[DEV] 호스트 · 192.168.0.11 · 정상",
                "RTT 12ms (최소 3 / 평균 8 / 최대 21, 20회)",
                "마지막 수신 120ms 전 · 수신 31/s · STATE 30/s",
                "PING 140 / PONG 139"), NetDebugText.lines(Optional.of(connected)));
    }

    @Test
    void 표본이_없으면_RTT는_응답_없음() {
        LinkStats waiting = stats(Role.CLIENT, LinkHealth.Status.NORMAL, -1L, -1L, -1L, -1L, 0,
                0L, 0, 0, 1L, 0L);

        List<String> lines = NetDebugText.lines(Optional.of(waiting));

        assertEquals(4, lines.size());
        assertEquals("RTT - (응답 없음)", lines.get(1));
        assertEquals("PING 1 / PONG 0", lines.get(3));
    }

    @Test
    void 각_상태를_한국어로_쓴다() {
        assertEquals("[DEV] 클라이언트 · 192.168.0.11 · 지연", first(LinkHealth.Status.LAGGING));
        assertEquals("[DEV] 클라이언트 · 192.168.0.11 · 끊김", first(LinkHealth.Status.LOST));
        assertEquals("[DEV] 클라이언트 · 192.168.0.11 · 상대 나감", first(LinkHealth.Status.PEER_LEFT));
        assertEquals("[DEV] 클라이언트 · 192.168.0.11 · 확인 중", first(LinkHealth.Status.NONE));
    }

    private static String first(LinkHealth.Status status) {
        return NetDebugText.lines(Optional.of(stats(Role.CLIENT, status, 5L, 5L, 5L, 5L, 1, 10L, 2, 1, 2L, 1L)))
                .get(0);
    }
}
