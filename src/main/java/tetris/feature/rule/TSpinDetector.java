package tetris.feature.rule;

import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.feature.data.Board;

/**
 * 3코너 규칙 T-스핀 판정 (TSP-1~4). Lock하기 직전, 보드에 기록하기 전에 부른다.
 * <ul>
 *   <li>TSP-1 T 블록이고 마지막으로 성공한 조작이 회전이어야 한다 (lastActionIsRotate).</li>
 *   <li>TSP-2 T의 중심을 둘러싼 대각선 4칸 중 3칸 이상이 막혀 있어야 한다. 벽·바닥도 막힌 칸이다.</li>
 *   <li>TSP-3 T가 가리키는 쪽의 두 코너가 모두 막혔으면 T-스핀, 아니면 T-스핀 미니.
 *       단, 마지막 킥(4번)으로 들어간 회전은 T-스핀으로 올린다.</li>
 * </ul>
 */
public final class TSpinDetector {
    /** SRS 킥 표의 마지막 번호. 이 킥으로 들어가면 미니를 T-스핀으로 올린다 */
    public static final int LAST_KICK = 4;

    private static final int[][] SINGLE_CELL = {{1}};

    private TSpinDetector() {
    }

    public static TSpinType detect(Board board, Block block, boolean lastActionIsRotate, int lastKickIndex) {
        if (block == null || block.getType() != BlockType.T || block.isWeight() || !lastActionIsRotate) return TSpinType.NONE;
        if (block.getShape().length != 3) return TSpinType.NONE;

        // T의 3×3 격자에서 중심은 회전과 관계없이 (1, 1)이다
        int centerRow = block.getRow() + 1;
        int centerCol = block.getCol() + 1;
        boolean topLeft = blocked(board, centerRow - 1, centerCol - 1);
        boolean topRight = blocked(board, centerRow - 1, centerCol + 1);
        boolean bottomLeft = blocked(board, centerRow + 1, centerCol - 1);
        boolean bottomRight = blocked(board, centerRow + 1, centerCol + 1);

        int corners = count(topLeft) + count(topRight) + count(bottomLeft) + count(bottomRight);
        if (corners < 3) return TSpinType.NONE;

        boolean frontsBlocked = switch (block.getRotation()) {
            case 0 -> topLeft && topRight;        // 위를 가리킴
            case 1 -> topRight && bottomRight;    // 오른쪽
            case 2 -> bottomLeft && bottomRight;  // 아래
            default -> topLeft && bottomLeft;     // 왼쪽
        };
        if (frontsBlocked || lastKickIndex == LAST_KICK) return TSpinType.FULL;
        return TSpinType.MINI;
    }

    private static boolean blocked(Board board, int row, int col) {
        return !board.canPlace(SINGLE_CELL, row, col);
    }

    private static int count(boolean value) {
        return value ? 1 : 0;
    }
}
