package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.raid.LocalRaidClient;
import tetris.feature.raid.api.RaidConfig;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.setting.ControlStyle;

/**
 * 시작 메뉴는 6개다. 솔로·멀티 하위 메뉴에서 레이드와 대전에 들어가고, Esc는 시작 메뉴로 돌아간다.
 */
@Timeout(30)
class RaidMenuNavigationTest {
    @Test
    void startMenuReachesSoloRaidMultiplayerAndBackWithoutTouchingScores() throws Exception {
        List<String> log = new ArrayList<>();
        List<MenuItem> main = MainFrame.mainMenuItems(
                () -> log.add("solo-menu"), () -> log.add("item"), () -> log.add("multi"),
                () -> log.add("settings"), () -> log.add("scores"), () -> log.add("exit"));
        assertEquals(List.of("게임 시작 (솔로)", "아이템 모드", "멀티플레이", "설정", "스코어보드", "게임 종료"),
                main.stream().map(MenuItem::label).toList());
        assertEquals(6, main.size());

        MainMenuPanel start = new MainMenuPanel(main);
        choose(start, 0);
        choose(start, 2);
        assertEquals(List.of("solo-menu", "multi"), log);

        List<MenuItem> solo = MainFrame.soloMenuItems(
                () -> log.add("classic"), () -> log.add("solo-raid"), () -> log.add("solo-back"));
        assertEquals(List.of("일반 게임", "솔로 레이드", "메인 메뉴"),
                solo.stream().map(MenuItem::label).toList());
        MainMenuPanel soloPanel = new MainMenuPanel(solo, () -> log.add("esc-solo"), "솔로 플레이", () -> false);
        choose(soloPanel, 0);
        choose(soloPanel, 1);
        choose(soloPanel, 2);
        press(soloPanel, KeyEvent.VK_ESCAPE);
        assertEquals(List.of("solo-menu", "multi", "classic", "solo-raid", "solo-back", "esc-solo"), log);

        List<MenuItem> multi = MainFrame.multiMenuItems(
                () -> log.add("local"), () -> log.add("item-battle"), () -> log.add("timed"),
                () -> log.add("online"), () -> log.add("coop"), () -> log.add("multi-back"));
        assertEquals(List.of("로컬 일반 대전", "로컬 아이템 대전", "로컬 시간제한 대전",
                "온라인 대전 (LAN·인터넷)", "협동 레이드 (2인)", "메인 메뉴"),
                multi.stream().map(MenuItem::label).toList());
        MainMenuPanel multiPanel = new MainMenuPanel(multi, () -> log.add("esc-multi"), "멀티플레이", () -> false);
        choose(multiPanel, 4);
        press(multiPanel, KeyEvent.VK_ESCAPE);
        assertEquals("coop", log.get(log.size() - 2));
        assertEquals("esc-multi", log.get(log.size() - 1));

        RaidConfig config = MainFrame.raidConfig(RaidMode.COOP, ControlStyle.MODERN, 42L);
        assertEquals(RaidMode.COOP, config.mode());
        assertEquals(42L, config.seed());
        SwingUtilities.invokeAndWait(() -> {
            var client = MainFrame.openRaid(new RaidConfig(RaidMode.SOLO, 7L, ControlStyle.CLASSIC));
            assertInstanceOf(LocalRaidClient.class, client);
            client.close();
        });
    }

    private static void choose(MainMenuPanel menu, int index) {
        while (menu.getSelectedIndex() != index) menu.moveSelection(1);
        menu.activateSelected();
    }

    private static void press(MainMenuPanel menu, int key) {
        var stroke = javax.swing.KeyStroke.getKeyStroke(key, 0);
        Object name = menu.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke);
        menu.getActionMap().get(name).actionPerformed(new java.awt.event.ActionEvent(menu, 0, "esc"));
    }
}
