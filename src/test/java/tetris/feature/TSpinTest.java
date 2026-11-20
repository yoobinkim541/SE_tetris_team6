package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tetris.feature.GameTestSupport.cell;
import static tetris.feature.GameTestSupport.sequence;

import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.debug.AsciiBoard;
import tetris.feature.GameTestSupport.RecordingClock;
import tetris.feature.GameTestSupport.RecordingListener;
import tetris.feature.data.Board;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameState;
import tetris.feature.rule.TSpinDetector;
import tetris.feature.rule.TSpinType;

/** TSP-1~4: 3코너 규칙 T-스핀 판정 */
public class TSpinTest {
    private final Board board = new Board();

    private void fill(int row, int col) {
        Block c = cell();
        c.setPosition(row, col);
        assertTrue(board.tryPlaceBlock(c));
    }

    /** rotation번 시계방향으로 돌린 T를 anchor (row, col)에 둔다. 중심은 (row+1, col+1) */
    private static TBlock t(int rotation, int row, int col) {
        TBlock t = new TBlock();
        for (int i = 0; i < rotation; i++) t.rotateClockwise();
        t.setPosition(row, col);
        return t;
    }

    //#region 판정기 (보드 직접 구성)
    @Test
    void 코너_3개와_앞쪽_코너_2개가_막히면_T스핀() {
        TBlock down = t(2, 10, 3);                   // 아래를 가리킴, 중심 (11, 4)
        fill(10, 3);                                 // 위 왼쪽
        fill(12, 3);                                 // 아래 왼쪽 (앞)
        fill(12, 5);                                 // 아래 오른쪽 (앞)
        assertEquals(TSpinType.FULL, TSpinDetector.detect(board, down, true, 0));
    }

    @Test
    void 앞쪽_코너가_하나만_막히면_미니() {
        TBlock down = t(2, 10, 3);
        fill(10, 3);
        fill(10, 5);                                 // 뒤쪽 2개
        fill(12, 3);                                 // 앞쪽 1개
        assertEquals(TSpinType.MINI, TSpinDetector.detect(board, down, true, 0));
    }

    @Test
    void 마지막_킥으로_들어가면_미니도_T스핀() {
        TBlock down = t(2, 10, 3);
        fill(10, 3);
        fill(10, 5);
        fill(12, 3);
        assertEquals(TSpinType.FULL, TSpinDetector.detect(board, down, true, TSpinDetector.LAST_KICK));
    }

    @Test
    void 코너가_2개뿐이면_아니다() {
        TBlock down = t(2, 10, 3);
        fill(12, 3);
        fill(12, 5);
        assertEquals(TSpinType.NONE, TSpinDetector.detect(board, down, true, 0));
    }

    @Test
    void 마지막_행동이_회전이_아니면_아니다() {
        TBlock down = t(2, 10, 3);
        fill(10, 3);
        fill(12, 3);
        fill(12, 5);
        assertEquals(TSpinType.NONE, TSpinDetector.detect(board, down, false, 0));
    }

    @Test
    void T가_아니면_아니다() {
        Block o = new OBlock();
        o.setPosition(10, 3);
        fill(9, 2);
        fill(9, 5);
        fill(12, 2);
        assertEquals(TSpinType.NONE, TSpinDetector.detect(board, o, true, 0));
    }

    @Test
    void 벽과_바닥도_막힌_코너로_센다() {
        TBlock right = t(1, 5, -1);                 // 오른쪽을 가리킴, 중심 (6, 0): 왼쪽 코너 2개가 벽
        fill(5, 1);                                  // 위 오른쪽 (앞)
        assertEquals(TSpinType.MINI, TSpinDetector.detect(board, right, true, 0));

        TBlock up = t(0, 18, 3);                     // 위를 가리킴, 중심 (19, 4): 아래 코너 2개가 바닥
        fill(18, 3);                                 // 위 왼쪽 (앞)
        fill(18, 5);                                 // 위 오른쪽 (앞)
        assertEquals(TSpinType.FULL, TSpinDetector.detect(board, up, true, 0));
    }
    //#endregion

    //#region 게임 안에서 (T-스핀 더블)
    /** 가로 2칸 블록. 지붕(돌출부)을 만들 때 쓴다 */
    private static Block domino() {
        return new Block(new int[][] {{1, 1}}) {
            @Override
            public BlockType getType() {
                return BlockType.O;
            }
        };
    }

    /**
     * 17~19행에 T-스핀 더블 자리를 만든다 (X: 고정 칸, 숫자: 열).
     * <pre>
     *        0123456789
     *   17   ..XX......   ← col 3이 지붕
     *   18   XXX...XXXX
     *   19   XXXX.XXXXX
     * </pre>
     * 1칸 블록과 가로 2칸 블록을 Spawn 열(4)에서 옮겨 Hard Drop으로 쌓는다.
     */
    private static Block[] slotPieces() {
        java.util.List<Block> pieces = new java.util.ArrayList<>();
        int[] singles = {3, 2, 2, 0, 0, 1, 1, 5, 6, 6, 7, 7, 8, 8, 9, 9};
        for (int ignored : singles) pieces.add(cell());
        pieces.add(domino());
        pieces.add(new TBlock());
        pieces.add(new OBlock());
        return pieces.toArray(new Block[0]);
    }

    private static void dropAt(GameSession session, int col) {
        int from = session.snapshot().currentBlock().getCol();
        for (int i = from; i > col; i--) session.moveLeft();
        for (int i = from; i < col; i++) session.moveRight();
        session.hardDrop();
    }

    private static GameSession sessionWithSlot() {
        GameSession session = new GameSession(sequence(slotPieces()), new RecordingClock(), new RecordingListener());
        session.start();
        for (int col : new int[] {3, 2, 2, 0, 0, 1, 1, 5, 6, 6, 7, 7, 8, 8, 9, 9}) dropAt(session, col);
        dropAt(session, 2);                          // 가로 2칸: col 2 위(17행)에 걸려 col 3 지붕이 된다
        assertEquals("..XX......".replace('X', 'O'), AsciiBoard.row(session.snapshot(), 17));
        assertEquals("OOO...OOOO", AsciiBoard.row(session.snapshot(), 18));
        assertEquals("OOOO.OOOOO", AsciiBoard.row(session.snapshot(), 19));
        assertEquals(BlockType.T, session.snapshot().currentBlock().getType());
        return session;
    }

    @Test
    void 회전으로_밀어_넣고_굳히면_T스핀_더블() {
        GameSession session = sessionWithSlot();
        session.rotateRight();                       // 세로(오른쪽을 가리킴)로 세운다
        for (int i = 0; i < 17; i++) session.moveDown(); // col 4로 17행까지 내려간다
        session.rotateRight();                       // 아래를 가리키며 홈에 들어간다
        session.tick();                              // 유예가 끝나 Lock

        assertEquals(TSpinType.FULL, session.getLastTSpin());
        assertEquals(TSpinType.FULL, session.snapshot().lastTSpin());
        assertEquals(GameState.CLEARING, session.getState());
        session.tick();                              // 150 ms 뒤 줄 삭제
        assertEquals(2, session.snapshot().lines());
        assertEquals(TSpinType.FULL, session.snapshot().lastTSpin()); // 다음 Lock 전까지 표시
    }

    @Test
    void 회전하지_않고_떨어뜨리면_T스핀이_아니다() {
        GameSession session = sessionWithSlot();
        session.rotateRight();
        for (int i = 0; i < 17; i++) session.moveDown(); // 마지막 행동: 하강
        session.tick();
        assertEquals(TSpinType.NONE, session.getLastTSpin());
    }

    @Test
    void 다음_Lock에서_판정이_다시_바뀌고_새_게임은_NONE() {
        GameSession session = sessionWithSlot();
        session.rotateRight();
        for (int i = 0; i < 17; i++) session.moveDown();
        session.rotateRight();
        session.tick();
        session.tick();                              // 줄 삭제 후 O Spawn
        session.hardDrop();
        assertEquals(TSpinType.NONE, session.getLastTSpin());

        session.pause();
        session.restart();
        assertEquals(TSpinType.NONE, session.getLastTSpin());
    }
    //#endregion
}
