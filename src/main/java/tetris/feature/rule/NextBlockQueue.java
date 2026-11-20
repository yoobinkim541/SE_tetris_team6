package tetris.feature.rule;

import java.util.ArrayList;
import java.util.List;
import tetris.component.block.Block;

/** Next 블록 큐 (NXT-1). 1차는 previewCount = 1, UI는 맨 앞 1개만 표시한다. */
public class NextBlockQueue {
    private final BlockGenerator generator;
    private int previewCount;
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

    /**
     * 맨 앞 조각을 주어진 블록으로 바꾼다. 큐보다 길면 뒤에 붙인다.
     * 레이드 저주 블록이 다음 조각을 S/Z로 바꿀 때 쓴다. 생성기 순서는 그 다음부터 그대로다.
     */
    public void replaceFront(List<Block> pieces) {
        if (pieces == null) throw new IllegalArgumentException("pieces cannot be null");
        for (int i = 0; i < pieces.size(); i++) {
            Block piece = pieces.get(i);
            if (piece == null) throw new IllegalArgumentException("piece cannot be null");
            if (i < queue.size()) queue.set(i, piece);
            else queue.add(piece);
        }
    }

    /**
     * 미리보기 개수를 늘린다. cap을 넘기지 않고, 이미 더 길면 줄이지 않는다.
     * 레이드 망원경 카드가 NEXT를 최대 5개까지 늘릴 때 쓴다.
     */
    public void expandPreview(int additional, int cap) {
        if (additional < 0 || cap < 1) throw new IllegalArgumentException("preview");
        int next = Math.min(cap, previewCount + additional);
        if (next <= previewCount) return;
        previewCount = next;
        fill();
    }
}
