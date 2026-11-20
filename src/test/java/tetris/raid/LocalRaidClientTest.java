package tetris.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.raid.LocalRaidClient;
import tetris.feature.raid.api.RaidConfig;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.ControlStyle;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;

@Timeout(30)
class LocalRaidClientTest {
    @Test
    void countdownPauseAndQuitPublishOnTheEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            long[] now = {0};
            ManualScheduler scheduler = new ManualScheduler();
            try (LocalRaidClient client = new LocalRaidClient(
                    new RaidConfig(RaidMode.SOLO, 1, ControlStyle.CLASSIC), () -> now[0], scheduler)) {
                AtomicReference<RaidSnapshot> latest = new AtomicReference<>();
                AtomicInteger callbacks = new AtomicInteger();
                var subscription = client.subscribe(snapshot -> {
                    latest.set(snapshot);
                    callbacks.incrementAndGet();
                });
                assertEquals(RaidPhase.COUNTDOWN, latest.get().phase());
                now[0] = 3_000;
                scheduler.fire();
                assertEquals(RaidPhase.PLAYING, latest.get().phase());
                client.send(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, true, 0));
                assertEquals(RaidPhase.PAUSED, latest.get().phase());
                client.send(new PlayerCommand(PlayerId.ONE, GameAction.QUIT, true, 1));
                assertEquals(RaidPhase.FINISHED, latest.get().phase());
                assertTrue(latest.get().result() != null);
                int seen = callbacks.get();
                subscription.close();
                scheduler.fire();
                assertEquals(seen, callbacks.get());
            }
        });
    }

    @Test
    void offEdtIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> new LocalRaidClient(new RaidConfig(RaidMode.SOLO, 1, ControlStyle.CLASSIC)));
    }

    private static final class ManualScheduler implements LocalRaidClient.Scheduler {
        private Runnable pulse;

        public void start(Runnable onPulse) { pulse = onPulse; }
        public void stop() { pulse = null; }
        void fire() { if (pulse != null) pulse.run(); }
    }
}
