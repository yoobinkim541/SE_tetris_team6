package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
import tetris.feature.rule.BlockGenerator;
import tetris.feature.rule.NextBlockQueue;
import tetris.feature.rule.RandomBlockGenerator;

/** BLK-3, NXT-1 (시드 고정 생성기 주입, 7종 등장, 큐 길이 유지, 맨 앞 1개 노출) */
public class BlockGenerationTest {
    // 정해진 순서대로 블록을 내주는 생성기 (BLK-3: 고정 순열 주입)
    private static BlockGenerator sequence(Block... blocks) {
        Iterator<Block> it = Arrays.asList(blocks).iterator();
        return it::next;
    }

    private static List<BlockType> draw(BlockGenerator generator, int count) {
        List<BlockType> types = new ArrayList<>();
        for (int i = 0; i < count; i++) types.add(generator.next().getType());
        return types;
    }

    //#region RandomBlockGenerator
    @Test
    void 같은_시드면_같은_순서() {
        assertEquals(draw(new RandomBlockGenerator(new Random(42)), 50),
                     draw(new RandomBlockGenerator(new Random(42)), 50));
    }

    @Test
    void 고정_시드로_7종이_모두_등장() {
        Set<BlockType> seen = EnumSet.noneOf(BlockType.class);
        seen.addAll(draw(new RandomBlockGenerator(new Random(42)), 100));
        assertEquals(EnumSet.allOf(BlockType.class), seen);
    }

    @Test
    void 생성된_블록은_0도_모양이고_매번_새_인스턴스() {
        RandomBlockGenerator generator = new RandomBlockGenerator(new Random(7));
        Block[] reference = {new IBlock(), new JBlock(), new LBlock(), new OBlock(), new SBlock(), new TBlock(), new ZBlock()};
        for (int i = 0; i < 50; i++) {
            Block block = generator.next();
            Block expected = reference[block.getType().ordinal()];
            assertTrue(Arrays.deepEquals(expected.getShape(), block.getShape()));
            assertEquals(0, block.getRotation());
            assertNotSame(block, generator.next());
        }
    }

    @Test
    void Random이_null이면_예외() {
        assertThrows(IllegalArgumentException.class, () -> new RandomBlockGenerator(null));
    }
    //#endregion

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
