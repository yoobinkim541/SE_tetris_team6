package tetris.feature.rule;

import tetris.component.block.Block;
import tetris.feature.data.Board;

/** 회전 정책 (ROT-5). 1차: BasicRotationPolicy. 향후 Wall Kick/SRS 구현체 추가. */
public interface RotationPolicy {
    /**
     * 시계방향 90° 회전을 시도한다.
     * 유효하면 block의 shape를 갱신하고 true, 충돌이면 상태를 바꾸지 않고 false.
     */
    boolean rotate(Board board, Block block);
}
