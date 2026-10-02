package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static tetris.feature.GameTestSupport.startedSession;

import org.junit.jupiter.api.Test;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.debug.AsciiBoard;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;

/** SCR-2, 명세 4.1 Hard Drop: 이동 칸수 x Level 점수, 즉시 Lock 후 다음 블록 Spawn */
public class HardDropTest {
    @Test
    void 빈_보드에서_하드_드롭하면_바닥까지_내려가_Lock되고_칸수_x_Level점() {
        GameSession session = startedSession(new OBlock(), new TBlock());
        session.hardDrop();

        GameSnapshot s = session.snapshot();
        assertEquals("....OO....", AsciiBoard.row(s, 18));
        assertEquals("....OO....", AsciiBoard.row(s, 19));
        assertEquals(18, s.score()); // 18칸 x Level 1
    }

    @Test
    void 하드_드롭_뒤에는_다음_블록이_Spawn되어_있다() {
        GameSession session = startedSession(new OBlock(), new TBlock());
        session.hardDrop();

        GameSnapshot s = session.snapshot();
        assertNotNull(s.currentBlock());
        assertEquals(0, s.currentBlock().getRow()); // 새로 Spawn된 T 블록은 맨 위에서 시작
    }

    @Test
    void 이미_바닥에_닿은_블록의_하드_드롭은_0점() {
        GameSession session = startedSession(new OBlock(), new OBlock());
        for (int i = 0; i < 18; i++) session.tick(); // 바닥까지 내려가 이동 불가 상태
        long before = session.snapshot().score();
        session.hardDrop();

        assertEquals(before, session.snapshot().score());
    }
}
