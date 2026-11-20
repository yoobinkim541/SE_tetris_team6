package tetris.ui.raid;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import tetris.feature.raid.api.RaidClient;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.ControlStyle;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.ui.Messages;
import tetris.ui.versus.BattleInputRouter;
import tetris.ui.versus.BattleKeyMap;

/**
 * 레이드 한 판의 키. 솔로는 설정의 {@link KeyMap}, 협동은 로컬 {@link BattleKeyMap}.
 * 필살은 P1 {@code R}, 협동 P2 {@code Enter}. 카드 선택은 선택권자의 좌우·하드 드롭.
 * 모던 DAS는 누르고 떼는 명령만 보내고 엔진이 반복한다 (대전 라우터와 같은 규칙).
 */
public final class RaidScreenController implements AutoCloseable {
    private final RaidClient client;
    private final RaidPanel panel;
    private final boolean solo;
    private final Runnable onMenu;
    private final Runnable onRetry;
    private final JRootPane root;
    private final Set<GameAction> cardHeld = EnumSet.noneOf(GameAction.class);
    private BattleInputRouter battleRouter;
    private SoloInputRouter soloRouter;
    private BattleClient.Subscription subscription;
    private RaidSnapshot current;
    private int cardIndex = 1;
    private boolean pausedForConfirm;
    private boolean confirming;
    private boolean confirmYes;
    private boolean closed;
    private long sequence;

    public RaidScreenController(JRootPane root, RaidClient client, RaidPanel panel, KeyMap soloKeys, boolean solo,
                                ControlStyle style, Runnable onMenu, Runnable onRetry) {
        this(root, client, panel, soloKeys, solo, style, onMenu, onRetry, null);
    }

    private RaidScreenController(JRootPane root, RaidClient client, RaidPanel panel, KeyMap soloKeys, boolean solo,
                                 ControlStyle style, Runnable onMenu, Runnable onRetry, PlayerId onlineViewer) {
        this.root = Objects.requireNonNull(root, "root");
        this.client = Objects.requireNonNull(client, "client");
        this.panel = Objects.requireNonNull(panel, "panel");
        this.solo = solo;
        this.onMenu = Objects.requireNonNull(onMenu, "onMenu");
        this.onRetry = Objects.requireNonNull(onRetry, "onRetry");
        Objects.requireNonNull(style, "style");
        panel.setGuide(onlineViewer != null ? onlineGuide() : guide(solo, soloKeys));
        panel.setResultActions(this::goMenu, this::goRetry);
        panel.setConfirmAction(this::cancelConfirm);
        try {
            if (onlineViewer != null) {
                battleRouter = new BattleInputRouter(root, BattleKeyMap.online(), this::onCommand, this::accepts);
                install(KeyEvent.VK_R, () -> {
                    if (!closed && !confirming && current != null && current.phase() == RaidPhase.PLAYING) {
                        client.ultimate(onlineViewer);
                    }
                });
            } else if (solo) soloRouter = new SoloInputRouter(root, soloKeys, this::onCommand, this::accepts);
            else battleRouter = new BattleInputRouter(root, new BattleKeyMap(), this::onCommand, this::accepts);
            if (onlineViewer == null) {
                install(KeyEvent.VK_R, this::onUltimateP1);
                install(KeyEvent.VK_ENTER, this::onEnter);
            }
            subscription = client.subscribe(this::onSnapshot);
        } catch (RuntimeException | Error failure) {
            close();
            try { client.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** 온라인. 이 PC의 키는 BattleKeyMap.online()이고 필살은 R. 호스트는 P1, 클라이언트는 P2 */
    public static RaidScreenController online(JRootPane root, RaidClient client, RaidPanel panel, PlayerId viewer,
                                              ControlStyle style, Runnable onMenu, Runnable onRetry) {
        return new RaidScreenController(root, client, panel, new KeyMap(), false, style, onMenu, onRetry, viewer);
    }

    /** 테스트·결과 버튼이 고른 카드를 본다 */
    public int cardSelection() {
        return cardIndex;
    }

    public void focusLost() {
        if (closed) return;
        if (soloRouter != null) soloRouter.clearPressedKeys();
        if (battleRouter != null) battleRouter.clearPressedKeys();
        if (current != null && current.phase() == RaidPhase.PLAYING && !confirming) sendPause();
    }

    /** 결과 화면에서 고른 버튼을 누른다 */
    public void activateResult() {
        if (current != null && current.phase() == RaidPhase.FINISHED) panel.activateResult();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (soloRouter != null) soloRouter.close();
        if (battleRouter != null) battleRouter.close();
        uninstallExtra();
        if (subscription != null) subscription.close();
        client.close();
    }

    private void onSnapshot(RaidSnapshot snapshot) {
        if (closed) return;
        RaidSnapshot previous = current;
        current = snapshot;
        boolean newOffer = snapshot.phase() == RaidPhase.CARD_PICK && (previous == null
                || previous.phase() != RaidPhase.CARD_PICK
                || previous.offer().chooser() != snapshot.offer().chooser()
                || !previous.offer().cards().equals(snapshot.offer().cards()));
        if (newOffer) {
            cardIndex = 1;
            cardHeld.clear();
        }
        panel.setCardSelection(cardIndex);
        panel.setSnapshot(snapshot);
        if (snapshot.phase() == RaidPhase.FINISHED) {
            closeGameplayKeys();
            installResultKeys();
        }
    }

    private boolean accepts(GameAction action) {
        if (closed || current == null || current.phase() == RaidPhase.FINISHED) return false;
        if (confirming) {
            return action == GameAction.MOVE_LEFT || action == GameAction.MOVE_RIGHT
                    || action == GameAction.QUIT || action == GameAction.HARD_DROP;
        }
        if (current.phase() == RaidPhase.CARD_PICK) {
            return action == GameAction.MOVE_LEFT || action == GameAction.MOVE_RIGHT
                    || action == GameAction.HARD_DROP || action == GameAction.QUIT;
        }
        if (action == GameAction.PAUSE || action == GameAction.QUIT) return true;
        return current.phase() == RaidPhase.PLAYING;
    }

    private void onCommand(PlayerCommand command) {
        if (closed || current == null) return;
        if (confirming) {
            handleConfirm(command);
            return;
        }
        if (command.action() == GameAction.QUIT && command.pressed()) {
            beginQuit();
            return;
        }
        if (current.phase() == RaidPhase.CARD_PICK) {
            handleCard(command);
            return;
        }
        client.send(new PlayerCommand(command.playerId(), command.action(), command.pressed(), sequence++));
    }

    private void handleCard(PlayerCommand command) {
        if (current.offer() == null || command.playerId().ordinal() != current.offer().chooser()) return;
        if (!command.pressed()) {
            cardHeld.remove(command.action());
            return;
        }
        if (!cardHeld.add(command.action())) return;
        int next = switch (command.action()) {
            case MOVE_LEFT -> cardIndex - 1;
            case MOVE_RIGHT -> cardIndex + 1;
            case HARD_DROP -> {
                client.pickCard(command.playerId(), cardIndex);
                yield cardIndex;
            }
            default -> cardIndex;
        };
        cardIndex = Math.max(0, Math.min(2, next));
        panel.setCardSelection(cardIndex);
    }

    private void handleConfirm(PlayerCommand command) {
        if (!command.pressed()) return;
        switch (command.action()) {
            case MOVE_LEFT, MOVE_RIGHT -> {
                confirmYes = !confirmYes;
                panel.setConfirm(true, confirmYes);
            }
            case HARD_DROP -> activateConfirm();
            case QUIT -> cancelConfirm();
            default -> {
            }
        }
    }

    private void onUltimateP1() {
        if (closed || confirming || current == null || current.phase() != RaidPhase.PLAYING) return;
        client.ultimate(PlayerId.ONE);
    }

    private void onEnter() {
        if (closed) return;
        if (confirming) {
            activateConfirm();
            return;
        }
        if (current != null && current.phase() == RaidPhase.FINISHED) {
            panel.activateResult();
            return;
        }
        if (!solo && current != null && current.phase() == RaidPhase.PLAYING) client.ultimate(PlayerId.TWO);
    }

    private void beginQuit() {
        confirming = true;
        confirmYes = false;
        panel.setConfirm(true, false);
        if (battleRouter != null) battleRouter.clearGameplayKeys();
        if (soloRouter != null) soloRouter.clearPressedKeys();
        if (current != null && current.phase() == RaidPhase.PLAYING) {
            pausedForConfirm = true;
            sendPause();
        }
    }

    private void activateConfirm() {
        if (confirmYes) goMenu();
        else cancelConfirm();
    }

    private void cancelConfirm() {
        confirming = false;
        panel.setConfirm(false, false);
        if (pausedForConfirm) {
            pausedForConfirm = false;
            sendPause();
        }
    }

    private void sendPause() {
        client.send(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, true, sequence++));
        client.send(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, false, sequence++));
    }

    private void goMenu() {
        close();
        onMenu.run();
    }

    private void goRetry() {
        close();
        onRetry.run();
    }

    private void closeGameplayKeys() {
        if (soloRouter != null) {
            soloRouter.close();
            soloRouter = null;
        }
        if (battleRouter != null) {
            battleRouter.close();
            battleRouter = null;
        }
    }

    private void installResultKeys() {
        install(KeyEvent.VK_LEFT, () -> panel.moveResult(-1));
        install(KeyEvent.VK_RIGHT, () -> panel.moveResult(1));
        install(KeyEvent.VK_UP, () -> panel.moveResult(-1));
        install(KeyEvent.VK_DOWN, () -> panel.moveResult(1));
    }

    private final java.util.Map<KeyStroke, Object> extra = new java.util.LinkedHashMap<>();

    private void install(int code, Runnable command) {
        KeyStroke stroke = KeyStroke.getKeyStroke(code, 0, false);
        Object name = new Object();
        extra.put(stroke, name);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!closed) command.run();
            }
        });
    }

    private void uninstallExtra() {
        var map = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        for (var entry : extra.entrySet()) {
            if (map.get(entry.getKey()) == entry.getValue()) map.remove(entry.getKey());
            root.getActionMap().remove(entry.getValue());
        }
        extra.clear();
    }

    public static String raceGuide() {
        BattleKeyMap battle = new BattleKeyMap();
        return "P1 " + String.join("  ", battle.playerLines(PlayerId.ONE)) + "  R 필살\n"
                + "P2 " + String.join("  ", battle.playerLines(PlayerId.TWO)) + "  Enter 필살\n"
                + battle.sharedLine();
    }

    private static String onlineGuide() {
        return BattleKeyMap.online().guide() + "\nR 필살";
    }

    static String guide(boolean solo, KeyMap keys) {
        if (!solo) return raceGuide() + "\n카드는 좌우 후 하드 드롭";
        KeyMap map = keys == null ? new KeyMap() : keys;
        return Messages.keyName(map, GameAction.MOVE_LEFT) + "/" + Messages.keyName(map, GameAction.MOVE_RIGHT) + " 이동"
                + "  ·  " + Messages.keyName(map, GameAction.ROTATE) + "/" + Messages.keyName(map, GameAction.ROTATE_CCW) + " 회전"
                + "  ·  " + Messages.keyName(map, GameAction.HARD_DROP) + " 하드"
                + "  ·  " + Messages.keyName(map, GameAction.HOLD) + " 홀드"
                + "  ·  R 필살"
                + "  ·  " + Messages.keyName(map, GameAction.PAUSE) + " 일시정지"
                + "  ·  " + Messages.keyName(map, GameAction.QUIT) + " 나가기";
    }

}
