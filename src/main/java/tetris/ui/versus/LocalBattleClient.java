package tetris.ui.versus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.*;

/** Owns the real local engine and its EDT pulse. Does not save versus scores. */
public final class LocalBattleClient implements BattleClient {
    private final LocalMatchEngine engine = new LocalMatchEngine();
    private final MatchClock time = MatchClock.system();
    private final Timer pulse;
    private final List<Consumer<MatchSnapshot>> listeners = new ArrayList<>();
    private boolean closed;

    public LocalBattleClient(MatchConfig config) {
        requireEdt(); engine.start(config);
        pulse = new Timer(VersusRules.MATCH_PULSE_MILLIS, event -> advance());
        pulse.setCoalesce(true); pulse.start();
    }

    @Override public void send(PlayerCommand command) {
        requireEdt(); if (closed) return;
        engine.submit(command);
        // Pause must publish synchronously before a modal quit dialog opens. Gameplay
        // stays queued for the common pulse so both players can lose in the same frame.
        if (command.action() == GameAction.PAUSE || command.action() == GameAction.QUIT) advance();
    }

    private void advance() {
        // Closing input bindings releases keys synchronously from a result callback.
        // A release must not re-publish the terminal state during that callback.
        if (closed || engine.snapshot().phase() == MatchPhase.FINISHED) return;
        engine.advance(time.nowMillis());
        MatchSnapshot state = engine.snapshot();
        if (state.phase() == MatchPhase.FINISHED) pulse.stop();
        for (var listener : List.copyOf(listeners)) {
            if (closed) break;
            if (listeners.contains(listener)) listener.accept(state);
        }
    }

    @Override public Subscription subscribe(Consumer<MatchSnapshot> listener) {
        requireEdt(); if (closed) throw new IllegalStateException("client closed");
        Objects.requireNonNull(listener); listeners.add(listener); listener.accept(engine.snapshot());
        return () -> { requireEdt(); listeners.remove(listener); };
    }

    @Override public void close() {
        requireEdt(); if (closed) return;
        closed = true; pulse.stop(); listeners.clear(); engine.close();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("local battle requires EDT");
    }
}
