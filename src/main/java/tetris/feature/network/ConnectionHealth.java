package tetris.feature.network;

import java.util.Objects;

/**
 * 시계를 주입받아 프레임 수신 시각과 RTT로 연결 상태(NORMAL/LAGGING/LOST)를 판정하는 순수 상태 기계 (S10-C1).
 */
public final class ConnectionHealth {
    public enum Status {
        NORMAL,
        LAGGING,
        LOST
    }

    private final HeartbeatPolicy policy;
    private long lastActivityAt;
    private Status status = Status.NORMAL;
    private long lastRttMillis = -1;
    private int highRttCount;
    private int lowRttCount;

    public ConnectionHealth(HeartbeatPolicy policy, long nowMillis) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.lastActivityAt = nowMillis;
    }

    /** 정상 프레임을 받았을 때 무수신 기준 시각을 갱신한다 */
    public void onFrame(long nowMillis) {
        if (status == Status.LOST) {
            return;
        }
        lastActivityAt = nowMillis;
        evaluate(nowMillis);
    }

    /** 유효한 PONG으로 계산한 RTT를 반영한다 (프레임 수신으로도 친다) */
    public void onRtt(long rttMillis, long nowMillis) {
        if (status == Status.LOST) {
            return;
        }
        lastActivityAt = nowMillis;
        lastRttMillis = Math.max(0L, rttMillis);
        if (lastRttMillis > policy.lagRttMillis()) {
            highRttCount++;
            lowRttCount = 0;
        } else if (lastRttMillis <= policy.recoverRttMillis()) {
            lowRttCount++;
            highRttCount = 0;
        } else {
            highRttCount = 0;
            lowRttCount = 0;
        }
        evaluate(nowMillis);
    }

    /** 현재 시각 기준으로 지연·회복·끊김을 판정하고 상태를 돌려준다 */
    public Status evaluate(long nowMillis) {
        if (status == Status.LOST) {
            return Status.LOST;
        }
        long silence = nowMillis - lastActivityAt;
        if (silence >= policy.lostSilenceMillis()) {
            status = Status.LOST;
            highRttCount = 0;
            lowRttCount = 0;
            return status;
        }
        if (status == Status.NORMAL) {
            if (highRttCount >= policy.lagRttCount() || silence >= policy.lagSilenceMillis()) {
                status = Status.LAGGING;
                highRttCount = 0;
                lowRttCount = 0;
            }
        } else if (status == Status.LAGGING) {
            if (lowRttCount >= policy.recoverRttCount() && silence < policy.lagSilenceMillis()) {
                status = Status.NORMAL;
                highRttCount = 0;
                lowRttCount = 0;
            }
        }
        return status;
    }

    public Status status() {
        return status;
    }

    public long lastRttMillis() {
        return lastRttMillis;
    }
}
