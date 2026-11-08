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
    @Test void realClientPublishesMovementPauseResultAndClosesSubscriptions() throws Exception {
        var reference=new AtomicReference<LocalBattleClient>();
        var latest=new AtomicReference<MatchSnapshot>();var movement=new CountDownLatch(1);
        var callbacks=new AtomicInteger();
        try {
            SwingUtilities.invokeAndWait(()->{
                var client=new LocalBattleClient(MatchConfig.of(BattleMode.CLASSIC));reference.set(client);
                client.subscribe(s->{latest.set(s);callbacks.incrementAndGet();if(s.one().score()>0)movement.countDown();});
                assertEquals(MatchPhase.PLAYING,latest.get().phase());
                client.send(new PlayerCommand(PlayerId.ONE,GameAction.SOFT_DROP,true,0));
                client.send(new PlayerCommand(PlayerId.TWO,GameAction.SOFT_DROP,true,1));
            });
            assertTrue(movement.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->{
                assertTrue(latest.get().two().score()>0);
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
