package tetris.versus.ui;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tetris.ui.versus.*;
import tetris.feature.versus.api.*;

class BattleScreenControllerTest {
    @Test void synchronousPauseSnapshotDoesNotClearSharedKeyLatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root = new JRootPane(); var client = new PreviewBattleClient();
            var state = new java.util.concurrent.atomic.AtomicReference<MatchSnapshot>();
            client.subscribe(state::set);
            var controller = new BattleScreenController(root, client, new BattleKeyMap(),
                new BattlePanel(false), s -> {}, () -> false, () -> {});
            key(root, KeyEvent.VK_P, false); key(root, KeyEvent.VK_P, false);
            assertEquals(MatchPhase.PAUSED, state.get().phase());
            key(root, KeyEvent.VK_P, true); key(root, KeyEvent.VK_P, false);
            assertEquals(MatchPhase.PLAYING, state.get().phase());
            controller.close();
        });
    }
    private void key(JRootPane root, int code, boolean release) {
        Object name = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .get(KeyStroke.getKeyStroke(code, 0, release));
        if (name != null) root.getActionMap().get(name).actionPerformed(null);
    }
    @Test void routesBothPlayersReleasesBeforePauseAndClosesOnResult() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root = new JRootPane(); var client = new FakeBattleClient();
            var results = new AtomicInteger();
            var controller = new BattleScreenController(root, client, new BattleKeyMap(),
                new BattlePanel(false), s -> results.incrementAndGet(), () -> true, () -> {});
            key(root, KeyEvent.VK_LEFT, false); key(root, KeyEvent.VK_A, false);
            assertEquals(2, client.commands().size());
            key(root, KeyEvent.VK_P, false);
            assertEquals(2, client.commands().stream().filter(c -> !c.pressed()).count());
            assertEquals(1, client.commands().stream().filter(c -> c.pressed() && c.action() == tetris.feature.setting.GameAction.PAUSE).count());
            key(root, KeyEvent.VK_P, true);
            controller.focusLost();
            var s = FakeBattleClient.sample();
            client.publish(new MatchSnapshot(s.matchId(), 1, s.one(), s.two(), -1,
                MatchPhase.FINISHED, new MatchResult(PlayerId.ONE, MatchResult.EndReason.TOPPED_OUT)));
            assertEquals(1, results.get());
            int count = client.commands().size(); key(root, KeyEvent.VK_A, false);
            assertEquals(count, client.commands().size());
            controller.close(); controller.close();
            assertThrows(IllegalStateException.class, () -> client.send(new PlayerCommand(PlayerId.ONE,
                tetris.feature.setting.GameAction.MOVE_LEFT, true, 100)));
        });
    }
    @Test void cancelledQuitRestoresInputAndConfirmedQuitCleansBindings() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root = new JRootPane(); var client = new FakeBattleClient();
            var exits = new AtomicInteger(); var answer = new java.util.concurrent.atomic.AtomicBoolean();
            var controller = new BattleScreenController(root, client, new BattleKeyMap(),
                new BattlePanel(false), s -> {}, answer::get, exits::incrementAndGet);
            key(root, KeyEvent.VK_Q, false); key(root, KeyEvent.VK_Q, true);
            assertEquals(0, exits.get());
            key(root, KeyEvent.VK_A, false); key(root, KeyEvent.VK_A, true);
            assertTrue(client.commands().stream().anyMatch(c -> c.playerId() == PlayerId.TWO));
            answer.set(true); key(root, KeyEvent.VK_Q, false);
            assertEquals(1, exits.get()); controller.close();
            assertNull(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_A,0)));
        });
    }
}
