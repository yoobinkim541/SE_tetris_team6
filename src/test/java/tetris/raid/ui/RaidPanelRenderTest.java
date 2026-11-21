package tetris.raid.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.setting.ScreenSize;
import tetris.ui.GamePanel;
import tetris.ui.raid.RaidPanel;
import tetris.ui.raid.RaidSamples;

/** 세 화면 크기에서 모든 단계가 예외 없이 그려지고, 보스 영역은 단색 배경이 아니다 */
@Timeout(30)
class RaidPanelRenderTest {
    @Test
    void everyPhaseRendersAtThreeSizesWithBossPixels() {
        for (boolean colorBlind : new boolean[] {false, true}) {
            for (RaidMode mode : RaidMode.values()) {
                for (RaidPhase phase : RaidPhase.values()) {
                    for (ScreenSize size : ScreenSize.values()) {
                        RaidPanel panel = new RaidPanel(colorBlind);
                        panel.setSnapshot(RaidSamples.phase(mode, phase));
                        panel.setGuide("P1 A D 이동 · R 필살");
                        Dimension box = GamePanel.preferredSizeFor(size.cellPixels());
                        BufferedImage image = render(panel, box.width, box.height);
                        var boss = panel.bossBounds();
                        assertTrue(boss.width > 8 && boss.height > 8, size + " " + mode + " " + phase);
                        assertTrue(boss.x >= 0 && boss.y >= 0 && boss.x + boss.width <= image.getWidth()
                                && boss.y + boss.height <= image.getHeight(), "boss clipped " + boss + " in " + box);
                        assertTrue(uniqueColors(image, boss.x, boss.y, boss.width, boss.height) > 8,
                                "boss area flat " + mode + " " + phase + " " + size);
                    }
                }
            }
        }
        RaidPanel split = new RaidPanel(true);
        split.setSnapshot(RaidSamples.snapshot(RaidMode.COOP, RaidPhase.PLAYING, BossId.JELLY_KING, 5, 80, 200,
                PatternId.SPLIT, -1, 4, new int[] {40, 40}, -1, 1, 10_000L, 100, null, java.util.List.of(), null));
        render(split, GamePanel.preferredSizeFor(ScreenSize.MEDIUM.cellPixels()).width,
                GamePanel.preferredSizeFor(ScreenSize.MEDIUM.cellPixels()).height);
        split.removeNotify();
    }

    private static BufferedImage render(RaidPanel panel, int width, int height) {
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
            panel.removeNotify();
        }
        return image;
    }

    private static int uniqueColors(BufferedImage image, int x, int y, int width, int height) {
        Set<Integer> colors = new HashSet<>();
        int step = Math.max(1, Math.min(width, height) / 24);
        for (int row = y; row < y + height; row += step) {
            for (int col = x; col < x + width; col += step) {
                colors.add(image.getRGB(col, row) & 0x00ffffff);
            }
        }
        return colors.size();
    }
}
