package tetris.feature.network.lobby;

import java.util.ArrayDeque;

/**
 * 연결 하나의 개발자 모드 통계를 모은다 (dev-net-overlay). 고정 크기만 쓴다: RTT 최근 {@link LinkStats#MAX_RTT_SAMPLES}개,
 * 수신 시각 최근 {@link LinkStats#FREQUENCY_WINDOW_MILLIS}. 모두 dispatcher 스레드에서만 읽고 쓴다.
 */
final class LinkStatsCollector {
    private final LobbySnapshot.Role role;
    private final String peerAddress;
    private final ArrayDeque<Long> rtts = new ArrayDeque<>();
    private final ArrayDeque<Long> frameTimes = new ArrayDeque<>();
    private final ArrayDeque<Long> stateTimes = new ArrayDeque<>();
    private long lastFrameAt;
    private long lastRttMillis = -1;
    private long pingsSent;
    private long pongsReceived;

    /** connectedAt: 연결 수립 시각. 아직 받은 프레임이 없으면 수신 경과 시간을 이 시각부터 센다 */
    LinkStatsCollector(LobbySnapshot.Role role, String peerAddress, long connectedAt) {
        this.role = role;
        this.peerAddress = peerAddress;
        this.lastFrameAt = connectedAt;
    }

    void onPingSent() {
        pingsSent++;
    }

    /** 알려진 nonce의 PONG. 표본은 최근 {@link LinkStats#MAX_RTT_SAMPLES}개만 남긴다. 프레임 수는 호출자가 센다 */
    void onPong(long rttMillis) {
        long rtt = Math.max(0L, rttMillis);
        pongsReceived++;
        lastRttMillis = rtt;
        rtts.addLast(rtt);
        while (rtts.size() > LinkStats.MAX_RTT_SAMPLES) rtts.removeFirst();
    }

    void onFrame(long now, boolean state) {
        lastFrameAt = now;
        frameTimes.addLast(now);
        if (state) stateTimes.addLast(now);
        prune(frameTimes, now);
        prune(stateTimes, now);
    }

    LinkStats snapshot(long now, LinkHealth.Status status) {
        prune(frameTimes, now);
        prune(stateTimes, now);
        return new LinkStats(role, peerAddress, status,
                lastRttMillis, minRtt(), avgRtt(), maxRtt(), rtts.size(),
                Math.max(0L, now - lastFrameAt), frameTimes.size(), stateTimes.size(),
                pingsSent, pongsReceived);
    }

    private long minRtt() {
        long min = -1;
        for (long rtt : rtts) min = min < 0 ? rtt : Math.min(min, rtt);
        return min;
    }

    private long avgRtt() {
        if (rtts.isEmpty()) return -1;
        long sum = 0;
        for (long rtt : rtts) sum += rtt;
        return sum / rtts.size(); // 정수 나눗셈 평균
    }

    private long maxRtt() {
        long max = -1;
        for (long rtt : rtts) max = Math.max(max, rtt);
        return max;
    }

    private static void prune(ArrayDeque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() > LinkStats.FREQUENCY_WINDOW_MILLIS) {
            times.removeFirst();
        }
    }
}
