package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
 * 보스 레이드 메뉴. MainFrame은 화면 없는 테스트에서 만들 수 없어서,
 * 프레임이 쓰는 메뉴 목록을 그대로 눌러 솔로·협동·돌아가기를 확인한다.
 */
@Timeout(30)
class RaidMenuNavigationTest {
    @Test
    void raidMenuReachesSoloCoopAndBackWithoutTouchingScores() throws Exception {
        List<String> log = new ArrayList<>();
        MainMenuPanel main = new MainMenuPanel(MainFrame.mainMenuItems(
                () -> log.add("start"), () -> log.add("item"), () -> log.add("settings"),
                () -> log.add("scores"), () -> log.add("exit"), () -> log.add("battle"),
                () -> log.add("raid")));
        assertEquals("대전", mainItems().get(5).label(), "기존 대전 메뉴 위치는 그대로다");
        assertEquals("보스 레이드", mainItems().get(6).label());
        choose(main, 6);
        assertEquals(List.of("raid"), log);

        MainMenuPanel raid = new MainMenuPanel(MainFrame.raidMenuItems(
                () -> log.add("solo"), () -> log.add("coop"), () -> log.add("back")));
        assertEquals(List.of("솔로 레이드", "협동 레이드 (2인)", "메인 메뉴"),
                raidItems().stream().map(MenuItem::label).toList());
        choose(raid, 0);
        choose(raid, 1);
        choose(raid, 2);
        assertEquals(List.of("raid", "solo", "coop", "back"), log);

        RaidConfig config = MainFrame.raidConfig(RaidMode.COOP, ControlStyle.MODERN, 42L);
        assertEquals(RaidMode.COOP, config.mode());
        assertEquals(42L, config.seed());
        SwingUtilities.invokeAndWait(() -> {
            var client = MainFrame.openRaid(new RaidConfig(RaidMode.SOLO, 7L, ControlStyle.CLASSIC));
            assertInstanceOf(LocalRaidClient.class, client);
            client.close();
        });
    }

    private static List<MenuItem> mainItems() {
        return MainFrame.mainMenuItems(() -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
    }

    private static List<MenuItem> raidItems() {
        return MainFrame.raidMenuItems(() -> { }, () -> { }, () -> { });
    }

    private static void choose(MainMenuPanel menu, int index) {
        while (menu.getSelectedIndex() != index) menu.moveSelection(1);
        menu.activateSelected();
    }
}
