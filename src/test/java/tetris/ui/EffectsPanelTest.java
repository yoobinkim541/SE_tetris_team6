package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.item.ActiveEffect;
import tetris.feature.item.ItemType;
import tetris.feature.setting.Settings;

/** 정보 영역의 지속 효과 섹션(노란색 이름·설명·남은 블록 수·10칸 게이지)을 화면 크기별로 그려 확인한다 */
public class EffectsPanelTest {
    private static final int GUIDE_LINES = 7; // KEYS 제목 + 안내 6줄

    private static BufferedImage render(int cell, List<ActiveEffect> effects) {
        int[][] cells = new int[20][10];
        GamePanel panel = new GamePanel(new Settings(), new PausePanel(() -> {}, () -> {}, () -> {}), s -> {});
        panel.setCellSize(cell);
        panel.setSize(GamePanel.preferredSizeFor(cell));
        panel.onChanged(new GameSnapshot(cells, null, null, 1234, 3, 12, GameState.PLAYING, GameMode.ITEM,
                Difficulty.NORMAL, List.of(), 8, effects));
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();
        return image;
    }

    private static BufferedImage[] onEdt(int cell, List<ActiveEffect> effects) throws Exception {
        BufferedImage[] result = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> result[0] = render(cell, effects));
        return result;
    }

    private static java.awt.Rectangle effects(int cell) {
        GamePanel panel = new GamePanel(new Settings(), new PausePanel(() -> {}, () -> {}, () -> {}), s -> {});
        panel.setCellSize(cell);
        return panel.effectsBounds();
    }

    private static java.awt.Rectangle guide(int cell) {
        GamePanel panel = new GamePanel(new Settings(), new PausePanel(() -> {}, () -> {}, () -> {}), s -> {});
        panel.setCellSize(cell);
        return panel.keyGuideBounds();
    }

    // 글자는 안티앨리어싱으로 색이 섞이므로 color와 가까운 픽셀(채널별 차이 합 60 이하)을 센다
    private static int countColor(BufferedImage image, int x, int y, int w, int h, Color color) {
        int count = 0;
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) {
                Color p = new Color(image.getRGB(xx, yy));
                int diff = Math.abs(p.getRed() - color.getRed()) + Math.abs(p.getGreen() - color.getGreen())
                        + Math.abs(p.getBlue() - color.getBlue());
                if (diff <= 60) count++;
            }
        return count;
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 40})
    void 지속_효과는_노란색_섹션과_게이지로_조작_안내_위에_그린다(int cell) throws Exception {
        BufferedImage none = onEdt(cell, List.of())[0];
        BufferedImage both = onEdt(cell, List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 7), new ActiveEffect(ItemType.SLOW, 9)))[0];
        java.awt.Rectangle fx = effects(cell);
        java.awt.Rectangle keys = guide(cell);

        assertEquals(0, countColor(none, fx.x, fx.y, fx.width, fx.height, Theme.ACCENT), "효과가 없으면 노란색이 없어야 함");
        assertTrue(countColor(both, fx.x, fx.y, fx.width, fx.height, Theme.ACCENT) > cell * 16, "노란 게이지가 보여야 함");
        assertArrayEquals(none.getRGB(keys.x, keys.y, keys.width, keys.height, null, 0, keys.width),
                both.getRGB(keys.x, keys.y, keys.width, keys.height, null, 0, keys.width), "지속 효과가 조작 안내를 덮으면 안 됨");
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 40})
    void 게이지는_남은_블록_수만큼_채운다(int cell) throws Exception {
        BufferedImage seven = onEdt(cell, List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 7)))[0];
        BufferedImage three = onEdt(cell, List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 3)))[0];
        java.awt.Rectangle fx = effects(cell);

        assertTrue(countColor(seven, fx.x, fx.y, fx.width, fx.height, Theme.ACCENT) > countColor(three, fx.x, fx.y, fx.width, fx.height, Theme.ACCENT));
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 20})
    void 작은_화면에서는_효과_줄만_노란색으로_그린다(int cell) throws Exception {
        BufferedImage none = onEdt(cell, List.of())[0];
        BufferedImage one = onEdt(cell, List.of(new ActiveEffect(ItemType.SLOW, 4)))[0];
        java.awt.Rectangle fx = effects(cell);

        assertEquals(0, countColor(none, fx.x, fx.y, fx.width, fx.height, Theme.ACCENT));
        assertTrue(countColor(one, fx.x, fx.y, fx.width, fx.height, Theme.ACCENT) > 0, "작은 화면에서도 효과 줄이 노란색이어야 함");
    }
}
