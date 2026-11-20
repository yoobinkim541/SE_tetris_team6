package tetris.feature.rule;

import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.feature.data.Board;

/**
 * SRS(Super Rotation System) 회전과 Wall Kick (ROT-8, ROT-9).
 * 모양 행렬은 ROT-2의 n×n 회전을 그대로 쓰고(블록 모양이 SRS 기준 모양과 같다), 제자리 회전이 막히면
 * 표의 대체 위치를 순서대로 시도해 처음 놓이는 곳으로 옮긴다. 모두 막히면 아무것도 바꾸지 않는다 (ROT-3).
 * 표의 (dx, dy)는 오른쪽·위가 +이고, 보드 좌표로는 (col + dx, row - dy)다.
 * 반시계 표는 반대 방향 시계 킥을 부호만 뒤집은 것이다.
 */
public class SrsRotationPolicy implements RotationPolicy {
    // [회전 전 상태 0..3][킥 번호 0..4] = {dx, dy}. 0→R, R→2, 2→L, L→0
    private static final int[][][] JLSTZ_KICKS = {
            {{0, 0}, {-1, 0}, {-1, 1}, {0, -2}, {-1, -2}},
            {{0, 0}, {1, 0}, {1, -1}, {0, 2}, {1, 2}},
            {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}},
            {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}},
    };
    private static final int[][][] I_KICKS = {
            {{0, 0}, {-2, 0}, {1, 0}, {-2, -1}, {1, 2}},
            {{0, 0}, {-1, 0}, {2, 0}, {-1, 2}, {2, -1}},
            {{0, 0}, {2, 0}, {-1, 0}, {2, 1}, {-1, -2}},
            {{0, 0}, {1, 0}, {-2, 0}, {1, -2}, {-2, 1}},
    };
    private static final int[][][] NO_KICKS = {{{0, 0}}, {{0, 0}}, {{0, 0}}, {{0, 0}}};
    // 반시계. 인덱스는 회전 전 상태. 0→L, R→0, 2→R, L→2
    private static final int[][][] JLSTZ_CCW = {
            {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}},
            {{0, 0}, {1, 0}, {1, -1}, {0, 2}, {1, 2}},
            {{0, 0}, {-1, 0}, {-1, 1}, {0, -2}, {-1, -2}},
            {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}},
    };
    private static final int[][][] I_CCW = {
            {{0, 0}, {-1, 0}, {2, 0}, {-1, 2}, {2, -1}},
            {{0, 0}, {2, 0}, {-1, 0}, {2, 1}, {-1, -2}},
            {{0, 0}, {1, 0}, {-2, 0}, {1, -2}, {-2, 1}},
            {{0, 0}, {-2, 0}, {1, 0}, {-2, -1}, {1, 2}},
    };

    @Override
    public boolean rotate(Board board, Block block) {
        return tryRotate(board, block) >= 0;
    }

    @Override
    public int tryRotate(Board board, Block block) {
        int[][] rotated = block.rotateRight();
        int[][] kicks = kicksFor(block)[block.getRotation()];
        for (int i = 0; i < kicks.length; i++) {
            int row = block.getRow() - kicks[i][1];
            int col = block.getCol() + kicks[i][0];
            if (!board.canPlace(rotated, row, col)) continue;
            block.rotateClockwise();
            block.setPosition(row, col);
            return i;
        }
        return -1;
    }

    @Override
    public int tryRotateCounterClockwise(Board board, Block block) {
        int[][] rotated = block.rotateLeft();
        int[][] kicks = ccwKicksFor(block)[block.getRotation()];
        for (int i = 0; i < kicks.length; i++) {
            int row = block.getRow() - kicks[i][1];
            int col = block.getCol() + kicks[i][0];
            if (!board.canPlace(rotated, row, col)) continue;
            block.rotateCounterClockwise();
            block.setPosition(row, col);
            return i;
        }
        return -1;
    }
    private static int[][][] kicksFor(Block block) {
        BlockType type = block.getType();
        if (type == BlockType.I) return I_KICKS;
        if (type == BlockType.O) return NO_KICKS;
        return JLSTZ_KICKS;
    }

    private static int[][][] ccwKicksFor(Block block) {
        BlockType type = block.getType();
        if (type == BlockType.I) return I_CCW;
        if (type == BlockType.O) return NO_KICKS;
        return JLSTZ_CCW;
    }
}
