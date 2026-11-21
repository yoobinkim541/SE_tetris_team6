package tetris.raid.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.raid.api.RaidMode;
import tetris.ui.GamePanel;
import tetris.ui.fx.A11yColors;
import tetris.ui.raid.RaidPanel;
import tetris.ui.raid.RaidSamples;

/** 색맹 레이드 화면은 대상·럭키를 색만이 아니라 글자·별로 그린다 */
@Timeout(30)
class ColorBlindCueTest {
    @Test
    void colorBlindRaidDrawsTargetLabelAndLuckyMark() {
        RaidPanel panel = new RaidPanel(true);
        panel.setSnapshot(RaidSamples.playing(RaidMode.COOP));
        var box = GamePanel.preferredSizeFor(30);
        BufferedImage image = render(panel, box.width, box.height);

        Rectangle target = panel.cueBounds("target");
        assertTrue(target.width > 8 && target.height > 4, "target label bounds " + target);
        int orange = A11yColors.ORANGE.getRGB() & 0xffffff;
        int targetInk = count(image, target, orange);
        int targetOther = countOther(image, target, orange);
        assertTrue(targetInk > 4, "▼ 대상 글자가 주황으로 찍혀야 한다");
        assertTrue(targetOther > 0, "글자는 칸을 통째로 칠하지 않는다");

        Rectangle lucky = panel.cueBounds("lucky");
        assertTrue(lucky.width > 2, "lucky mark bounds " + lucky);
        int black = count(image, lucky, 0);
        assertTrue(black > 3, "럭키 칸에 검은 별·대각선이 있다 black=" + black + " bounds=" + lucky);
        assertTrue(countOther(image, lucky, 0) > 0, "별은 칸 전체를 덮지 않는다");

        Rectangle score = panel.cueBounds("score");
        assertTrue(countLight(image, score) > 8, "보드에 점수가 글자로 보인다");
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

    private static int count(BufferedImage image, Rectangle area, int rgb) {
        int found = 0;
        int x0 = Math.max(0, area.x);
        int y0 = Math.max(0, area.y);
        int x1 = Math.min(image.getWidth(), area.x + area.width);
        int y1 = Math.min(image.getHeight(), area.y + area.height);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((image.getRGB(x, y) & 0xffffff) == rgb) found++;
            }
        }
        return found;
    }

    private static int countOther(BufferedImage image, Rectangle area, int rgb) {
        int found = 0;
        int x0 = Math.max(0, area.x);
        int y0 = Math.max(0, area.y);
        int x1 = Math.min(image.getWidth(), area.x + area.width);
        int y1 = Math.min(image.getHeight(), area.y + area.height);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((image.getRGB(x, y) & 0xffffff) != rgb) found++;
            }
        }
        return found;
    }

    private static int countLight(BufferedImage image, Rectangle area) {
        int found = 0;
        int x0 = Math.max(0, area.x);
        int y0 = Math.max(0, area.y);
        int x1 = Math.min(image.getWidth(), area.x + area.width);
        int y1 = Math.min(image.getHeight(), area.y + area.height);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int pixel = image.getRGB(x, y);
                int luma = ((pixel >> 16) & 255) + ((pixel >> 8) & 255) + (pixel & 255);
                if (luma > 500) found++;
            }
        }
        return found;
    }
}
