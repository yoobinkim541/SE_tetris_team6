package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.PlayerId;
import tetris.ui.versus.BattleKeyMap;
import tetris.ui.versus.BattlePanel;
import tetris.ui.versus.FakeBattleClient;

/** S12-B1: 경기 화면의 키 안내를 보드 옆에 읽을 수 있는 크기로, 방향키는 화살표로 쓴다 (R3-DEMO) */
class BattleKeyGuideTest {
    @Test
    void arrowKeysAreShownAsArrowsAndRemappedKeysByName() {
        assertEquals("←", BattleKeyMap.keyLabel(KeyEvent.VK_LEFT));
        assertEquals("↓", BattleKeyMap.keyLabel(KeyEvent.VK_DOWN));
        assertEquals(KeyEvent.getKeyText(KeyEvent.VK_SPACE), BattleKeyMap.keyLabel(KeyEvent.VK_SPACE));
        var keys = new BattleKeyMap();
        assertEquals(List.of("← →  이동", "↑  회전", "↓  내리기", KeyEvent.getKeyText(KeyEvent.VK_SPACE) + "  즉시 낙하"),
                keys.playerLines(PlayerId.ONE));
        assertEquals("A D  이동", keys.playerLines(PlayerId.TWO).getFirst());
        assertTrue(keys.assign(PlayerId.TWO, GameAction.ROTATE, KeyEvent.VK_Z));
        assertEquals("Z  회전", keys.playerLines(PlayerId.TWO).get(1));
        assertEquals("일시정지 P · 나가기 Q", keys.sharedLine());
        assertEquals("일시정지 P · 기권 Q 두 번", BattleKeyMap.online().sharedLine());
        assertFalse(keys.guide().contains("Left"), "안내에 영어 방향키 이름을 쓰지 않는다");
    }

    @Test
    void localAndOnlineScreensPaintTheKeysBesideTheBoards() throws Exception {
        Object[][] cases = {{new BattleKeyMap(), null, "local"}, {BattleKeyMap.online(), PlayerId.TWO, "client"}};
        for (Object[] c : cases) {
            var keys = (BattleKeyMap) c[0];
            var panel = new BattlePanel(false, keys.guide(), "일반 대전", BattleMode.CLASSIC, (PlayerId) c[1]);
            panel.setKeys(keys);
            assertSame(keys, panel.keys());
            panel.setSize(960, 720);
            panel.setSnapshot(FakeBattleClient.sample());
            var image = new BufferedImage(960, 720, BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            try { panel.paint(g); } finally { g.dispose(); }
            assertEquals(1, panel.paintLog().entries().size(), "키 안내가 있어도 그리기 기록을 남긴다");
            Path out = Path.of("build/verification/s12-b1-battle-" + c[2] + ".png");
            Files.createDirectories(out.getParent());
            ImageIO.write(image, "png", out.toFile());
        }
    }
}
