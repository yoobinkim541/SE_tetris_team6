package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static tetris.feature.GameTestSupport.sequence;
import static tetris.feature.GameTestSupport.startedSession;

import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.component.block.WeightBlock;
import tetris.debug.AsciiBoard;
import tetris.feature.GameTestSupport.RecordingClock;
import tetris.feature.GameTestSupport.RecordingListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;

/** ITW-3~8: 착지 전에는 일반 블록처럼, 첫 하강 실패부터는 분쇄하며 내려가 바닥에 고정된다 */
class WeightCrushTest {

    private static void tick(GameSession session, int times) {
        for (int i = 0; i < times; i++) session.tick();
    }

    @Test
    void 착지_전에는_좌우로_움직일_수_있다() { // ITW-3
        GameSession session = startedSession(new WeightBlock(), new OBlock());
        session.moveLeft();
        assertEquals("...ww.....", AsciiBoard.row(session.snapshot(), 0));
        assertEquals("..wwww....", AsciiBoard.row(session.snapshot(), 1));
    }

    @Test
    void 빈_보드에서는_바닥까지_내려가_값_8로_고정되고_점수는_0이다() { // ITW-5, ITW-7
        GameSession session = startedSession(new WeightBlock(), new OBlock());
        tick(session, 18);   // 앵커 row 0 -> 18, 맨 아랫줄이 row 19
        GameSnapshot s = session.snapshot();
        assertEquals("....ww....", AsciiBoard.row(s, 18));
        assertEquals("...wwww...", AsciiBoard.row(s, 19));
        assertEquals(0, s.score());

        session.tick();      // 바닥에서 하강 실패 -> 고정
        s = session.snapshot();
        assertEquals("....WW....", AsciiBoard.row(s, 18));
        assertEquals("...WWWW...", AsciiBoard.row(s, 19));
        assertEquals(0, s.score());
        assertNotNull(s.currentBlock());                          // 다음 블록 Spawn
    }

    @Test
    void 장애물에_닿으면_분쇄_모드가_되어_좌우_이동이_막히고_겹치는_칸을_지운다() { // ITW-4
        GameSession session = startedSession(new IBlock(), new WeightBlock(), new OBlock());
        tick(session, 20);   // I를 중앙(col 3..6)에 두고 19행에 고정
        assertEquals("...IIII...", AsciiBoard.row(session.snapshot(), 19));
        long scoreAfterI = session.snapshot().score();

        tick(session, 17);   // 무게추 앵커 row 17: 맨 아랫줄이 18행, I 바로 위
        assertEquals("...wwww...", AsciiBoard.row(session.snapshot(), 18));

        session.tick();      // 첫 하강 실패 순간에도 바로 한 칸 내려가며 I를 부순다
        GameSnapshot s = session.snapshot();
        assertEquals("...wwww...", AsciiBoard.row(s, 19));
        assertEquals("....ww....", AsciiBoard.row(s, 18));

        session.moveLeft();
        session.moveRight();
        assertEquals("...wwww...", AsciiBoard.row(session.snapshot(), 19)); // 좌우 이동 불가
        assertEquals(scoreAfterI, session.snapshot().score());              // 무게추는 점수 0
    }

    @Test
    void 분쇄가_끝나고_바닥에서_하강에_실패하면_고정된다() { // ITW-5
        GameSession session = startedSession(new IBlock(), new WeightBlock(), new OBlock());
        tick(session, 20);
        tick(session, 18);   // 앵커 row 17에서 분쇄 하강 (앵커 18)
        session.tick();      // 바닥이라 더 못 내려감 -> 고정
        GameSnapshot s = session.snapshot();
        assertEquals("....WW....", AsciiBoard.row(s, 18));
        assertEquals("...WWWW...", AsciiBoard.row(s, 19));           // I 자리를 W가 차지한다
    }

    @Test
    void Soft_Drop도_무게추에는_점수가_없다() { // ITW-7
        GameSession session = startedSession(new WeightBlock(), new OBlock());
        session.moveDown();
        session.moveDown();
        assertEquals(0, session.snapshot().score());
    }

    @Test
    void Hard_Drop은_지나는_칸을_모두_지우고_바닥에_고정한다_점수는_0() { // ITW-6
        GameSession session = startedSession(new IBlock(), new WeightBlock(), new OBlock());
        tick(session, 20);   // I 고정
        long before = session.snapshot().score();

        session.hardDrop();

        GameSnapshot s = session.snapshot();
        assertEquals("....WW....", AsciiBoard.row(s, 18));
        assertEquals("...WWWW...", AsciiBoard.row(s, 19));
        assertEquals(before, s.score());
        assertEquals(GameState.PLAYING, session.getState());
    }

    @Test
    void 고정된_무게추가_한_줄을_완성하면_평소처럼_삭제하고_점수를_받는다() { // ITW-5
        GameSession session = startedSession(new IBlock(), new IBlock(), new WeightBlock(), new OBlock());
        for (int i = 0; i < 3; i++) session.moveLeft();    // 첫 I: col 0..3
        tick(session, 20);
        for (int i = 0; i < 3; i++) session.moveRight();   // 둘째 I: col 6..9
        tick(session, 20);
        long before = session.snapshot().score();
        assertEquals("IIII..IIII", AsciiBoard.row(session.snapshot(), 19));

        session.hardDrop();   // 무게추가 col 3..6을 부수고 19행이 꽉 참 -> CLEARING

        assertEquals(GameState.CLEARING, session.getState());
        assertEquals("IIIWWWWIII", AsciiBoard.row(session.snapshot(), 19));
        session.tick();
        GameSnapshot s = session.snapshot();
        assertEquals(before + 100, s.score());                // 무게추 자체 점수는 없고 줄 삭제만
        assertEquals(1, s.lines());
        assertEquals("....WW....", AsciiBoard.row(s, 19));    // 위의 WW가 내려옴
    }

    @Test
    void 무게추_Spawn_자리가_막히면_Game_Over() { // ITW-8
        Block[] blocks = new Block[11];
        for (int i = 0; i < 10; i++) blocks[i] = new OBlock();
        blocks[10] = new WeightBlock();
        GameSession session = new GameSession(sequence(blocks), new RecordingClock(), new RecordingListener());
        session.start();
        for (int k = 0; k < 10; k++) tick(session, 19 - 2 * k);
        assertEquals(GameState.GAME_OVER, session.getState());
    }
}
