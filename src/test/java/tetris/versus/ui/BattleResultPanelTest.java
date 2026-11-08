package tetris.versus.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import tetris.feature.versus.api.*;
import tetris.ui.versus.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleResultPanelTest {
    @Test void displaysStablePlayersBothScoresAndAllReasons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new BattleResultPanel(() -> {}, () -> {});
            for (var reason : MatchResult.EndReason.values()) {
                for (var winner : PlayerId.values()) {
                    panel.setSnapshot(finished(MatchResult.win(winner, reason)));
                    assertTrue(panel.getSummaryText().contains(winner == PlayerId.ONE ? "P1 승리" : "P2 승리"));
                    assertTrue(panel.getSummaryText().contains(switch (reason) {
                        case TOPPED_OUT -> "블록 쌓임";
                        case TIME_UP -> "시간 종료";
                        case FORFEIT -> "기권";
                    }));
                    assertEquals("P1: 1200   P2: 2400", panel.getScoresText());
                }
            }
            panel.setSnapshot(finished(MatchResult.draw(MatchResult.EndReason.TIME_UP)));
            assertTrue(panel.getSummaryText().contains("무승부"));
            assertThrows(IllegalArgumentException.class, () -> panel.setSnapshot(FakeBattleClient.sample()));
        });
    }

    @Test void buttonsAndWindowLevelKeysInvokeOnlyNavigationCallbacks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var modes = new AtomicInteger(); var main = new AtomicInteger();
            var panel = new BattleResultPanel(modes::incrementAndGet, main::incrementAndGet);
            panel.setSnapshot(finished(MatchResult.win(PlayerId.ONE, MatchResult.EndReason.FORFEIT)));
            button(panel, "모드 선택").doClick(); button(panel, "메인 메뉴").doClick();
            key(panel, KeyEvent.VK_ENTER); key(panel, KeyEvent.VK_ESCAPE);
            assertEquals(2, modes.get()); assertEquals(2, main.get());
            assertEquals(0, panel.getInputMap(JComponent.WHEN_FOCUSED).size());
            assertEquals(0, panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).size());
        });
    }
    private static MatchSnapshot finished(MatchResult result) {
        var sample = FakeBattleClient.sample();
        return new MatchSnapshot(sample.matchId(), 1, sample.one(), sample.two(), 0, MatchPhase.FINISHED, result);
    }
    private static JButton button(Container parent, String text) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton b && b.getText().equals(text)) return b;
            if (c instanceof Container nested) { var found = button(nested, text); if (found != null) return found; }
        }
        return null;
    }
    private static void key(JComponent panel, int code) {
        var binding = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(code, 0));
        assertNotNull(binding);
        panel.getActionMap().get(binding).actionPerformed(new ActionEvent(panel, 0, "key"));
    }
}
