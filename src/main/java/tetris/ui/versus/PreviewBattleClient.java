package tetris.ui.versus;

import java.util.function.Consumer;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.*;

/** Explicit UI preview until S8-A2 supplies the real local engine. No gameplay simulation. */
public final class PreviewBattleClient implements BattleClient {
    private final FakeBattleClient fixture = new FakeBattleClient();
    private MatchSnapshot current = FakeBattleClient.sample();
    @Override public void send(PlayerCommand command) {
        fixture.send(command);
        if (command.pressed() && command.action() == GameAction.PAUSE) {
            current = new MatchSnapshot(current.matchId(), current.revision()+1, current.one(), current.two(),
                current.remainingMillis(), current.phase() == MatchPhase.PAUSED ? MatchPhase.PLAYING : MatchPhase.PAUSED, null);
            fixture.publish(current);
        }
    }
    @Override public Subscription subscribe(Consumer<MatchSnapshot> listener) { return fixture.subscribe(listener); }
    @Override public void close() { fixture.close(); }
}
