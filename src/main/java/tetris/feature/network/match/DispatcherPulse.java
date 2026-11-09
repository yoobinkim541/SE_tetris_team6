package tetris.feature.network.match;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 데몬 스케줄러가 주기마다 dispatcher에 tick을 넘긴다. dispatcher가 밀려 있으면 tick을 쌓지 않고 합친다.
 * Swing Timer에 의존하지 않아 EDT와 테스트용 단일 스레드 executor에서 똑같이 동작한다.
 */
public final class DispatcherPulse implements MatchPulse {
    private final Executor dispatcher;

    public DispatcherPulse(Executor dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    @Override
    public Handle start(int periodMillis, Runnable tick) {
        if (periodMillis <= 0) throw new IllegalArgumentException("period must be positive");
        Objects.requireNonNull(tick, "tick");
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "match-pulse");
            thread.setDaemon(true);
            return thread;
        });
        AtomicBoolean queued = new AtomicBoolean();
        AtomicBoolean stopped = new AtomicBoolean();
        scheduler.scheduleAtFixedRate(() -> {
            if (stopped.get() || !queued.compareAndSet(false, true)) return;
            try {
                dispatcher.execute(() -> {
                    queued.set(false);
                    if (!stopped.get()) tick.run();
                });
            } catch (RuntimeException rejected) {
                queued.set(false); // dispatcher가 멈췄다. 다음 주기에 다시 시도하거나 close로 끝난다
            }
        }, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
        return () -> {
            stopped.set(true);
            scheduler.shutdownNow();
        };
    }
}
