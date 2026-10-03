package tetris.ui;

import javax.swing.Timer;
import tetris.feature.game.GameClock;

/** javax.swing.Timer 기반 GameClock (TMR-1). coalesce를 켜서 밀린 Tick을 몰아서 처리하지 않는다. */
public class SwingGameClock implements GameClock {
    private final Timer timer;

    /** onTick: 보통 session::tick */
    public SwingGameClock(Runnable onTick) {
        if (onTick == null) throw new IllegalArgumentException("onTick cannot be null");
        timer = new Timer(1000, e -> onTick.run());
        timer.setCoalesce(true);
        timer.setRepeats(true);
    }

    @Override
    public void start(int intervalMillis) {
        timer.stop();
        timer.setInitialDelay(intervalMillis); // 첫 Tick도 전체 간격 뒤 (TMR-3)
        timer.setDelay(intervalMillis);
        timer.start();
    }

    @Override
    public void stop() {
        timer.stop();
    }
}
