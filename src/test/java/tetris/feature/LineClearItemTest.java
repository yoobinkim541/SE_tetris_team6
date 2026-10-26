package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tetris.component.block.OBlock;
import tetris.feature.data.Board;
import tetris.feature.item.GameModifiers;
import tetris.feature.item.ItemResult;
import tetris.feature.item.ItemType;
import tetris.feature.item.LineClearEffect;

/** 줄 삭제 아이템(L)이 아이템 칸의 행을 꽉 차지 않아도 지울 행으로 돌려주는지, 보드·지속 효과를 바꾸지 않는지 검증한다 */
public class LineClearItemTest {
    private final LineClearEffect effect = new LineClearEffect();

    @Test
    void 아이템_칸의_행을_지울_행으로_돌려준다() {
        ItemResult result = effect.onLock(new Board(), 17, 4, new GameModifiers());

        assertEquals(Set.of(17), result.rows());
        assertTrue(result.cells().isEmpty());
    }

    @Test
    void 꽉_차지_않은_행이어도_돌려준다() {
        Board board = new Board(); // 빈 보드라 어떤 행도 꽉 차 있지 않다
        assertEquals(Set.of(19), effect.onLock(board, 19, 0, new GameModifiers()).rows());
    }

    @Test
    void 열_위치와_관계없이_같은_행을_돌려준다() {
        for (int col = 0; col < Board.WIDTH; col++) {
            assertEquals(Set.of(5), effect.onLock(new Board(), 5, col, new GameModifiers()).rows());
        }
    }

    @Test
    void 숨은_줄에_놓여도_그_행을_그대로_돌려준다() {
        assertEquals(Set.of(-1), effect.onLock(new Board(), -1, 3, new GameModifiers()).rows());
        assertEquals(Set.of(-2), effect.onLock(new Board(), -2, 3, new GameModifiers()).rows());
    }

    @Test
    void 보드를_바꾸지_않는다() {
        Board board = new Board();
        OBlock block = new OBlock();
        block.setPosition(18, 0);
        board.tryPlaceBlock(block);
        int[][] before = board.copyCells();

        effect.onLock(board, 18, 0, new GameModifiers());

        assertArrayEquals(before, board.copyCells());
    }

    @Test
    void 지속_효과를_등록하지_않는다() {
        GameModifiers modifiers = new GameModifiers();
        effect.onLock(new Board(), 10, 0, modifiers);

        assertEquals(1, modifiers.scoreMultiplier());
        assertEquals(1.0, modifiers.fallMultiplier());
    }

    @Test
    void ItemType_L은_LineClearEffect를_효과로_갖는다() {
        assertEquals('L', ItemType.LINE_CLEAR.symbol());
        assertInstanceOf(LineClearEffect.class, ItemType.LINE_CLEAR.effect());
    }

    @Test
    void 아직_구현하지_않은_아이템도_효과가_있고_아무것도_지우지_않는다() {
        for (ItemType type : ItemType.values()) {
            assertNotNull(type.effect(), type.name());
            if (type == ItemType.LINE_CLEAR) continue;

            ItemResult result = type.effect().onLock(new Board(), 10, 4, new GameModifiers());
            assertTrue(result.rows().isEmpty(), type.name());
            assertTrue(result.cells().isEmpty(), type.name());
        }
    }
}
