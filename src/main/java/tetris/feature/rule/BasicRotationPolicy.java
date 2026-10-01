package tetris.feature.rule;

import tetris.component.block.Block;
import tetris.feature.data.Board;

/** 앵커 고정 n×n 행렬 회전, 충돌 시 취소 (ROT-2, ROT-3). O는 변화 없음. */
public class BasicRotationPolicy implements RotationPolicy {
    @Override
    public boolean rotate(Board board, Block block) {
        if (!board.canPlace(block.rotateRight(), block.getRow(), block.getCol())) return false;
        block.rotateClockwise();
        return true;
    }
}
