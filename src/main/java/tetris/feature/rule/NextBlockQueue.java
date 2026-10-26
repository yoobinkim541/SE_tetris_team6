package tetris.feature.rule;

import java.util.ArrayList;
import java.util.List;
import tetris.component.block.Block;

/** Next 블록 큐 (NXT-1). 1차는 previewCount = 1, UI는 맨 앞 1개만 표시한다. */
public class NextBlockQueue {
    private final BlockGenerator generator;
    private final int previewCount;
    private final List<Block> queue = new ArrayList<>(); // index 0 = 다음 Spawn 블록

    public NextBlockQueue(BlockGenerator generator, int previewCount) {
        if (generator == null) throw new IllegalArgumentException("generator cannot be null");
        if (previewCount < 1) throw new IllegalArgumentException("previewCount should be 1 or more. Value : " + previewCount);
        this.generator = generator;
        this.previewCount = previewCount;
        fill(); // 큐를 previewCount개로 채움
    }

    /** 맨 앞 블록을 꺼내고 뒤에 새 블록을 채워 길이를 유지 (Spawn 시 호출) */
    public Block pop() {
        Block front = queue.remove(0);
        queue.add(generator.next());
        return front;
    }

    /** index번째 대기 블록 조회 (제거하지 않음). 0이 다음 Spawn 블록. 범위 밖이면 IllegalArgumentException */
    public Block peek(int index) {
        if (index < 0 || index >= queue.size())
            throw new IllegalArgumentException(String.format("index should be between %d and %d. Value : %d", 0, queue.size() - 1, index));
        return queue.get(index);
    }

    public int previewCount() {
        return previewCount;
    }

    /** 새 게임/Restart 시 큐를 다시 채움 */
    public void reset() {
        queue.clear();
        fill();
    }

    private void fill() {
        while (queue.size() < previewCount) queue.add(generator.next());
    }
}
