package tetris.ui.versus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import tetris.feature.versus.api.*;

/** Deterministic UI fixture. Does not simulate gameplay or communicate over a socket. EDT confined. */
public final class FakeBattleClient implements BattleClient {
    private final List<Consumer<MatchSnapshot>> listeners = new ArrayList<>();
    private MatchSnapshot current = sample();
    private boolean closed;
    private final List<PlayerCommand> commands = new ArrayList<>();

    @Override public void send(PlayerCommand command) { ensureOpen(); commands.add(Objects.requireNonNull(command)); }
    public List<PlayerCommand> commands() { return List.copyOf(commands); }

    @Override public Subscription subscribe(Consumer<MatchSnapshot> listener) {
        ensureOpen(); Objects.requireNonNull(listener);
        listeners.add(listener); listener.accept(current);
        return () -> listeners.remove(listener);
    }
    public void publish(MatchSnapshot snapshot) {
        ensureOpen(); current = Objects.requireNonNull(snapshot);
        for (var listener : List.copyOf(listeners)) listener.accept(snapshot);
    }
    private void ensureOpen() { if (closed) throw new IllegalStateException("client is closed"); }
    @Override public void close() { closed = true; listeners.clear(); }

    public static MatchSnapshot sample() {
        int[][] one = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS], two = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        for (int r=16;r<VersusRules.BOARD_ROWS;r++) for (int c=0;c<VersusRules.BOARD_COLS;c++) {
            one[r][c] = c == 4 ? 0 : (r==19 ? VersusRules.ATTACK_CELL : c%8+1);
            two[r][c] = c == 6 ? 0 : (r==19 ? VersusRules.ATTACK_CELL : (c+3)%8+1);
        }
        one[3][4]=one[3][5]=one[4][4]=one[4][5]=4;
        two[4][3]=two[4][4]=two[4][5]=two[3][4]=6;
        List<int[]> rows = new ArrayList<>();
        for (int r=0;r<VersusRules.MAX_PENDING_ROWS;r++) { int[] row = new int[VersusRules.BOARD_COLS]; java.util.Arrays.fill(row,VersusRules.ATTACK_CELL); row[r % VersusRules.BOARD_COLS]=0; rows.add(row); }
        return new MatchSnapshot("fake-s7",0,
            new MatchSnapshot.PlayerView(one,new int[][]{{1,1,1,1}},1200,rows.subList(0,3)),
            new MatchSnapshot.PlayerView(two,new int[][]{{0,6,0},{6,6,6}},2400,rows),90000,MatchPhase.PLAYING,null);
    }
}
