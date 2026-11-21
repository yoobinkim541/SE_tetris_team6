package tetris.raid.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.setting.ControlStyle;
import tetris.feature.setting.KeyMap;
import tetris.feature.versus.api.PlayerId;
import tetris.ui.raid.FakeRaidClient;
import tetris.ui.raid.RaidPanel;
import tetris.ui.raid.RaidSamples;
import tetris.ui.raid.RaidScreenController;

/** 카드는 선택권자 키만, 필살은 R / Enter가 각자 플레이어로 간다 */
@Timeout(30)
class RaidInputRoutingTest {
    @Test
    void cardPickAndUltimateRouteToTheRightPlayer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JRootPane root = new JRootPane();
            FakeRaidClient coop = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.CARD_PICK));
            RaidPanel panel = new RaidPanel(false);
            List<String> left = new ArrayList<>();
            try (var controller = new RaidScreenController(root, coop, panel, new KeyMap(), false, ControlStyle.MODERN,
                    () -> left.add("menu"), () -> left.add("retry"))) {
                assertEquals(1, controller.cardSelection());
                key(root, KeyEvent.VK_D);
                key(root, KeyEvent.VK_F);
                assertTrue(coop.picks().isEmpty(), "P1 키는 P2 차례에 카드를 고르지 않는다");
                assertEquals(1, controller.cardSelection());
                key(root, KeyEvent.VK_RIGHT);
                key(root, KeyEvent.VK_SPACE);
                assertEquals(List.of(new FakeRaidClient.Pick(PlayerId.TWO, 2)), coop.picks());
            }

            FakeRaidClient playing = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.PLAYING));
            try (var controller = new RaidScreenController(root, playing, new RaidPanel(false), new KeyMap(), false,
                    ControlStyle.MODERN, () -> left.add("menu"), () -> left.add("retry"))) {
                key(root, KeyEvent.VK_R);
                key(root, KeyEvent.VK_ENTER);
                assertEquals(List.of(PlayerId.ONE, PlayerId.TWO), playing.ultimates());
                playing.publish(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.FINISHED));
                controller.activateResult();
            }
            assertEquals(List.of("menu"), left);

            FakeRaidClient solo = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.SOLO, RaidPhase.PLAYING));
            try (var ignored = new RaidScreenController(root, solo, new RaidPanel(false), new KeyMap(), true,
                    ControlStyle.MODERN, () -> { }, () -> { })) {
                key(root, KeyEvent.VK_ENTER);
                key(root, KeyEvent.VK_R);
                assertEquals(List.of(PlayerId.ONE), solo.ultimates(), "솔로 Enter는 P2 필살이 아니다");
            }
        });
    }

    private static void key(JRootPane root, int code) {
        Object name = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(code, 0, false));
        assertTrue(name != null, "missing binding " + code);
        var action = root.getActionMap().get(name);
        assertTrue(action != null && action.isEnabled());
        action.actionPerformed(new ActionEvent(root, 0, "test"));
    }
}
