package tetris.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    private static GamePanel probe(int cell) {
        GamePanel panel = new GamePanel(new Settings(), new PausePanel(() -> {}, () -> {}, () -> {}), s -> {});
        panel.setCellSize(cell);
        return panel;
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 13, 14, 15, 16, 17, 18, 19, 20})
    void bothEffectCountsRemainVisibleAboveUnchangedKeyGuide(int cell) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage none = render(cell, List.of());
            BufferedImage doubleScore = render(cell, List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 7)));
            BufferedImage both = render(cell, List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 7), new ActiveEffect(ItemType.SLOW, 3)));
            Rectangle fx = probe(cell).effectsBounds();
            Rectangle guide = probe(cell).keyGuideBounds();
            assertFalse(Arrays.equals(none.getRGB(fx.x, fx.y, fx.width, fx.height, null, 0, fx.width),
                    doubleScore.getRGB(fx.x, fx.y, fx.width, fx.height, null, 0, fx.width)), "x2 잔여 수가 안내 위에 보여야 함");
            assertFalse(Arrays.equals(doubleScore.getRGB(fx.x, fx.y, fx.width, fx.height, null, 0, fx.width),
                    both.getRGB(fx.x, fx.y, fx.width, fx.height, null, 0, fx.width)), "SLOW 잔여 수가 안내 위에 보여야 함");
            assertArrayEquals(none.getRGB(guide.x, guide.y, guide.width, guide.height, null, 0, guide.width),
                    both.getRGB(guide.x, guide.y, guide.width, guide.height, null, 0, guide.width),
                    "효과 표시가 조작 안내를 덮으면 안 됨");
        });
    }
}
