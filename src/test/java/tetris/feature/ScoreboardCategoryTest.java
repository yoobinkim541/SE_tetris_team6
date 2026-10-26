package tetris.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.game.*;
import tetris.feature.score.*;

class ScoreboardCategoryTest {
    private static ScoreEntry entry(String name, long score, GameMode mode, Difficulty difficulty) {
        return new ScoreEntry(name, score, 1, 0, mode, difficulty, Instant.EPOCH);
    }

    @Test
    void fullCategoryDoesNotRejectAnotherCategoryAndTiesStayBehindExistingRecords() {
        Scoreboard board = new Scoreboard(List.of());
        for (int i = 0; i < 10; i++) board.add(entry("old" + i, 100, GameMode.CLASSIC, Difficulty.HARD));
        assertFalse(board.qualifies(100, GameMode.CLASSIC, Difficulty.HARD));
        assertEquals(-1, board.add(entry("tie", 100, GameMode.CLASSIC, Difficulty.HARD)));
        assertTrue(board.qualifies(1, GameMode.ITEM, Difficulty.NORMAL));
        assertEquals(0, board.add(entry("item", 1, GameMode.ITEM, Difficulty.NORMAL)));
        assertEquals(1, board.add(entry("itemTie", 1, GameMode.ITEM, Difficulty.NORMAL)));
        assertEquals(0, board.add(entry("winner", 101, GameMode.CLASSIC, Difficulty.HARD)));
        assertEquals("old8", board.entries(GameMode.CLASSIC, Difficulty.HARD).getLast().name());
        assertEquals(12, board.entries().size());
    }

    @Test
    void itemDifficultyNormalizesAndResetClearsEveryCategory() {
        ScoreEntry item = entry("item", 1, GameMode.ITEM, Difficulty.HARD);
        assertEquals(Difficulty.NORMAL, item.difficulty());
        Scoreboard board = new Scoreboard(List.of(item, entry("easy", 2, GameMode.CLASSIC, Difficulty.EASY)));
        assertEquals(List.of(item), board.entries(GameMode.ITEM, Difficulty.HARD));
        board.clear();
        assertTrue(board.entries().isEmpty());
    }
}
