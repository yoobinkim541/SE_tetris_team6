package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.ConnectionHealth;
import tetris.feature.network.ConnectionHealth.Status;
import tetris.feature.network.HeartbeatPolicy;

/** S10-C1: 가짜 시계 기준 지연·회복·끊김 순수 판정기 검증 (R3-LAG, R3-DISCONNECT) */
@Timeout(30)
class ConnectionHealthTest {

    @Test
    void 처음에는_NORMAL이다() {
        ConnectionHealth health = new ConnectionHealth(HeartbeatPolicy.DEFAULT, 1_000L);
        assertEquals(Status.NORMAL, health.status());
        assertEquals(Status.NORMAL, health.evaluate(1_000L));
        assertEquals(-1L, health.lastRttMillis());
    }

    @Test
    void RTT_201ms_3회면_LAGGING이고_2회면_NORMAL() {
        ConnectionHealth health = new ConnectionHealth(HeartbeatPolicy.DEFAULT, 0L);
        health.onRtt(201, 100);
        assertEquals(Status.NORMAL, health.status());
        assertEquals(201L, health.lastRttMillis());

        health.onRtt(201, 200);
        assertEquals(Status.NORMAL, health.status());

        health.onRtt(201, 300);
        assertEquals(Status.LAGGING, health.status());
    }

    @Test
    void 정확히_200ms는_지연이_아니다() {
        ConnectionHealth health = new ConnectionHealth(HeartbeatPolicy.DEFAULT, 0L);
        health.onRtt(201, 100);
        health.onRtt(201, 200);
        health.onRtt(200, 300);
        assertEquals(Status.NORMAL, health.status());

        health.onRtt(201, 400);
        health.onRtt(201, 500);
        assertEquals(Status.NORMAL, health.status());
    }

    @Test
    void 무수신_1499ms는_NORMAL_1500ms는_LAGGING() {
        ConnectionHealth health = new ConnectionHealth(HeartbeatPolicy.DEFAULT, 0L);
        assertEquals(Status.NORMAL, health.evaluate(1_499L));
        assertEquals(Status.LAGGING, health.evaluate(1_500L));
        assertEquals(Status.LAGGING, health.status());
    }

    @Test
    void 무수신_4999ms는_LOST가_아니고_5000ms는_LOST() {
        ConnectionHealth health = new ConnectionHealth(HeartbeatPolicy.DEFAULT, 0L);
        assertEquals(Status.LAGGING, health.evaluate(4_999L));
        assertEquals(Status.LOST, health.evaluate(5_000L));
        assertEquals(Status.LOST, health.status());
    }

    @Test
    void 프레임을_받으면_무수신_시간이_다시_시작된다() {
        ConnectionHealth health = new ConnectionHealth(HeartbeatPolicy.DEFAULT, 0L);
        assertEquals(Status.NORMAL, health.evaluate(1_200L));
        health.onFrame(1_200L);

        assertEquals(Status.NORMAL, health.evaluate(2_699L));
        assertEquals(Status.LAGGING, health.evaluate(2_700L));
        assertEquals(Status.LAGGING, health.evaluate(6_199L));
        assertEquals(Status.LOST, health.evaluate(6_200L));
    }

    @Test
    void LAGGING에서_RTT_150ms_3회면_NORMAL_151ms면_회복_안됨() {
        ConnectionHealth health = new ConnectionHealth(HeartbeatPolicy.DEFAULT, 0L);
        assertEquals(Status.LAGGING, health.evaluate(1_500L));

        health.onRtt(151, 1_600L);
        health.onRtt(151, 1_700L);
        health.onRtt(151, 1_800L);
        assertEquals(Status.LAGGING, health.status());

        health.onRtt(150, 1_900L);
        assertEquals(Status.LAGGING, health.status());
        health.onRtt(150, 2_000L);
        assertEquals(Status.LAGGING, health.status());
        health.onRtt(150, 2_100L);
        assertEquals(Status.NORMAL, health.status());
    }

    @Test
    void 회복_RTT_사이에_지연_RTT가_끼면_회복_카운트가_초기화된다() {
        ConnectionHealth health = new ConnectionHealth(HeartbeatPolicy.DEFAULT, 0L);
        assertEquals(Status.LAGGING, health.evaluate(1_500L));

        health.onRtt(150, 1_600L);
        health.onRtt(150, 1_700L);
        health.onRtt(205, 1_800L);
        health.onRtt(150, 1_900L);
        health.onRtt(150, 2_000L);
        assertEquals(Status.LAGGING, health.status());

        health.onRtt(150, 2_100L);
        assertEquals(Status.NORMAL, health.status());
    }

    @Test
    void LOST는_되돌릴_수_없다() {
        ConnectionHealth health = new ConnectionHealth(HeartbeatPolicy.DEFAULT, 0L);
        assertEquals(Status.LOST, health.evaluate(5_000L));

        health.onFrame(5_001L);
        assertEquals(Status.LOST, health.status());

        health.onRtt(10, 5_002L);
        health.onRtt(10, 5_003L);
        health.onRtt(10, 5_004L);
        assertEquals(Status.LOST, health.status());
        assertEquals(Status.LOST, health.evaluate(5_005L));
        assertEquals(-1L, health.lastRttMillis());
    }

    @Test
    void HeartbeatPolicy는_잘못된_값을_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatPolicy(0, 200, 3, 1_500, 5_000, 150, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatPolicy(500, 0, 3, 1_500, 5_000, 150, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatPolicy(500, 200, 0, 1_500, 5_000, 150, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatPolicy(500, 200, 3, 0, 5_000, 150, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatPolicy(500, 200, 3, 1_500, 0, 150, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatPolicy(500, 200, 3, 1_500, 5_000, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatPolicy(500, 200, 3, 1_500, 5_000, 150, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatPolicy(500, 200, 3, 1_500, 5_000, 200, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatPolicy(500, 200, 3, 5_000, 5_000, 150, 3));
    }
}
