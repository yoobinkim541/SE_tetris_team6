package tetris.ui;

import tetris.feature.game.GameClock;

/** javax.swing.Timer 기반 GameClock (TMR-1). coalesce를 켜서 밀린 Tick을 몰아서 처리하지 않는다. */
public class SwingGameClock implements GameClock {
    /** onTick: 보통 session::tick */
    public SwingGameClock(Runnable onTick) {
        // Timer 준비
    }

    @Override
    public void start(int intervalMillis) {
        // 전체 간격으로 (재)시작
    }

    @Override
    public void stop() {
        // 정지
    }
}
