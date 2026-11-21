package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;
import tetris.ui.fx.AssetStore;
import tetris.ui.raid.RaidPanel;
import tetris.ui.raid.RaidSamples;
import tetris.ui.theme.ScreenFade;
import tetris.ui.versus.BattlePanel;
import tetris.ui.versus.FakeBattleClient;

/**
 * 전환이 끝난 뒤 배경은 그 화면이 그리는 원본의 95% 이상이어야 한다.
 * 보드·글자 위가 아니라, UI가 덮지 않는 배경 점을 비교한다.
 */
@Timeout(30)
class BackgroundBrightnessTest {
    @Test
    void uncoveredBackgroundStaysWithinFivePercentOfTheSource() {
        StringBuilder detail = new StringBuilder();
        boolean ok = true;
        ok &= check("menu", detail, menu());
        ok &= check("versus", detail, versus());
        ok &= check("raid", detail, raid());
        ok &= check("single", detail, single());
        assertTrue(ok, detail.toString());
    }

    @Test
    void fadeIsGoneAfterTheTransition() throws Exception {
        ScreenFade fade = new ScreenFade();
        fade.setSize(40, 40);
        SwingUtilities.invokeAndWait(fade::play);
        long deadline = System.currentTimeMillis() + 800;
        while (System.currentTimeMillis() < deadline) Thread.sleep(20);
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 40, 40);
        SwingUtilities.invokeAndWait(() -> fade.paint(g));
        g.dispose();
        assertTrue(luma(image.getRGB(20, 20)) >= 243, "전환 완료 후 페이드 luma=" + luma(image.getRGB(20, 20)));
    }

    private static boolean check(String name, StringBuilder detail, Sample sample) {
        double ratio = ratio(sample.source, sample.painted, sample.points);
        int[] point = sample.points.get(0);
        int x = Math.min(point[0], sample.source.getWidth() - 1);
        int y = Math.min(point[1], sample.source.getHeight() - 1);
        detail.append(name).append('=').append(String.format("%.3f", ratio))
                .append('@').append(x).append(',').append(y)
                .append(" src=").append(luma(sample.source.getRGB(x, y)))
                .append(" paint=").append(luma(sample.painted.getRGB(x, y)))
                .append(' ');
        return ratio >= 0.95;
    }

    private static Sample menu() {
        int width = 480;
        int height = 720;
        BufferedImage source = AssetStore.SHARED.cover(AssetStore.BG_MENU, width, height);
        MainMenuPanel menu = new MainMenuPanel(List.of(new MenuItem("시작", () -> { })));
        return new Sample(source, paint(menu, width, height), corners(width, height));
    }

    private static Sample versus() {
        int width = 960;
        int height = 720;
        BufferedImage source = AssetStore.SHARED.cover(AssetStore.BG_VERSUS, width, height);
        BattlePanel panel = new BattlePanel(false, "가이드", "로컬 일반", null, null);
        panel.setSnapshot(FakeBattleClient.sample());
        BufferedImage painted = paint(panel, width, height);
        panel.removeNotify();
        return new Sample(source, painted, List.of(brightest(source, 0, 120, 0, width)));
    }

    private static Sample raid() {
        int width = 600;
        int height = 720;
        BufferedImage source = AssetStore.SHARED.cover("/assets/raid/scene-raid.jpg", width, height);
        RaidPanel panel = new RaidPanel(false);
        panel.setSnapshot(RaidSamples.phase(RaidMode.SOLO, RaidPhase.PLAYING));
        BufferedImage painted = paint(panel, width, height);
        var boss = panel.bossBounds();
        panel.removeNotify();
        int x = Math.max(8, boss.x + 8);
        int y = Math.max(8, boss.y + 6);
        return new Sample(source, painted, List.of(new int[] {x, y}, new int[] {x + 12, y + 4}, new int[] {width - 16, 12}));
    }

    private static Sample single() {
        int cell = ScreenSize.MEDIUM.cellPixels();
        var size = GamePanel.preferredSizeFor(cell);
        BufferedImage source = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D sky = source.createGraphics();
        tetris.ui.theme.UiKit.paintNight(sky, size.width, size.height);
        sky.dispose();
        GamePanel panel = new GamePanel(new Settings(), new PausePanel(() -> { }, () -> { }, () -> { }), snapshot -> { });
        panel.setCellSize(cell);
        return new Sample(source, paint(panel, size.width, size.height), corners(size.width, size.height));
    }

    private static int[] brightest(BufferedImage image, int y0, int y1, int x0, int x1) {
        int best = -1;
        int[] at = {x0, y0};
        for (int y = y0; y < y1 && y < image.getHeight(); y += 2) {
            for (int x = x0; x < x1 && x < image.getWidth(); x += 2) {
                int value = luma(image.getRGB(x, y));
                if (value > best) {
                    best = value;
                    at = new int[] {x, y};
                }
            }
        }
        return at;
    }

    private static List<int[]> corners(int width, int height) {
        return List.of(new int[] {8, 8}, new int[] {width - 12, 8}, new int[] {8, height - 12}, new int[] {width - 12, height - 12});
    }

    private static double ratio(BufferedImage source, BufferedImage painted, List<int[]> points) {
        double sum = 0;
        int count = 0;
        for (int[] point : points) {
            int x = Math.min(point[0], Math.min(source.getWidth(), painted.getWidth()) - 1);
            int y = Math.min(point[1], Math.min(source.getHeight(), painted.getHeight()) - 1);
            int base = luma(source.getRGB(x, y));
            if (base < 8) continue;
            sum += luma(painted.getRGB(x, y)) / (double) base;
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private static BufferedImage paint(javax.swing.JComponent panel, int width, int height) {
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int luma(int rgb) {
        return (((rgb >> 16) & 0xff) * 3 + ((rgb >> 8) & 0xff) * 6 + (rgb & 0xff)) / 10;
    }

    private record Sample(BufferedImage source, BufferedImage painted, List<int[]> points) {
    }
}
