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
    private boolean online;
    private boolean forfeitArmed;
    private BattlePanel panel;
    private BattleKeyMap keys;
    private javax.swing.Timer forfeitTimeout;

    public BattleScreenController(JRootPane root, BattleClient client, BattleKeyMap keys,
            BattlePanel panel, Consumer<MatchSnapshot> onResult, BooleanSupplier confirmQuit, Runnable onExit) {
        this(root, client, keys, panel, onResult, legacyConfirmation(confirmQuit), onExit);
    }

    /**
     * P2P 경기 화면 (S9-B2). 결과는 호스트가 정하므로 기권을 보낸 뒤에도 결과 STATE를 기다리고,
     * 이 창의 포커스가 빠져도 두 PC의 경기를 자동으로 멈추지 않는다(눌린 키만 놓는다).
     * 기권은 모달 확인창 대신 Q를 3초 안에 한 번 더 누르는 것으로 확인한다. 확인창이 떠 있는 동안 상대가 Resume하면
     * 이 화면은 입력을 못 하는 채로 블록이 떨어지기 때문이다. 첫 Q는 경기를 멈추지 않고, 다른 키를 누르면 취소된다.
     */
    public static BattleScreenController online(JRootPane root, BattleClient client, BattleKeyMap keys,
            BattlePanel panel, Consumer<MatchSnapshot> onResult) {
        var controller = new BattleScreenController(root, client, keys, panel, onResult,
                (Supplier<PlayerId>) () -> null, () -> { });
        controller.online = true;
        controller.panel = panel;
        controller.keys = keys;
        controller.forfeitTimeout = new javax.swing.Timer(FORFEIT_CONFIRM_MILLIS, e -> controller.disarmForfeit());
        controller.forfeitTimeout.setRepeats(false);
        return controller;
    }

    /** 온라인 기권 확인 대기 시간 */
    public static final int FORFEIT_CONFIRM_MILLIS = 3000;

    private void onlineQuit() {
        if (forfeitArmed) {
            disarmForfeit();
            dispatch(new PlayerCommand(PlayerId.ONE, GameAction.QUIT, true, 0));
            return;
        }
        forfeitArmed = true;
        panel.setNotice("기권하려면 " + java.awt.event.KeyEvent.getKeyText(keys.keyOf(PlayerId.ONE, GameAction.QUIT))
                + "를 한 번 더 누르세요");
        forfeitTimeout.restart();
    }

    private void disarmForfeit() {
        if (!forfeitArmed) return;
        forfeitArmed = false;
        forfeitTimeout.stop();
        panel.setNotice("");
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
        if (online) {
            if (command.action() == GameAction.QUIT) { onlineQuit(); return; }
            disarmForfeit();
        }
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
                if (!online && current.phase() != MatchPhase.FINISHED) { close(); onExit.run(); }
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
        if (!online && !confirming && current != null && current.phase() == MatchPhase.PLAYING) pauseCommand();
    }

    @Override public void close() {
        if (closed) return;
        router.close();
        closed = true;
        if (forfeitTimeout != null) forfeitTimeout.stop();
        if (subscription != null) subscription.close();
        client.close();
    }
}
