package tetris.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import tetris.feature.game.*;
import tetris.feature.score.ScoreEntry;

class ScoreboardNavigationTest {
    private static final List<ScoreEntry> RECORDS = List.of(
            new ScoreEntry("Easy", 11, 1, 0, GameMode.CLASSIC, Difficulty.EASY, Instant.EPOCH),
            new ScoreEntry("Normal", 22, 2, 0, GameMode.CLASSIC, Difficulty.NORMAL, Instant.EPOCH),
            new ScoreEntry("Hard", 33, 3, 0, GameMode.CLASSIC, Difficulty.HARD, Instant.EPOCH),
            new ScoreEntry("Item", 44, 4, 0, GameMode.ITEM, Difficulty.NORMAL, Instant.EPOCH));

    private static BufferedImage render(ScoreBoardPanel panel) {
        panel.setSize(525, 630);
        BufferedImage image = new BufferedImage(525, 630, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static void key(ScoreBoardPanel panel, int code) {
        Object name = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(code, 0));
        assertNotNull(name);
        panel.getActionMap().get(name).actionPerformed(null);
    }

    private static void assertOnlyRecordVisible(ScoreBoardPanel panel, ScoreEntry expected) {
        ScoreBoardPanel reference = new ScoreBoardPanel(() -> {}, () -> {});
        reference.show(List.of(expected), -1, false, expected.mode(), expected.difficulty());
        BufferedImage actual = render(panel), wanted = render(reference);
        // 제목만 바뀌고 실제 기록이 다른 분류에 남는 결함도 잡는다.
        assertArrayEquals(wanted.getRGB(0, 110, 525, 320, null, 0, 525),
                actual.getRGB(0, 110, 525, 320, null, 0, 525));
    }

    @Test
    void arrowKeysSelectFourCategoriesAndWrapInBothDirections() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ScoreBoardPanel panel = new ScoreBoardPanel(() -> {}, () -> {});
            panel.show(RECORDS, -1);
            assertOnlyRecordVisible(panel, RECORDS.get(1));
            key(panel, KeyEvent.VK_LEFT);
            assertOnlyRecordVisible(panel, RECORDS.get(0));
            key(panel, KeyEvent.VK_LEFT);
            assertOnlyRecordVisible(panel, RECORDS.get(3));
            key(panel, KeyEvent.VK_LEFT);
            assertOnlyRecordVisible(panel, RECORDS.get(2));
            key(panel, KeyEvent.VK_RIGHT);
            assertOnlyRecordVisible(panel, RECORDS.get(3));
            key(panel, KeyEvent.VK_RIGHT);
            assertOnlyRecordVisible(panel, RECORDS.get(0));
        });
    }

    @Test
    void resultHighlightReturnsOnlyInOriginalCategoryAndMenuReopenClearsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ScoreBoardPanel panel = new ScoreBoardPanel(() -> {}, () -> {});
            panel.show(RECORDS, 0, true, GameMode.ITEM, Difficulty.NORMAL);
            assertEquals(Theme.HIGHLIGHT_ROW.getRGB(), render(panel).getRGB(20, 125));
            key(panel, KeyEvent.VK_LEFT);
            assertNotEquals(Theme.HIGHLIGHT_ROW.getRGB(), render(panel).getRGB(20, 125));
            key(panel, KeyEvent.VK_RIGHT);
            assertEquals(Theme.HIGHLIGHT_ROW.getRGB(), render(panel).getRGB(20, 125));
            panel.show(RECORDS, -1);
            assertOnlyRecordVisible(panel, RECORDS.get(1));
            assertNotEquals(Theme.HIGHLIGHT_ROW.getRGB(), render(panel).getRGB(20, 125));
        });
    }

    @Test
    void changingCategoryPreservesExitSelectionAfterGame() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            int[] exits = {0};
            ScoreBoardPanel panel = new ScoreBoardPanel(() -> fail("Exit 선택이 소실됨"), () -> exits[0]++);
            panel.show(RECORDS, 0, true, GameMode.ITEM, Difficulty.NORMAL);
            key(panel, KeyEvent.VK_DOWN);
            key(panel, KeyEvent.VK_LEFT);
            key(panel, KeyEvent.VK_ENTER);
            assertEquals(1, exits[0]);
        });
    }
}
