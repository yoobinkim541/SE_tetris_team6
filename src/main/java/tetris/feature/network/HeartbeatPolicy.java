package tetris.feature.network;

/**
 * P2P heartbeat 주기 및 지연·회복·끊김 판정 임계치 (S10-C1, decisions.md).
 */
public record HeartbeatPolicy(
        long intervalMillis,
        long lagRttMillis,
        int lagRttCount,
        long lagSilenceMillis,
        long lostSilenceMillis,
        long recoverRttMillis,
        int recoverRttCount) {

    public static final HeartbeatPolicy DEFAULT =
            new HeartbeatPolicy(500, 200, 3, 1_500, 5_000, 150, 3);

    public HeartbeatPolicy {
        if (intervalMillis <= 0
                || lagRttMillis <= 0
                || lagRttCount <= 0
                || lagSilenceMillis <= 0
                || lostSilenceMillis <= 0
                || recoverRttMillis <= 0
                || recoverRttCount <= 0) {
            throw new IllegalArgumentException("all policy thresholds must be positive");
        }
        if (recoverRttMillis >= lagRttMillis) {
            throw new IllegalArgumentException("recoverRttMillis must be less than lagRttMillis");
        }
        if (lagSilenceMillis >= lostSilenceMillis) {
            throw new IllegalArgumentException("lagSilenceMillis must be less than lostSilenceMillis");
        }
    }
}
