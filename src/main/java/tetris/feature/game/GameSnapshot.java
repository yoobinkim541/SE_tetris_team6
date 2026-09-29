package tetris.feature.game;

import tetris.component.block.Block;

/** UI가 읽는 게임 상태 사본. 보드 배열·블록은 복사본이라 수정해도 게임에 영향이 없다. */
public record GameSnapshot(
        int[][] cells,
        Block currentBlock,
        Block nextBlock,
        long score,
        int level,
        int lines,
        GameState state) {
}
