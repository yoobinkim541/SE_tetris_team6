package tetris.ui.raid;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import tetris.ui.fx.A11yColors;
import tetris.ui.fx.StarfieldBackground;

/** 보드에 잘린 부드러운 안개. 덩어리는 한 번만 만들고, 연출이 낮으면 멈춘다 */
final class CloudFog {
    private BufferedImage small;
    private BufferedImage large;
    private int frame;

    void tick() {
        frame++;
    }

    void paint(Graphics2D g, Rectangle play, int[] area, int cell, boolean colorBlind) {
        if (area == null || area.length < 4 || cell <= 0) return;
        int x = play.x + area[1] * cell;
        int y = play.y + area[0] * cell;
        int w = Math.max(cell, area[3] * cell);
        int h = Math.max(cell, area[2] * cell);
        ensure();
        boolean drift = !StarfieldBackground.lowEffects();
        int shift = drift ? (int) (Math.sin(frame * 0.05) * Math.max(2, w / 6)) : 0;
        int lift = drift ? (int) (Math.cos(frame * 0.04) * Math.max(1, h / 8)) : 0;
        Graphics2D layer = (Graphics2D) g.create();
        try {
            layer.clipRect(play.x, play.y, play.width, play.height);
            layer.clipRect(x, y, w, h);
            layer.drawImage(large, x + shift - w / 5, y + lift - h / 6, w + w / 3, h + h / 4, null);
            layer.drawImage(small, x + w / 3 - shift, y + h / 4, w / 2, h / 2, null);
            if (colorBlind) {
                layer.setColor(A11yColors.WHITE);
                layer.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        1f, new float[] {4f, 3f}, 0f));
                layer.drawRoundRect(x + 1, y + 1, Math.max(1, w - 3), Math.max(1, h - 3), 8, 8);
                layer.setStroke(new BasicStroke(1f));
                layer.setColor(new Color(255, 255, 255, 150));
                for (int i = 0; i < w; i += 7) layer.drawLine(x + i, y + 2, x + i, y + h - 2);
            }
        } finally {
            layer.dispose();
        }
    }

    private void ensure() {
        if (large != null) return;
        large = blob(96, new Color(210, 236, 255, 150), new Color(170, 210, 255, 0));
        small = blob(64, new Color(255, 255, 255, 120), new Color(200, 230, 255, 0));
    }

    private static BufferedImage blob(int size, Color center, Color edge) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setPaint(new RadialGradientPaint(size / 2f, size / 2f, size / 2f, new float[] {0f, 1f},
                    new Color[] {center, edge}));
            g.fillOval(0, 0, size, size);
        } finally {
            g.dispose();
        }
        return image;
    }
}
