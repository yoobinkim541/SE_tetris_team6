package tetris.ui.versus;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JRootPane;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.*;

/** Owns the UI subscription and root input bindings for exactly one local match. EDT confined. */
public final class BattleScreenController implements AutoCloseable {
    private final BattleClient client;
    private final BattleInputRouter router;
    private BattleClient.Subscription subscription;
    private final Consumer<MatchSnapshot> onResult;
    private final BooleanSupplier confirmQuit;
    private final Runnable onExit;
    private MatchSnapshot current;
    private boolean confirming;
    private boolean closed;
    private long sequence;

    public BattleScreenController(JRootPane root, BattleClient client, BattleKeyMap keys,
            BattlePanel panel, Consumer<MatchSnapshot> onResult, BooleanSupplier confirmQuit, Runnable onExit) {
        this.client = Objects.requireNonNull(client);
        this.onResult = Objects.requireNonNull(onResult);
        this.confirmQuit = Objects.requireNonNull(confirmQuit);
        this.onExit = Objects.requireNonNull(onExit);
        router = new BattleInputRouter(root, keys, this::send, this::accepts);
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
            boolean quit;
            try { quit = confirmQuit.getAsBoolean(); }
            finally { confirming = false; }
            if (closed) return;
            if (quit) { dispatch(command); close(); onExit.run(); }
            else if (wasPlaying && current.phase() == MatchPhase.PAUSED) pauseCommand();
        } else {
            if (command.action() == GameAction.PAUSE) router.clearGameplayKeys();
            dispatch(command);
        }
    }

    private void pauseCommand() {
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
