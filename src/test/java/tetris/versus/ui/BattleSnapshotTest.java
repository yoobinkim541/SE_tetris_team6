package tetris.versus.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import tetris.ui.versus.*;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;

class BattleSnapshotTest {
    @Test void snapshotCopiesArraysAndRejectsInvalidBoards() {
        int[][] board = new int[20][10]; board[19][0] = 9;
        var player = new MatchSnapshot.PlayerView(board, new int[][]{{1,1,1,1}}, 120, List.of(new int[10]));
        board[19][0] = 0;
        assertEquals(9, player.cells()[19][0]);
        player.cells()[19][0] = 0;
        assertEquals(9, player.cells()[19][0]);
        player.pendingRows().getFirst()[0] = 9;
        assertEquals(0, player.pendingRows().getFirst()[0]);
        player.next()[0][0] = 0;
        assertEquals(1,player.next()[0][0]);
        assertThrows(IllegalArgumentException.class, () -> new MatchSnapshot.PlayerView(new int[19][10], new int[0][], 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MatchSnapshot.PlayerView(new int[20][10], new int[0][], 0, java.util.Collections.nCopies(11, new int[10])));
        board[0][0] = 10;
        assertThrows(IllegalArgumentException.class, () -> new MatchSnapshot.PlayerView(board, new int[0][], 0, List.of()));
    }
    @Test void fakeSubscriptionStopsAndCloseIsIdempotent() {
        var fake = new FakeBattleClient();
        var received = new java.util.ArrayList<MatchSnapshot>();
        var subscription = fake.subscribe(received::add);
        assertEquals(1, received.size());
        subscription.close(); fake.publish(FakeBattleClient.sample());
        assertEquals(1,received.size());
        fake.close(); fake.close();
        assertThrows(IllegalStateException.class, () -> fake.subscribe(received::add));
    }
    @Test void fakeRecordsCommandsWithoutRunningAnEngine() {
        var fake=new FakeBattleClient();
        var command=new PlayerCommand(PlayerId.TWO,tetris.feature.setting.GameAction.ROTATE,true,0);
        fake.send(command);
        assertEquals(List.of(command),fake.commands());
        assertThrows(UnsupportedOperationException.class, () -> fake.commands().clear());
        fake.close(); assertThrows(IllegalStateException.class, () -> fake.send(command));
    }
}
