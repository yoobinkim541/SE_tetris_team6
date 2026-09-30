package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.IBlock;
import tetris.component.block.JBlock;
import tetris.component.block.LBlock;
import tetris.component.block.OBlock;
import tetris.component.block.SBlock;
import tetris.component.block.TBlock;
import tetris.component.block.ZBlock;
import tetris.debug.AsciiBoard;
import tetris.feature.data.Board;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.rule.SpawnPolicy;

/** SPN-1, SPN-4, ROT-2~4, ROT-6 (Spawn 앵커, 회전 4회 원복, O 불변, 벽 근처 회전 실패, Spawn 충돌) */
public class SpawnAndRotationTest {
    private final SpawnPolicy spawn = new SpawnPolicy();

    private static Block[] allBlocks() {
        return new Block[] {new IBlock(), new JBlock(), new LBlock(), new OBlock(), new SBlock(), new TBlock(), new ZBlock()};
    }

    //#region SpawnPolicy
    @Test
    void Spawn_열은_I_J_L_S_T_Z가_3_O가_4() { // SPN-1
        for (Block block : allBlocks()) {
            int expected = block instanceof OBlock ? 4 : 3;
            assertEquals(expected, spawn.column(block));
        }
    }

    @Test
    void Spawn_행은_I만_마이너스1_나머지는_0() { // SPN-1 (가장 높은 칸 기준)
        for (Block block : allBlocks()) {
            int expected = block instanceof IBlock ? -1 : 0;
            assertEquals(expected, spawn.row(block));
        }
    }

    @Test
    void 빈_보드에서는_7종_모두_Spawn_위치가_유효() {
        Board board = new Board();
        for (Block block : allBlocks()) {
            assertTrue(board.canPlace(block, spawn.row(block), spawn.column(block)));
        }
    }

    @Test
    void Spawn한_I는_맨_윗줄에_보이고_바로_회전할_수_있다() {
        Board board = new Board();
        Block i = new IBlock();
        i.setPosition(spawn.row(i), spawn.column(i));
        assertTrue(board.canPlace(i.rotateRight(), i.getRow(), i.getCol()));

        board.TryPlaceBlock(i);
        int[][] cells = board.copyCells();
        for (int col = 3; col <= 6; col++) assertTrue(cells[0][col] != Board.EMPTY);
    }

    @Test
    void Spawn_칸이_막혀_있으면_유효하지_않다() { // SPN-4
        Board board = new Board();
        Block cell = new Block(new int[][] {{1}}) {};
        cell.setPosition(0, 4);
        board.TryPlaceBlock(cell);

        Block t = new TBlock();
        assertFalse(board.canPlace(t, spawn.row(t), spawn.column(t)));
    }
    //#endregion

    //#region GameSession Spawn
    private static BlockGenerator sequence(Block... blocks) {
        Iterator<Block> it = Arrays.asList(blocks).iterator();
        return it::next;
    }

    private static final class RecordingClock implements GameClock {
        final List<Integer> starts = new ArrayList<>();
        int stops;

        @Override public void start(int intervalMillis) { starts.add(intervalMillis); }
        @Override public void stop() { stops++; }
    }

    private static final class RecordingListener implements GameListener {
        final List<GameSnapshot> changed = new ArrayList<>();
        final List<GameSnapshot> gameOvers = new ArrayList<>();

        @Override public void onChanged(GameSnapshot snapshot) { changed.add(snapshot); }
        @Override public void onGameOver(GameSnapshot snapshot) { gameOvers.add(snapshot); }
    }

    @Test
    void start하면_첫_블록이_Spawn되고_PLAYING() { // PIPE-4
        RecordingClock clock = new RecordingClock();
        RecordingListener listener = new RecordingListener();
        GameSession session = new GameSession(sequence(new IBlock(), new OBlock(), new TBlock()), clock, listener);

        session.start();

        GameSnapshot s = session.snapshot();
        assertEquals(GameState.PLAYING, session.getState());
        assertEquals("::::::::::", AsciiBoard.row(s, -1));
        assertEquals("...iiii...", AsciiBoard.row(s, 0));
        assertEquals(BlockType.O, s.nextBlock().getType());
        assertEquals(List.of(1000), clock.starts);   // S4, Level 1
        assertEquals(1, listener.changed.size());
        assertTrue(listener.gameOvers.isEmpty());
    }

    @Test
    void Spawn한_O는_보이는_맨_윗줄_가운데() {
        GameSession session = new GameSession(sequence(new OBlock(), new IBlock()), new RecordingClock(), new RecordingListener());
        session.start();

        GameSnapshot s = session.snapshot();
        assertEquals("::::::::::", AsciiBoard.row(s, -1));
        assertEquals("....oo....", AsciiBoard.row(s, 0));
        assertEquals("....oo....", AsciiBoard.row(s, 1));
        assertEquals("..........", AsciiBoard.row(s, 2));
    }

    @Test
    void 스냅샷을_바꿔도_게임에_영향이_없다() {
        GameSession session = new GameSession(sequence(new TBlock(), new OBlock()), new RecordingClock(), new RecordingListener());
        session.start();

        GameSnapshot s = session.snapshot();
        s.currentBlock().setPosition(10, 0);
        s.currentBlock().getShape()[0][0] = 1;
        s.cells()[19][0] = 7;

        GameSnapshot again = session.snapshot();
        assertEquals("....t.....", AsciiBoard.row(again, 0));
        assertEquals("...ttt....", AsciiBoard.row(again, 1));
        assertEquals("..........", AsciiBoard.row(again, 19));
    }

    @Test
    void 다시_start하면_생성기의_다음_블록부터_새_게임() {
        GameSession session = new GameSession(sequence(new IBlock(), new OBlock(), new TBlock(), new SBlock()), new RecordingClock(), new RecordingListener());
        session.start();
        session.start();

        GameSnapshot s = session.snapshot();
        assertEquals("....t.....", AsciiBoard.row(s, 0));
        assertEquals(BlockType.S, s.nextBlock().getType());
        assertEquals(1, s.level());
    }
    //#endregion
}
