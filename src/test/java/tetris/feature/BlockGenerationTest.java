package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.rule.NextBlockQueue;

/** Next 블록 큐가 생성기 순서대로 채워지고 길이를 유지하며 맨 앞 블록을 내주는지 검증한다. 생성기 자체는 WeightedBlockGeneratorTest가 맡는다 */
public class BlockGenerationTest {
    // 정해진 순서대로 블록을 내주는 생성기 (BLK-3: 고정 순열 주입)
    private static BlockGenerator sequence(Block... blocks) {
        Iterator<Block> it = Arrays.asList(blocks).iterator();
        return it::next;
    }

    //#region NextBlockQueue
    @Test
    void 생성하면_previewCount만큼_채워진다() {
        Block first = new IBlock();
        NextBlockQueue queue = new NextBlockQueue(sequence(first, new OBlock()), 1);
        assertEquals(1, queue.previewCount());
        assertSame(first, queue.peek(0));
        assertThrows(IllegalArgumentException.class, () -> queue.peek(1)); // 맨 앞 1개만 노출
    }

    @Test
    void pop은_맨_앞을_꺼내고_뒤를_채운다() {
        Block a = new IBlock(), b = new OBlock(), c = new TBlock();
        NextBlockQueue queue = new NextBlockQueue(sequence(a, b, c), 1);
        assertSame(a, queue.pop());
        assertSame(b, queue.peek(0)); // 길이 유지
        assertSame(b, queue.pop());
        assertSame(c, queue.peek(0));
    }

    @Test
    void previewCount가_2면_순서대로_두_개를_보여준다() {
        Block a = new IBlock(), b = new OBlock(), c = new TBlock();
        NextBlockQueue queue = new NextBlockQueue(sequence(a, b, c), 2);
        assertSame(a, queue.peek(0));
        assertSame(b, queue.peek(1));
        queue.pop();
        assertSame(b, queue.peek(0));
        assertSame(c, queue.peek(1));
    }

    @Test
    void reset은_큐를_새_블록으로_다시_채운다() {
        Block a = new IBlock(), b = new OBlock();
        NextBlockQueue queue = new NextBlockQueue(sequence(a, b), 1);
        queue.reset();
        assertSame(b, queue.peek(0));
    }

    @Test
    void 잘못된_인자는_예외() {
        assertThrows(IllegalArgumentException.class, () -> new NextBlockQueue(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new NextBlockQueue(sequence(new IBlock()), 0));
        NextBlockQueue queue = new NextBlockQueue(sequence(new IBlock()), 1);
        assertThrows(IllegalArgumentException.class, () -> queue.peek(-1));
    }
    //#endregion
}
