package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tetris.feature.GameTestSupport.cell;
import static tetris.feature.GameTestSupport.sequence;
import static tetris.feature.GameTestSupport.startedSession;

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
import tetris.feature.GameTestSupport.RecordingClock;
import tetris.feature.GameTestSupport.RecordingListener;
import tetris.feature.data.Board;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
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
            assertEquals(expected, spawn.getSpawnColumn(block));
        }
    }

    @Test
    void Spawn_행은_I만_마이너스1_나머지는_0() { // SPN-1 (가장 높은 칸 기준)
        for (Block block : allBlocks()) {
            int expected = block instanceof IBlock ? -1 : 0;
            assertEquals(expected, spawn.getSpawnRow(block));
        }
    }

    @Test
    void 빈_보드에서는_7종_모두_Spawn_위치가_유효() {
        Board board = new Board();
        for (Block block : allBlocks()) {
            assertTrue(board.canPlace(block, spawn.getSpawnRow(block), spawn.getSpawnColumn(block)));
        }
    }

    @Test
    void Spawn한_I는_맨_윗줄에_보이고_바로_회전할_수_있다() {
        Board board = new Board();
        Block i = new IBlock();
        i.setPosition(spawn.getSpawnRow(i), spawn.getSpawnColumn(i));
        assertTrue(board.canPlace(i.rotateRight(), i.getRow(), i.getCol()));

        board.tryPlaceBlock(i);
        int[][] cells = board.copyCells();
        for (int col = 3; col <= 6; col++) assertTrue(cells[0][col] != Board.EMPTY);
    }

    @Test
    void Spawn_칸이_막혀_있으면_유효하지_않다() { // SPN-4
        Board board = new Board();
        Block blocker = cell();
        blocker.setPosition(0, 4);
        board.tryPlaceBlock(blocker);

        Block t = new TBlock();
        assertFalse(board.canPlace(t, spawn.getSpawnRow(t), spawn.getSpawnColumn(t)));
    }
    //#endregion

    //#region GameSession Spawn
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

    //#region 회전
    private static String row(GameSession session, int row) {
        return AsciiBoard.row(session.snapshot(), row);
    }

    @Test
    void T를_시계방향으로_90도_돌린다() { // ROT-2 (앵커 고정)
        GameSession session = startedSession(new TBlock(), new OBlock());
        session.rotateRight();
        assertEquals("....t.....", row(session, 0));
        assertEquals("....tt....", row(session, 1));
        assertEquals("....t.....", row(session, 2));
        assertEquals(1, session.snapshot().currentBlock().getRotation());
    }

    @Test
    void 네_번_돌리면_원래_모양과_회전_상태로() { // ROT-2
        GameSession session = startedSession(new TBlock(), new OBlock());
        for (int i = 0; i < 4; i++) session.rotateRight();
        assertEquals("....t.....", row(session, 0));
        assertEquals("...ttt....", row(session, 1));
        assertEquals(0, session.snapshot().currentBlock().getRotation());
    }

    @Test
    void O는_돌려도_모양과_위치가_그대로() { // ROT-4
        GameSession session = startedSession(new OBlock(), new TBlock());
        session.rotateRight();
        assertEquals("....oo....", row(session, 0));
        assertEquals("....oo....", row(session, 1));
    }

    @Test
    void Spawn_직후_I는_숨은_줄에_걸쳐_세로로_선다() {
        GameSession session = startedSession(new IBlock(), new OBlock());
        session.rotateRight();
        assertEquals(":::::i::::", row(session, -1));
        assertEquals(".....i....", row(session, 0));
        assertEquals(".....i....", row(session, 2));
        assertEquals("..........", row(session, 3));
    }

    @Test
    void 오른쪽_벽의_세로_I는_회전에_실패하고_그대로() { // ROT-3, ROT-6 (세로 I 앵커 col 7)
        RecordingListener listener = new RecordingListener();
        GameSession session = new GameSession(sequence(new IBlock(), new OBlock()), new RecordingClock(), listener);
        session.start();
        session.rotateRight();
        for (int i = 0; i < 4; i++) session.moveRight();   // 세로 막대 col 5 -> 9
        assertEquals(7, session.snapshot().currentBlock().getCol());
        int notified = listener.changed.size();

        session.rotateRight();

        assertEquals(".........i", row(session, 0));
        assertEquals(1, session.snapshot().currentBlock().getRotation());
        assertEquals(notified, listener.changed.size());
    }
    //#endregion
}
