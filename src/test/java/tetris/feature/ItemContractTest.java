package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tetris.component.block.BlockType;
import tetris.component.block.OBlock;
import tetris.feature.data.Board;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.item.Cell;
import tetris.feature.item.ItemResult;
import tetris.feature.item.ItemType;
import tetris.feature.score.ScoreEntry;
import tetris.feature.setting.Settings;

/** 2차 구현 전에 합의한 접점(Difficulty, GameMode, 스냅샷 필드, 아이템 인터페이스)의 기본값을 고정한다 */
class ItemContractTest {

    @Test
    void difficultyValuesFollowSpec() {
        assertEquals(12, Difficulty.EASY.iWeight());
        assertEquals(10, Difficulty.NORMAL.iWeight());
        assertEquals(8, Difficulty.HARD.iWeight());
        assertEquals(80, Difficulty.EASY.levelStepMillis());
        assertEquals(100, Difficulty.NORMAL.levelStepMillis());
        assertEquals(120, Difficulty.HARD.levelStepMillis());
        assertEquals(400, Difficulty.EASY.minIntervalMillis());
        assertEquals(300, Difficulty.NORMAL.minIntervalMillis());
        assertEquals(250, Difficulty.HARD.minIntervalMillis());
    }

    @Test
    void difficultyWeightsCoverAllBlockTypesAndOnlyIDiffers() {
        var weights = Difficulty.HARD.weights();
        assertEquals(BlockType.ordinary().length, weights.size());
        assertEquals(8, weights.get(BlockType.I));
        for (BlockType type : BlockType.ordinary()) {
            if (type != BlockType.I) assertEquals(10, weights.get(type));
        }
    }

    @Test
    void sessionDefaultsToClassicNormalAndRejectsNull() {
        GameSession session = GameTestSupport.startedSession(new OBlock());
        assertEquals(GameMode.CLASSIC, session.getMode());
        assertEquals(Difficulty.NORMAL, session.getDifficulty());
        GameSnapshot snapshot = session.snapshot();
        assertEquals(GameMode.CLASSIC, snapshot.mode());
        assertEquals(Difficulty.NORMAL, snapshot.difficulty());
        assertTrue(snapshot.clearingCells().isEmpty());
        assertTrue(snapshot.effects().isEmpty());
        assertEquals(0, snapshot.nextItemLines());

        assertThrows(IllegalArgumentException.class,
                () -> new GameSession(null, Difficulty.NORMAL, () -> new OBlock(), null, null));
    }

    @Test
    void itemSessionUsesNormalDifficulty() {
        GameSession session = new GameSession(GameMode.ITEM, Difficulty.HARD,
                GameTestSupport.sequence(new OBlock()), new GameTestSupport.RecordingClock(), new GameTestSupport.RecordingListener());
        assertEquals(GameMode.ITEM, session.getMode());
        assertEquals(Difficulty.NORMAL, session.getDifficulty());
    }

    @Test
    void ordinaryBlockHasNoItemCell() {
        OBlock block = new OBlock();
        for (int r = 0; r < block.getShape().length; r++)
            for (int c = 0; c < block.getShape()[r].length; c++)
                assertTrue(block.itemCellAt(r, c).isEmpty());
    }

    @Test
    void itemSymbolsAreDistinct() {
        long distinct = java.util.Arrays.stream(ItemType.values()).map(ItemType::symbol).distinct().count();
        assertEquals(ItemType.values().length, distinct);
        assertEquals('L', ItemType.LINE_CLEAR.symbol());
    }

    @Test
    void itemResultIsImmutableAndNoneIsEmpty() {
        assertTrue(ItemResult.none().rows().isEmpty());
        assertTrue(ItemResult.none().cells().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> ItemResult.none().cells().add(new Cell(0, 0)));
    }

    @Test
    void boardViewReportsEmptyCellsIncludingHiddenRows() {
        Board board = new Board();
        assertTrue(board.isEmpty(-2, 0));
        assertTrue(board.isEmpty(19, 9));
        assertEquals(false, board.isEmpty(20, 0));  // 바닥 벽
        assertEquals(false, board.isEmpty(0, -1));  // 옆 벽
        assertEquals(false, board.isEmpty(-3, 0));  // 숨은 줄 밖
    }

    @Test
    void settingsAndScoreEntryDefaultToNormalClassic() {
        Settings settings = new Settings();
        assertEquals(Difficulty.NORMAL, settings.getDifficulty());
        settings.setDifficulty(Difficulty.HARD);
        settings.resetToDefaults();
        assertEquals(Difficulty.NORMAL, settings.getDifficulty());
        assertThrows(IllegalArgumentException.class, () -> settings.setDifficulty(null));

        ScoreEntry entry = new ScoreEntry("A", 1, 1, 0, Instant.EPOCH);
        assertEquals(GameMode.CLASSIC, entry.mode());
        assertEquals(Difficulty.NORMAL, entry.difficulty());
        assertThrows(IllegalArgumentException.class,
                () -> new ScoreEntry("A", 1, 1, 0, null, Difficulty.NORMAL, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> new ScoreEntry("A", 1, 1, 0, GameMode.ITEM, null, Instant.EPOCH));
    }
}
