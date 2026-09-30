package tetris.feature.rule;

import tetris.component.block.Block;
import tetris.feature.data.Board;

/**
 * Spawn 앵커 계산 (SPN-1). 값이 있는 가장 높은 칸이 보이는 맨 윗줄(row 0)에 오도록 한다.
 * 행렬 0행이 비어 있는 I는 숨은 줄(-1행)에 앵커를 둬야 맨 윗줄에 보이고, 숨은 줄 덕분에 Spawn 직후 회전도 가능하다.
 */
public class SpawnPolicy {
    public int row(Block block) {
        int[][] shape = block.getShape();
        for (int row = 0; row < shape.length; row++) {
            for (int value : shape[row]) {
                if (value != Board.EMPTY) return -row;
            }
        }
        throw new IllegalArgumentException("block shape has no cells");
    }

    /** 채워진 칸이 아니라 행렬 크기 기준으로 가운데, 나누어떨어지지 않으면 왼쪽 */
    public int column(Block block) {
        return (Board.WIDTH - block.getShape().length) / 2;
    }
}
