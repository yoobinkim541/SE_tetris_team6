package tetris.versus.ui;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tetris.ui.versus.*;
import tetris.feature.versus.api.*;

class BattleScreenControllerTest {
    @Test void confirmedRealForfeitShowsResultInsteadOfNavigatingAway() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root = new JRootPane(); var client = new LocalBattleClient(MatchConfig.of(BattleMode.CLASSIC));
            var result = new AtomicInteger(); var exit = new AtomicInteger();
            var controller = new BattleScreenController(root, client, new BattleKeyMap(),
                new BattlePanel(false), state -> {
                    assertEquals(MatchResult.EndReason.FORFEIT, state.result().reason()); result.incrementAndGet();
                }, () -> true, exit::incrementAndGet);
            key(root, KeyEvent.VK_Q, false);
            assertEquals(1, result.get()); assertEquals(0, exit.get()); controller.close();
        });
    }

    @Test void quitDialogPausesAfterResumeWhilePhysicalPauseIsStillHeld() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root=new JRootPane();var client=new LocalBattleClient(MatchConfig.of(BattleMode.CLASSIC));
            var state=new java.util.concurrent.atomic.AtomicReference<MatchSnapshot>();client.subscribe(state::set);
            var controller=new BattleScreenController(root,client,new BattleKeyMap(),new BattlePanel(false),s->{},()->{
                assertEquals(MatchPhase.PAUSED,state.get().phase(),"modal confirmation must freeze both sessions");return false;
            },()->{});
            try {
                key(root,KeyEvent.VK_P,false);key(root,KeyEvent.VK_P,true);key(root,KeyEvent.VK_P,false);
                assertEquals(MatchPhase.PLAYING,state.get().phase());key(root,KeyEvent.VK_Q,false);
                assertEquals(MatchPhase.PLAYING,state.get().phase());
                controller.focusLost();assertEquals(MatchPhase.PAUSED,state.get().phase());
            } finally {controller.close();}
        });
    }
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

    /** Review I-3: 온라인 기권은 모달 없이 Q 두 번. 첫 Q는 경기를 멈추지 않고 안내만 띄운다 */
    @Test void onlineForfeitNeedsASecondQuitWithoutAModalOrPause() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root = new JRootPane(); var client = new LocalBattleClient(MatchConfig.of(BattleMode.CLASSIC));
            var state = new java.util.concurrent.atomic.AtomicReference<MatchSnapshot>(); client.subscribe(state::set);
            var panel = new BattlePanel(false);
            var results = new AtomicInteger();
            var controller = BattleScreenController.online(root, client, BattleKeyMap.online(), panel,
                s -> results.incrementAndGet());
            try {
                key(root, KeyEvent.VK_Q, false); key(root, KeyEvent.VK_Q, true);
                assertEquals(MatchPhase.PLAYING, state.get().phase(), "the first Q neither pauses nor forfeits");
                assertFalse(panel.notice().isEmpty(), "the first Q explains how to confirm");
                assertEquals(0, results.get());
                key(root, KeyEvent.VK_Q, false);
                assertEquals(MatchPhase.FINISHED, state.get().phase());
                assertEquals(MatchResult.win(PlayerId.TWO, MatchResult.EndReason.FORFEIT), state.get().result());
                assertEquals(1, results.get());
            } finally { controller.close(); }
        });
    }
    @Test void onlineForfeitArmingIsCancelledByAnyOtherKey() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root = new JRootPane(); var client = new LocalBattleClient(MatchConfig.of(BattleMode.CLASSIC));
            var state = new java.util.concurrent.atomic.AtomicReference<MatchSnapshot>(); client.subscribe(state::set);
            var panel = new BattlePanel(false);
            var controller = BattleScreenController.online(root, client, BattleKeyMap.online(), panel, s -> { });
            try {
                key(root, KeyEvent.VK_Q, false); key(root, KeyEvent.VK_Q, true);
                key(root, KeyEvent.VK_LEFT, false); key(root, KeyEvent.VK_LEFT, true);
                assertTrue(panel.notice().isEmpty());
                key(root, KeyEvent.VK_Q, false);
                assertEquals(MatchPhase.PLAYING, state.get().phase(), "a Q after another key starts over");
            } finally { controller.close(); }
        });
    }
}
