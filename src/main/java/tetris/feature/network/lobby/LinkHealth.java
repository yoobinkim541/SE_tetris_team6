package tetris.feature.network.lobby;

import java.util.Objects;

/**
 * 로비·경기 연결의 건강 상태와 최근 RTT (S10-C1).
 */
public record LinkHealth(Status status, long rttMillis) {
    public enum Status {
        NONE,
        NORMAL,
        LAGGING,
        LOST,
        PEER_LEFT
    }

    public static final LinkHealth NONE = new LinkHealth(Status.NONE, -1);

    public LinkHealth {
        Objects.requireNonNull(status, "status");
    }
}
