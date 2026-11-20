package tetris.feature.game;

import java.util.List;
import java.util.Map;
import tetris.component.block.Block;
import tetris.feature.item.ActiveEffect;
import tetris.feature.item.Cell;
import tetris.feature.item.ItemType;
import tetris.feature.rule.TSpinType;

/**
 * UI가 읽는 게임 상태 사본. 보드 배열·블록은 복사본이라 수정해도 게임에 영향이 없다.
 * 떨어지는 블록·Next의 아이템 칸은 블록 복사본(Block.itemCellAt)이 들고 있고,
 * 이미 고정된 칸에 남은 아이템 문자(D·S 등)는 lockedItems에 있다.
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
        List<ActiveEffect> effects,     // 켜져 있는 지속 효과와 남은 블록 수 (MOD-4)
        Map<Cell, ItemType> lockedItems,   // 고정된 칸에 남은 아이템 문자. 키는 보이는 영역의 논리 좌표
        TSpinType lastTSpin,               // 가장 최근 Lock의 T-스핀 판정 (TSP-4)
        Block holdBlock,                   // 홀드 칸. 비어 있으면 null (HLD-1)
        List<Block> nextBlocks,            // Next 미리보기. 맨 앞이 nextBlock과 같다 (NXT-2)
        Block ghostBlock) {                // 착지 미리보기. 없거나 이미 그 자리면 null (GST-1)

    public GameSnapshot {
        clearingCells = List.copyOf(clearingCells);
        effects = List.copyOf(effects);
        lockedItems = Map.copyOf(lockedItems);
        if (lastTSpin == null) lastTSpin = TSpinType.NONE;
        nextBlocks = nextBlocks == null ? List.of() : List.copyOf(nextBlocks);
    }

    /** T-스핀 정보가 없는 스냅샷 */
    public GameSnapshot(int[][] cells, Block currentBlock, Block nextBlock, long score, int level, int lines, GameState state,
                        GameMode mode, Difficulty difficulty, List<Cell> clearingCells, int nextItemLines, List<ActiveEffect> effects,
                        Map<Cell, ItemType> lockedItems) {
        this(cells, currentBlock, nextBlock, score, level, lines, state, mode, difficulty, clearingCells, nextItemLines, effects,
                lockedItems, TSpinType.NONE, null, nextBlock == null ? List.of() : List.of(nextBlock), null);
    }

    /** 홀드·고스트·Next 목록까지 담은 스냅샷 */
    public GameSnapshot(int[][] cells, Block currentBlock, Block nextBlock, long score, int level, int lines, GameState state,
                        GameMode mode, Difficulty difficulty, List<Cell> clearingCells, int nextItemLines, List<ActiveEffect> effects,
                        Map<Cell, ItemType> lockedItems, TSpinType lastTSpin) {
        this(cells, currentBlock, nextBlock, score, level, lines, state, mode, difficulty, clearingCells, nextItemLines, effects,
                lockedItems, lastTSpin, null, nextBlock == null ? List.of() : List.of(nextBlock), null);
    }

    /** 고정된 칸에 남은 아이템 문자가 없는 스냅샷 */
    public GameSnapshot(int[][] cells, Block currentBlock, Block nextBlock, long score, int level, int lines, GameState state,
                        GameMode mode, Difficulty difficulty, List<Cell> clearingCells, int nextItemLines, List<ActiveEffect> effects) {
        this(cells, currentBlock, nextBlock, score, level, lines, state, mode, difficulty, clearingCells, nextItemLines, effects, Map.of());
    }

    /** 일반 모드·NORMAL, 애니메이션과 아이템 정보가 없는 스냅샷 */
    public GameSnapshot(int[][] cells, Block currentBlock, Block nextBlock, long score, int level, int lines, GameState state) {
        this(cells, currentBlock, nextBlock, score, level, lines, state,
                GameMode.CLASSIC, Difficulty.NORMAL, List.of(), 0, List.of());
    }
}
