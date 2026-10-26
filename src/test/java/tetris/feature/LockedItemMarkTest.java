package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.OBlock;
import tetris.component.block.WeightBlock;
import tetris.feature.data.Board;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameState;
import tetris.feature.item.Cell;
import tetris.feature.item.ItemType;
import tetris.feature.rule.ItemBlockGenerator;

/**
 * 고정된 칸에 남는 아이템 문자(D·S 등)가 보드 변화를 따라가는지 검증한다.
 * 블록을 놓으면 기록되고, 행이 내려가면 같이 내려가고, 칸이 지워지면 사라진다.
 */
public class LockedItemMarkTest {
    // 논리 좌표 (row, col)에 아이템이 붙은 O블록을 놓는다. 아이템은 O블록의 (itemRow, itemCol) 칸
    private static void placeO(Board board, int row, int col, ItemType item, int itemRow, int itemCol) {
        OBlock block = new OBlock();
        if (item != null) block.attachItem(itemRow, itemCol, item);
        block.setPosition(row, col);
        assertTrue(board.tryPlaceBlock(block));
    }

    private static void fill(Board board, int row, int col) {
        Block cell = GameTestSupport.cell();
        cell.setPosition(row, col);
        assertTrue(board.tryPlaceBlock(cell));
    }

    @Test
    void 아이템이_붙은_블록을_놓으면_그_칸에_문자가_남는다() {
        Board board = new Board();
        placeO(board, 18, 4, ItemType.DOUBLE_SCORE, 1, 0);

        assertEquals(Map.of(new Cell(19, 4), ItemType.DOUBLE_SCORE), board.copyItemCells());
    }

    @Test
    void 아이템이_없는_블록은_문자를_남기지_않는다() {
        Board board = new Board();
        placeO(board, 18, 4, null, 0, 0);

        assertTrue(board.copyItemCells().isEmpty());
    }

    @Test
    void 아래_행이_지워지면_문자도_같이_내려간다() {
        Board board = new Board();
        placeO(board, 17, 0, ItemType.SLOW, 0, 1); // S는 (17, 1)
        for (int col = 0; col < Board.WIDTH; col++) if (board.isEmpty(19, col)) fill(board, 19, col);

        board.clearRows(board.findFullRows());

        assertEquals(Map.of(new Cell(18, 1), ItemType.SLOW), board.copyItemCells());
    }

    @Test
    void 문자가_있는_행이_지워지면_문자도_사라진다() {
        Board board = new Board();
        placeO(board, 18, 0, ItemType.DOUBLE_SCORE, 1, 0);

        board.removeRows(List.of(19));

        assertTrue(board.copyItemCells().isEmpty());
    }

    @Test
    void 칸_삭제와_무게추_분쇄로_지워진_칸의_문자도_사라진다() {
        Board board = new Board();
        placeO(board, 18, 0, ItemType.DOUBLE_SCORE, 1, 1); // D는 (19, 1)
        placeO(board, 18, 6, ItemType.SLOW, 0, 0);         // S는 (18, 6)

        board.clearCells(List.of(new Cell(19, 1)));
        assertEquals(Map.of(new Cell(18, 6), ItemType.SLOW), board.copyItemCells());

        board.clearUnder(new WeightBlock(), 17, 5); // 무게추 아래 줄이 (18, 5..8)을 덮는다
        assertTrue(board.copyItemCells().isEmpty());
    }

    @Test
    void 지운_칸에_다른_블록을_놓으면_이전_문자가_남지_않는다() {
        Board board = new Board();
        placeO(board, 18, 0, ItemType.DOUBLE_SCORE, 1, 0);
        board.clearCells(List.of(new Cell(19, 0)));

        fill(board, 19, 0);

        assertTrue(board.copyItemCells().isEmpty());
    }

    @Test
    void reset하면_문자도_모두_지워진다() {
        Board board = new Board();
        placeO(board, 18, 0, ItemType.DOUBLE_SCORE, 1, 0);
        board.reset();

        assertTrue(board.copyItemCells().isEmpty());
    }

    @Test
    void 숨은_줄의_문자는_화면용_목록에_넣지_않는다() {
        Board board = new Board();
        placeO(board, -2, 0, ItemType.SLOW, 0, 0); // S는 숨은 줄 (-2, 0)

        assertTrue(board.copyItemCells().isEmpty());
    }

    @Test
    void 화면용_목록은_바꿀_수_없다() {
        Board board = new Board();
        placeO(board, 18, 0, ItemType.SLOW, 1, 0);

        assertThrows(UnsupportedOperationException.class, () -> board.copyItemCells().clear());
    }

    //#region 실제 세션
    // 아이템 모드에서 O블록만 내주는 생성기로 10줄을 지워 아이템 블록을 받는다. 아이템 종류는 시드로 고른다
    private static GameSession sessionWithItemNext(ItemType wanted) {
        for (long seed = 0; seed < 200; seed++) {
            ItemBlockGenerator generator = new ItemBlockGenerator(OBlock::new, WeightBlock::new, new Random(seed));
            GameSession session = new GameSession(GameMode.ITEM, Difficulty.NORMAL, generator,
                    new GameTestSupport.RecordingClock(), new GameTestSupport.RecordingListener());
            session.start();
            for (int round = 0; round < 5; round++) for (int col = 0; col < 10; col += 2) drop(session, col);
            Block next = session.snapshot().nextBlock();
            for (int r = 0; r < 2; r++)
                for (int c = 0; c < 2; c++)
                    if (next.itemCellAt(r, c).orElse(null) == wanted) return session;
        }
        throw new AssertionError("item not found: " + wanted);
    }

    private static void drop(GameSession session, int col) {
        for (int i = 0; i < 12; i++) session.moveLeft();
        for (int i = 0; i < col; i++) session.moveRight();
        session.hardDrop();
        while (session.getState() == GameState.CLEARING) session.tick();
    }

    @Test
    void 점수_2배_블록을_놓으면_스냅샷에_D가_남는다() {
        GameSession session = sessionWithItemNext(ItemType.DOUBLE_SCORE);
        drop(session, 0); // 일반 블록
        drop(session, 2); // D 블록

        assertEquals(List.of(ItemType.DOUBLE_SCORE), List.copyOf(session.snapshot().lockedItems().values()));
    }

    @Test
    void 슬로우_블록을_놓으면_스냅샷에_S가_남는다() {
        GameSession session = sessionWithItemNext(ItemType.SLOW);
        drop(session, 0);
        drop(session, 2);

        assertEquals(List.of(ItemType.SLOW), List.copyOf(session.snapshot().lockedItems().values()));
    }

    @Test
    void 줄_삭제_블록은_자기_행을_지워_문자가_남지_않는다() {
        GameSession session = sessionWithItemNext(ItemType.LINE_CLEAR);
        drop(session, 0);
        drop(session, 2);

        assertTrue(session.snapshot().lockedItems().isEmpty());
    }

    @Test
    void 새_게임을_시작하면_남은_문자가_지워진다() {
        GameSession session = sessionWithItemNext(ItemType.DOUBLE_SCORE);
        drop(session, 0);
        drop(session, 2);
        session.start();

        assertTrue(session.snapshot().lockedItems().isEmpty());
    }
    //#endregion
}
