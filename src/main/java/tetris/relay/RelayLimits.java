package tetris.relay;

import java.time.Duration;
import java.util.Objects;

/**
 * 중계 서버 자원 제한 (설계 v2 §6.2). 기본값은 Oracle E2.1.Micro(1/8 OCPU, 1GB) 기준 출발값이며,
 * 배포 후 실측으로 조정한다.
 */
public record RelayLimits(int maxConnections, int maxPendingHandshakes, int newConnectionsPerIpPerSecond,
                          Duration handshakeDeadline, int maxWaitingRooms, Duration roomWaitLimit,
                          int maxPairedRooms, Duration idleLimit, Duration stalledWriteLimit,
                          Duration maxSessionLength, int noRoomPerIpPerMinute) {
    public static final RelayLimits DEFAULT = new RelayLimits(300, 64, 5, Duration.ofSeconds(5), 100,
            Duration.ofMinutes(10), 100, Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10);

    public RelayLimits {
        if (maxConnections <= 0 || maxPendingHandshakes <= 0 || newConnectionsPerIpPerSecond <= 0
                || maxWaitingRooms <= 0 || maxPairedRooms <= 0 || noRoomPerIpPerMinute <= 0) {
            throw new IllegalArgumentException("limits must be positive");
        }
        for (Duration d : new Duration[]{handshakeDeadline, roomWaitLimit, idleLimit, stalledWriteLimit, maxSessionLength}) {
            Objects.requireNonNull(d);
            if (d.isZero() || d.isNegative()) throw new IllegalArgumentException("durations must be positive");
        }
    }
}
