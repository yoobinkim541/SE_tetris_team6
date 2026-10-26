package tetris.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tetris.feature.game.*;
import tetris.feature.item.*;
import tetris.feature.setting.Settings;

class CompactInfoLayoutTest {
    private static BufferedImage render(int cell, List<ActiveEffect> effects) {
        GamePanel panel = new GamePanel(new Settings(), new PausePanel(() -> {}, () -> {}, () -> {}), s -> {});
        panel.setCellSize(cell);
        panel.setSize(GamePanel.preferredSizeFor(cell));
        panel.onChanged(new GameSnapshot(new int[20][10], null, null, 1234, 3, 12,
                GameState.PLAYING, GameMode.ITEM, Difficulty.NORMAL, List.of(), 8, effects));
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        return image;
    }

    @ParameterizedTest
    @CsvSource({"12,183", "13,203", "14,217", "15,237", "16,244", "17,264", "18,278", "19,298", "20,305"})
    void bothEffectCountsRemainVisibleAboveUnchangedKeyGuide(int cell, int guideTop) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage none = render(cell, List.of());
            BufferedImage doubleScore = render(cell, List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 7)));
            BufferedImage both = render(cell, List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 7), new ActiveEffect(ItemType.SLOW, 3)));
            int x = cell * 10 + cell / 2 * 2;
            int width = cell * 6;
            assertFalse(Arrays.equals(none.getRGB(x, 0, width, guideTop, null, 0, width),
                    doubleScore.getRGB(x, 0, width, guideTop, null, 0, width)), "x2 잔여 수가 안내 위에 보여야 함");
            assertFalse(Arrays.equals(doubleScore.getRGB(x, 0, width, guideTop, null, 0, width),
                    both.getRGB(x, 0, width, guideTop, null, 0, width)), "SLOW 잔여 수가 안내 위에 보여야 함");
            assertArrayEquals(none.getRGB(x, guideTop, width, none.getHeight() - guideTop, null, 0, width),
                    both.getRGB(x, guideTop, width, both.getHeight() - guideTop, null, 0, width),
                    "효과 표시가 조작 안내를 덮으면 안 됨");
        });
    }
}
