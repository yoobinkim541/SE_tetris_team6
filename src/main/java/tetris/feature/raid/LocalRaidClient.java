package tetris.feature.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import tetris.feature.raid.api.RaidClient;
import tetris.feature.raid.api.RaidConfig;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.MatchClock;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

/** 로컬 솔로·협동 레이드. 엔진을 EDT 16ms pulse로 돌린다 */
public final class LocalRaidClient implements RaidClient {
    /** 테스트가 펄스를 직접 쏘려고 넘기는 시계 대용 */
    public interface Scheduler {
        void start(Runnable onPulse);

        void stop();
    }

    private final RaidEngine engine;
    private final MatchClock time;
    private final Scheduler scheduler;
    private final List<Consumer<RaidSnapshot>> listeners = new ArrayList<>();
    private boolean closed;

    public LocalRaidClient(RaidConfig config) {
        this(config, MatchClock.system(), swingScheduler());
    }

    /** 테스트용. clock·scheduler는 호출자가 소유하고, 콜백은 이 메서드를 부른 스레드(EDT)에서 온다 */
    public LocalRaidClient(RaidConfig config, MatchClock clock, Scheduler scheduler) {
        requireEdt();
        this.time = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.engine = new RaidEngine(Objects.requireNonNull(config, "config"), clock);
        scheduler.start(this::advance);
    }

    @Override
    public void send(PlayerCommand command) {
        requireEdt();
        if (closed) return;
        engine.submit(command);
        if (command.action() == GameAction.PAUSE || command.action() == GameAction.QUIT) advance();
    }

    @Override
    public void ultimate(PlayerId player) {
        requireEdt();
        if (closed) return;
        engine.ultimate(player);
        publish();
    }

    @Override
    public void pickCard(PlayerId player, int index) {
        requireEdt();
        if (closed) return;
        engine.pickCard(player, index);
        publish();
    }

    @Override
    public BattleClient.Subscription subscribe(Consumer<RaidSnapshot> listener) {
        requireEdt();
        if (closed) throw new IllegalStateException("client closed");
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(engine.snapshot());
        return () -> {
            requireEdt();
            listeners.remove(listener);
        };
    }

    @Override
    public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        scheduler.stop();
        listeners.clear();
        engine.close();
    }

    private void advance() {
        if (closed || engine.snapshot().phase() == RaidPhase.FINISHED) return;
        engine.advance(time.nowMillis());
        publish();
    }

    private void publish() {
        if (closed) return;
        RaidSnapshot state = engine.snapshot();
        if (state.phase() == RaidPhase.FINISHED) scheduler.stop();
        for (var listener : List.copyOf(listeners)) {
            if (closed) break;
            if (listeners.contains(listener)) listener.accept(state);
        }
    }

    private static Scheduler swingScheduler() {
        return new Scheduler() {
            private Timer timer;

            @Override
            public void start(Runnable onPulse) {
                timer = new Timer(VersusRules.MATCH_PULSE_MILLIS, event -> onPulse.run());
                timer.setCoalesce(true);
                timer.start();
            }

            @Override
            public void stop() {
                if (timer != null) timer.stop();
            }
        };
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("local raid requires EDT");
    }
}
