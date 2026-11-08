package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import tetris.feature.versus.api.*;
import tetris.feature.setting.GameAction;
import tetris.ui.versus.LocalBattleClient;

class LocalBattleClientTest {
    private static int column(MatchSnapshot.PlayerView view) {
        int first=10;
        for(int[] row:view.cells())for(int c=0;c<row.length;c++)if(row[c]!=0)first=Math.min(first,c);
        return first;
    }
    @Test void unsubscribePreventsLaterCallbacksWhileOtherSubscribersStayActive() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try(var client=new LocalBattleClient(MatchConfig.of(BattleMode.CLASSIC))) {
                var removed=new AtomicInteger();var active=new AtomicInteger();
                var subscription=client.subscribe(s->removed.incrementAndGet());
                client.subscribe(s->active.incrementAndGet());
                subscription.close();subscription.close();
                client.send(new PlayerCommand(PlayerId.ONE,GameAction.PAUSE,true,0));
                assertEquals(1,removed.get());assertTrue(active.get()>1);
            }
        });
    }
    @Test void realClientPublishesMovementPauseResultAndClosesSubscriptions() throws Exception {
        var reference=new AtomicReference<LocalBattleClient>();
        var latest=new AtomicReference<MatchSnapshot>();var movement=new CountDownLatch(1);
        var callbacks=new AtomicInteger();
        int[] initial={-1,-1};
        try {
            SwingUtilities.invokeAndWait(()->{
                var client=new LocalBattleClient(MatchConfig.of(BattleMode.CLASSIC));reference.set(client);
                client.subscribe(s->{
                    latest.set(s);callbacks.incrementAndGet();
                    if(initial[0]<0){initial[0]=column(s.one());initial[1]=column(s.two());}
                    else if(column(s.one())==initial[0]-1&&column(s.two())==initial[1]+1)movement.countDown();
                });
                assertEquals(MatchPhase.PLAYING,latest.get().phase());
                client.send(new PlayerCommand(PlayerId.ONE,GameAction.MOVE_LEFT,true,0));
                client.send(new PlayerCommand(PlayerId.TWO,GameAction.MOVE_RIGHT,true,1));
            });
            assertTrue(movement.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->{
                assertEquals(initial[1]+1,column(latest.get().two()));
                var client=reference.get();client.send(new PlayerCommand(PlayerId.ONE,GameAction.PAUSE,true,2));
                assertEquals(MatchPhase.PAUSED,latest.get().phase());
                client.send(new PlayerCommand(PlayerId.TWO,GameAction.QUIT,true,3));
                assertEquals(MatchPhase.FINISHED,latest.get().phase());assertEquals(PlayerId.ONE,latest.get().result().winner());
                assertEquals(MatchResult.EndReason.FORFEIT,latest.get().result().reason());
                int count=callbacks.get();client.close();client.close();
                client.send(new PlayerCommand(PlayerId.ONE,GameAction.MOVE_LEFT,true,4));assertEquals(count,callbacks.get());
                assertThrows(IllegalStateException.class,()->client.subscribe(s->{}));
            });
        } finally {SwingUtilities.invokeAndWait(()->{if(reference.get()!=null)reference.get().close();});}
    }
}
