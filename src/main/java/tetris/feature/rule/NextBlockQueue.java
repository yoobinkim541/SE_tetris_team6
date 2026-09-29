package tetris.feature.rule;

import tetris.component.block.Block;

/** Next 블록 큐 (NXT-1). 1차는 previewCount = 1, UI는 맨 앞 1개만 표시한다. */
public class NextBlockQueue {
    public NextBlockQueue(BlockGenerator generator, int previewCount) {
        // 큐를 previewCount개 이상으로 채움
    }

    /** 맨 앞 블록을 꺼내고 뒤에 새 블록을 채워 길이를 유지 (Spawn 시 호출) */
    public Block pop() {
        throw new UnsupportedOperationException("TODO");
    }

    /** index번째 대기 블록 조회 (제거하지 않음). 0이 다음 Spawn 블록 */
    public Block peek(int index) {
        throw new UnsupportedOperationException("TODO");
    }

    public int previewCount() {
        throw new UnsupportedOperationException("TODO");
    }

    /** 새 게임/Restart 시 큐를 다시 채움 */
    public void reset() {
        // 비우고 다시 채움
    }
}
