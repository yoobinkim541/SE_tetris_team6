package tetris.component.block;

/**
 * 무게추 (ITW-1). 블록 전체가 아이템이다. 너비 4, 높이 2의 고정 모양이고 회전하지 않는다.
 *
 * <pre>
 *  WW
 * WWWW
 * </pre>
 *
 * 4x4 행렬의 위 두 행만 쓴다. Spawn 위치(row 0, col 3)는 SpawnPolicy의 일반 규칙이 그대로 적용된다 (ITW-2).
 * 첫 하강 실패 뒤의 분쇄 동작은 GameSession이 isWeight()로 분기해 처리한다 (ARC-3).
 */
public class WeightBlock extends Block {
    public WeightBlock() {
        super(new int[][] {
            {0, 1, 1, 0},
            {1, 1, 1, 1},
            {0, 0, 0, 0},
            {0, 0, 0, 0}
        });
    }

    @Override
    public BlockType getType() {
        return BlockType.W;
    }

    @Override
    public boolean isWeight() {
        return true;
    }

    /** 회전하지 않으므로 지금 모양을 그대로 돌려준다 */
    @Override
    public int[][] rotateRight() {
        int[][] same = new int[shape.length][];
        for (int i = 0; i < shape.length; i++) same[i] = shape[i].clone();
        return same;
    }

    @Override
    public void rotateClockwise() {
        // ITW-1: 회전 입력은 무시한다
    }
}
