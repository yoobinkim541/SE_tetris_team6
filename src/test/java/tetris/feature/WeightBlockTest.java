package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tetris.component.block.BlockType;
import tetris.component.block.OBlock;
import tetris.component.block.WeightBlock;
import tetris.debug.AsciiBoard;
import tetris.feature.GameTestSupport.RecordingClock;
import tetris.feature.GameTestSupport.RecordingListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;

/** ITW-1, ITW-2: 무게추의 모양, 종류 값, 회전 불가, Spawn 위치 */
class WeightBlockTest {

    @Test
    void 모양은_너비_4_높이_2() {
        int[][] shape = new WeightBlock().getShape();
        assertArrayEquals(new int[] {0, 1, 1, 0}, shape[0]);
        assertArrayEquals(new int[] {1, 1, 1, 1}, shape[1]);
        assertArrayEquals(new int[4], shape[2]);
        assertArrayEquals(new int[4], shape[3]);
    }

    @Test
    void 종류는_W이고_보드_값은_8이다() { // G-2
        WeightBlock weight = new WeightBlock();
        assertEquals(BlockType.W, weight.getType());
        assertEquals(8, weight.getType().cellValue());
        assertEquals('W', weight.getType().letter());
        assertTrue(weight.isWeight());
        assertFalse(new OBlock().isWeight());
    }

    @Test
    void 일반_블록_종류_목록에는_무게추가_없다() {
        assertEquals(7, BlockType.ordinary().length);
        for (BlockType type : BlockType.ordinary()) assertFalse(type == BlockType.W);
    }

    @Test
    void 회전해도_모양과_회전_상태가_바뀌지_않는다() {
        WeightBlock weight = new WeightBlock();
        int[][] before = weight.getShape();
        weight.rotateClockwise();
        assertArrayEquals(before[0], weight.getShape()[0]);
        assertArrayEquals(before[1], weight.getShape()[1]);
        assertEquals(0, weight.getRotation());
        assertArrayEquals(before[1], weight.rotateRight()[1]);
    }

    @Test
    void 복사본도_무게추다() {
        assertTrue(new WeightBlock().copy().isWeight());
    }

    @Test
    void Spawn_위치는_맨_윗줄에_WW_다음_줄에_WWWW() { // ITW-2
        GameSession session = GameTestSupport.startedSession(new WeightBlock(), new OBlock());
        GameSnapshot s = session.snapshot();
        assertEquals(0, s.currentBlock().getRow());
        assertEquals(3, s.currentBlock().getCol());
        assertEquals("....ww....", AsciiBoard.row(s, 0));
        assertEquals("...wwww...", AsciiBoard.row(s, 1));
    }

    @Test
    void 회전_입력은_무시되고_알림도_없다() { // ITW-1
        RecordingListener listener = new RecordingListener();
        GameSession session = new GameSession(GameTestSupport.sequence(new WeightBlock(), new OBlock()), new RecordingClock(), listener);
        session.start();
        int notifications = listener.changed.size();

        session.rotateRight();

        assertEquals(notifications, listener.changed.size());
        assertEquals("....ww....", AsciiBoard.row(session.snapshot(), 0));
        assertEquals("...wwww...", AsciiBoard.row(session.snapshot(), 1));
    }

    @Test
    void 좌우_이동과_하강은_일반_블록처럼_된다() { // ITW-3
        GameSession session = GameTestSupport.startedSession(new WeightBlock(), new OBlock());
        session.moveLeft();
        session.moveDown();
        GameSnapshot s = session.snapshot();
        assertEquals("...ww.....".replace('w', 'w'), AsciiBoard.row(s, 1));
        assertEquals("..wwww....", AsciiBoard.row(s, 2));
    }
}
