package tetris.feature.network.lobby;

import java.util.Objects;

/**
 * 개발자 모드 연결 상태 표시용 통계 (dev-net-overlay). 화면은 이 값을 읽어 RTT·수신 간격·수신 빈도를 보여 준다.
 *
 * <p>RTT 값은 표본이 없으면 -1이다. {@code rttSamples}는 최근 20개까지만 남긴 표본 수이고,
 * {@code framesLastSecond}/{@code statesLastSecond}는 조회 시점 기준 최근 1초 창의 수신 수다.
 * {@code millisSinceLastFrame}은 마지막 수신(없으면 연결 수립) 뒤 지난 시간이다.
 */
public record LinkStats(
        LobbySnapshot.Role role,
        String peerAddress,
        LinkHealth.Status status,
        long lastRttMillis,
        long minRttMillis,
        long avgRttMillis,
        long maxRttMillis,
        int rttSamples,
        long millisSinceLastFrame,
        int framesLastSecond,
        int statesLastSecond,
        long pingsSent,
        long pongsReceived) {

    /** RTT 표본을 유지하는 최대 개수 */
    public static final int MAX_RTT_SAMPLES = 20;
    /** 수신 빈도를 세는 창 */
    public static final long FREQUENCY_WINDOW_MILLIS = 1_000L;

    public LinkStats {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(peerAddress, "peerAddress");
        Objects.requireNonNull(status, "status");
        if (rttSamples < 0 || rttSamples > MAX_RTT_SAMPLES) {
            throw new IllegalArgumentException("rttSamples outside 0.." + MAX_RTT_SAMPLES);
        }
        if (millisSinceLastFrame < 0) {
            throw new IllegalArgumentException("millisSinceLastFrame must not be negative");
        }
        if (framesLastSecond < 0 || statesLastSecond < 0 || pingsSent < 0 || pongsReceived < 0) {
            throw new IllegalArgumentException("counters must not be negative");
        }
    }
}
