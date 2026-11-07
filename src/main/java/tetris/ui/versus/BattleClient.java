package tetris.ui.versus;

import java.util.function.Consumer;

/** Display port only, pending A's common contract. Callbacks are delivered on the caller thread. */
public interface BattleClient extends AutoCloseable {
    void send(PlayerCommand command);
    Subscription subscribe(Consumer<MatchSnapshot> listener);
    @Override void close();
    interface Subscription extends AutoCloseable { @Override void close(); }
}
