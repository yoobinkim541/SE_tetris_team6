package tetris.versus.ui;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.*;
import tetris.ui.versus.*;

class BattleCloseoutTest {
    @Test void invalidLegacyConfirmationAlsoClosesTheSuppliedClient() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root=new JRootPane();var client=new FakeBattleClient();
            assertThrows(NullPointerException.class,()->new BattleScreenController(root,client,new BattleKeyMap(),
                new BattlePanel(false),s->{},(java.util.function.BooleanSupplier)null,()->{}));
            assertThrows(IllegalStateException.class,()->client.send(new PlayerCommand(PlayerId.ONE,GameAction.MOVE_LEFT,true,0)));
        });
    }
    @Test void quitChooserCanForfeitEitherPlayerOrCancel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (PlayerId forfeiting : new PlayerId[]{PlayerId.ONE,PlayerId.TWO,null}) {
                var root=new JRootPane(); var client=new LocalBattleClient(MatchConfig.of(BattleMode.CLASSIC));
                var result=new java.util.concurrent.atomic.AtomicReference<MatchSnapshot>();
                java.util.function.Supplier<PlayerId> choose=()->forfeiting;
                try(var controller=new BattleScreenController(root,client,new BattleKeyMap(),new BattlePanel(false),
                        result::set,choose,()->fail("real match must display its result"))) {
                    quit(root);
                    if(forfeiting==null) assertNull(result.get());
                    else assertEquals(forfeiting.opponent(),result.get().result().winner());
                }
            }
        });
    }
    private void quit(JRootPane root) {
        var name = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0));
        root.getActionMap().get(name).actionPerformed(null);
    }
    @Test void finishingDuringAutomaticPauseDoesNotOpenQuitDialog() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root = new JRootPane(); var dialogs = new AtomicInteger(); var results = new AtomicInteger();
            BattleClient client = new BattleClient() {
                Consumer<MatchSnapshot> listener;
                boolean finished;
                public Subscription subscribe(Consumer<MatchSnapshot> listener) {
                    this.listener=listener; listener.accept(FakeBattleClient.sample()); return () -> {};
                }
                public void send(PlayerCommand c) {
                    if(c.pressed() && c.action()==GameAction.PAUSE && !finished) {
                        finished=true; var s=FakeBattleClient.sample();
                        listener.accept(new MatchSnapshot(s.matchId(),1,s.one(),s.two(),-1,MatchPhase.FINISHED,
                            MatchResult.win(PlayerId.TWO,MatchResult.EndReason.TOPPED_OUT)));
                    }
                }
                public void close() {}
            };
            try(var controller = new BattleScreenController(root,client,new BattleKeyMap(),new BattlePanel(false),
                    s -> results.incrementAndGet(), () -> { dialogs.incrementAndGet(); return true; }, () -> {})) {
                quit(root); assertEquals(1,results.get()); assertEquals(0,dialogs.get());
            }
        });
    }
    @Test void failedSubscriptionClosesClientAndRestoresRootBindings() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root=new JRootPane(); var closed=new AtomicBoolean();
            BattleClient client=new BattleClient() {
                public void send(PlayerCommand c) {}
                public Subscription subscribe(Consumer<MatchSnapshot> listener) { throw new IllegalStateException("subscribe failed"); }
                public void close() { closed.set(true); }
            };
            assertThrows(IllegalStateException.class, () -> new BattleScreenController(root,client,new BattleKeyMap(),
                new BattlePanel(false),s -> {},() -> false,() -> {}));
            assertTrue(closed.get());
            assertNull(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_A,0)));
            try(var replacement=new BattleScreenController(root,new FakeBattleClient(),new BattleKeyMap(),
                    new BattlePanel(false),s -> {},() -> false,() -> {})) { }
        });
    }
}
