package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import tetris.feature.game.Difficulty;
import tetris.feature.versus.api.*;
import tetris.ui.versus.*;

/** S9-B1: 세 대전 모드의 설정·표시와 결과 화면 (R3-MODES, R3-TIME, R3-ITEM, R3-NOSCORE) */
class BattleModesUiTest {
    @Test
    void localConfigPerMode() {
        assertEquals(new MatchConfig(BattleMode.CLASSIC, Difficulty.HARD, 0),
                BattleModes.localConfig(BattleMode.CLASSIC, Difficulty.HARD));
        assertEquals(MatchConfig.of(BattleMode.ITEM), BattleModes.localConfig(BattleMode.ITEM, Difficulty.HARD),
                "item battle keeps the NORMAL item rules");
        assertEquals(MatchConfig.of(BattleMode.TIMED), BattleModes.localConfig(BattleMode.TIMED, Difficulty.EASY),
                "timed battle: 180 s, normal blocks, NORMAL (D-05)");
    }

    @Test
    void modeLabelsAndRemainingTimeText() {
        assertEquals("일반 대전", BattleModes.label(BattleMode.CLASSIC));
        assertEquals("아이템 대전", BattleModes.label(BattleMode.ITEM));
        assertEquals("시간제한 대전", BattleModes.label(BattleMode.TIMED));
        assertEquals("", BattlePanel.timeText(-1));
        assertEquals("남은 시간 3:00", BattlePanel.timeText(180_000));
        assertEquals("남은 시간 1:00", BattlePanel.timeText(59_001), "rounds up so 0:00 means time is up");
        assertEquals("남은 시간 0:01", BattlePanel.timeText(1));
        assertEquals("남은 시간 0:00", BattlePanel.timeText(0));
    }

    @Test
    void itemCellsAreDrawnWithTheirSymbol() {
        var renderer = new BoardRenderer(false);
        var plain = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        var item = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        var g1 = plain.createGraphics();
        renderer.paint(g1, new int[][]{{1}}, new int[][]{{0}}, 0, 0, 20);
        g1.dispose();
        var g2 = item.createGraphics();
        renderer.paint(g2, new int[][]{{1}}, new int[][]{{'B'}}, 0, 0, 20);
        g2.dispose();
        boolean different = false;
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) different |= plain.getRGB(x, y) != item.getRGB(x, y);
        assertTrue(different, "an item cell must look different from a plain block");
    }

    @Test
    void panelShowsModeAndCountdownAndPaintsItems() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new BattlePanel(false, new BattleKeyMap().guide(), BattleModes.label(BattleMode.TIMED));
            assertEquals("시간제한 대전", panel.modeLabel());
            var s = FakeBattleClient.sample();
            int[][] cells = s.one().cells();
            cells[19][0] = 1;
            int[][] items = new int[20][10];
            items[19][0] = 'L';
            var one = new MatchSnapshot.PlayerView(cells, s.one().next(), 10, List.of(), items);
            panel.setSnapshot(new MatchSnapshot(s.matchId(), 1, one, s.two(), 61_000, MatchPhase.PLAYING, null));
            panel.setSize(960, 720);
            var image = new BufferedImage(960, 720, BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            panel.paint(g);
            g.dispose();
            assertNotEquals(image.getRGB(0, 0), image.getRGB(28 + 13, 90 + 19 * 27 + 13));
        });
    }

    @Test
    void resultNamesTheModeAndNeverOffersScoreSaving() {
        var panel = new BattleResultPanel(() -> {}, () -> {});
        var s = FakeBattleClient.sample();
        panel.setSnapshot(new MatchSnapshot(s.matchId(), 2, s.one(), s.two(), 0, MatchPhase.FINISHED,
                MatchResult.draw(MatchResult.EndReason.TIME_UP)), BattleModes.label(BattleMode.TIMED));
        assertEquals("시간제한 대전 · 무승부 · 시간 종료", panel.getSummaryText());
        assertFalse(panel.getScoresText().isEmpty());
    }
}
