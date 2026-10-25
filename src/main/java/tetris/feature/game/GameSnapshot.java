package tetris.feature.game;

import java.util.List;
import tetris.component.block.Block;
import tetris.feature.item.ActiveEffect;
import tetris.feature.item.Cell;

/**
 * UI가 읽는 게임 상태 사본. 보드 배열·블록은 복사본이라 수정해도 게임에 영향이 없다.
 * 아이템 칸 정보는 블록 복사본(Block.itemCellAt)이 들고 있어 따로 두지 않는다.
 */
public record GameSnapshot(
        int[][] cells,
        Block currentBlock,
        Block nextBlock,
        long score,
        int level,
        int lines,
        GameState state,
        GameMode mode,
        Difficulty difficulty,
        List<Cell> clearingCells,       // CLEARING 중 흰색으로 그릴 칸. 그 외에는 빈 목록 (ANI-5)
        int nextItemLines,              // 다음 아이템까지 남은 줄 수. 일반 모드는 0 (MOD-4)
        List<ActiveEffect> effects) {   // 켜져 있는 지속 효과와 남은 블록 수 (MOD-4)

    public GameSnapshot {
        clearingCells = List.copyOf(clearingCells);
        effects = List.copyOf(effects);
    }

    /** 일반 모드·NORMAL, 애니메이션과 아이템 정보가 없는 스냅샷 */
    public GameSnapshot(int[][] cells, Block currentBlock, Block nextBlock, long score, int level, int lines, GameState state) {
        this(cells, currentBlock, nextBlock, score, level, lines, state,
                GameMode.CLASSIC, Difficulty.NORMAL, List.of(), 0, List.of());
    }
}
