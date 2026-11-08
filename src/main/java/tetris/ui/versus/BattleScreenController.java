package tetris.ui.versus;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JRootPane;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.*;

/** Owns the UI subscription and root input bindings for exactly one local match. EDT confined. */
public final class BattleScreenController implements AutoCloseable {
    private final BattleClient client;
    private final BattleInputRouter router;
    private BattleClient.Subscription subscription;
    private final Consumer<MatchSnapshot> onResult;
    private final Supplier<PlayerId> confirmQuit;
    private final Runnable onExit;
    private MatchSnapshot current;
    private boolean confirming;
    private boolean closed;
    private long sequence;

    public BattleScreenController(JRootPane root, BattleClient client, BattleKeyMap keys,
            BattlePanel panel, Consumer<MatchSnapshot> onResult, BooleanSupplier confirmQuit, Runnable onExit) {
        this(root, client, keys, panel, onResult, legacyConfirmation(confirmQuit), onExit);
    }

    private static Supplier<PlayerId> legacyConfirmation(BooleanSupplier confirm) {
        return confirm == null ? null : () -> confirm.getAsBoolean() ? PlayerId.ONE : null;
    }

    /** Shared Quit chooses the forfeiting player; null cancels and resumes the match. */
    public BattleScreenController(JRootPane root, BattleClient client, BattleKeyMap keys,
            BattlePanel panel, Consumer<MatchSnapshot> onResult, Supplier<PlayerId> confirmQuit, Runnable onExit) {
        this.client = Objects.requireNonNull(client);
        BattleInputRouter installed = null;
        try {
            this.onResult = Objects.requireNonNull(onResult);
            this.confirmQuit = Objects.requireNonNull(confirmQuit);
            this.onExit = Objects.requireNonNull(onExit);
            Objects.requireNonNull(panel);
            router = new BattleInputRouter(root, keys, this::send, this::accepts);
            installed = router;
            var subscribed = client.subscribe(snapshot -> {
                if (closed) return;
                MatchSnapshot previous = current;
                current = snapshot;
                router.resetForMatch(snapshot.matchId());
                if (previous != null && previous.phase() != snapshot.phase()) router.clearGameplayKeys();
                panel.setSnapshot(snapshot);
                if (snapshot.phase() == MatchPhase.FINISHED) {
                    router.close();
                    if (subscription != null) subscription.close();
                    this.onResult.accept(snapshot);
                }
            });
            subscription = subscribed;
            if (closed || current != null && current.phase() == MatchPhase.FINISHED) subscribed.close();
        } catch (RuntimeException | Error failure) {
            closed = true;
            if (installed != null) {
                try { installed.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            try { client.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private boolean accepts(GameAction action) {
        if (closed || confirming || current == null || current.phase() == MatchPhase.FINISHED) return false;
        return BattleKeyMap.isShared(action) || current.phase() == MatchPhase.PLAYING;
    }

    private void dispatch(PlayerCommand command) {
        client.send(new PlayerCommand(command.playerId(), command.action(), command.pressed(), sequence++));
    }

    private void send(PlayerCommand command) {
        if (closed) return;
        if (!command.pressed()) { dispatch(command); return; }
        if (command.action() == GameAction.QUIT) {
            confirming = true;
            router.clearGameplayKeys();
            boolean wasPlaying = current.phase() == MatchPhase.PLAYING;
            if (wasPlaying) pauseCommand();
            if (closed || current.phase() == MatchPhase.FINISHED) { confirming = false; return; }
            PlayerId forfeiting;
            try { forfeiting = confirmQuit.get(); }
            finally { confirming = false; }
            if (closed || current.phase() == MatchPhase.FINISHED) return;
            if (forfeiting != null) {
                dispatch(new PlayerCommand(forfeiting,GameAction.QUIT,true,0));
                // The real client publishes a forfeit result synchronously. Preserve
                // that result screen; only preview/legacy clients need an explicit exit.
                if (current.phase() != MatchPhase.FINISHED) { close(); onExit.run(); }
            }
            else if (wasPlaying && current.phase() == MatchPhase.PAUSED) pauseCommand();
        } else {
            if (command.action() == GameAction.PAUSE) router.clearGameplayKeys();
            dispatch(command);
        }
    }

    private void pauseCommand() {
        // Automatic pause/resume is a fresh transition even while physical P is held.
        dispatch(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, false, 0));
        dispatch(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, true, 0));
        dispatch(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, false, 0));
    }

    public void focusLost() {
        if (closed) return;
        router.clearPressedKeys();
        if (!confirming && current != null && current.phase() == MatchPhase.PLAYING) pauseCommand();
    }

    @Override public void close() {
        if (closed) return;
        router.close();
        closed = true;
        if (subscription != null) subscription.close();
        client.close();
    }
}
