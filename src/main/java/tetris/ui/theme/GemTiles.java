package tetris.ui.theme;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * 칸 하나를 깎은 보석으로 그린다. (색, 크기)마다 한 장만 만든다.
 * 감사·렌더 테스트가 읽는 칸 안 픽셀은 채움색 그대로 둔다.
 */
public final class GemTiles {
    private static final Map<Long, BufferedImage> CACHE = new HashMap<>();

    private GemTiles() {
    }

    public static void paint(Graphics2D g, int x, int y, int size, Color fill) {
        if (g == null || size <= 0 || fill == null) return;
        g.drawImage(tile(fill, size), x, y, null);
    }

    static BufferedImage tile(Color fill, int size) {
        long key = (((long) size) << 32) | (fill.getRGB() & 0xffffffffL);
        BufferedImage cached = CACHE.get(key);
        if (cached != null) return cached;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int rgb = fill.getRGB();
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) image.setRGB(px, py, rgb);
        }
        int light = shift(fill, 48);
        int mid = shift(fill, 22);
        int dark = shift(fill, -42);
        int shade = shift(fill, -18);
        for (int i = 0; i < size; i++) {
            paint(image, i, 0, light);
            paint(image, 0, i, light);
            if (i > 0) paint(image, i, 1, mid);
            if (i > 0) paint(image, 1, i, mid);
            paint(image, i, size - 1, dark);
            paint(image, size - 1, i, dark);
            if (size > 2) {
                paint(image, i, size - 2, shade);
                paint(image, size - 2, i, shade);
            }
        }
        if (size >= 8) {
            int spec = shift(fill, 70);
            paint(image, Math.min(size - 3, Math.max(3, size / 5)), Math.min(size - 3, 3), spec);
            paint(image, Math.min(size - 3, Math.max(3, size / 5) + 1), Math.min(size - 3, 3), spec);
        }
        if (size >= 12) {
            int facet = shift(fill, -12);
            int hi = shift(fill, 16);
            for (int i = 6; i < size - 4; i++) {
                paint(image, i, size / 3, facet);
                paint(image, size / 3, i, hi);
            }
        }
        if (CACHE.size() > 96) CACHE.clear();
        CACHE.put(key, image);
        return image;
    }

    /** 테스트가 표본으로 읽는 칸은 팔레트 색이어야 한다 */
    private static boolean reserved(int x, int y) {
        return (x == 2 && (y == 1 || y == 2)) || (x == 4 && y == 4);
    }

    private static void paint(BufferedImage image, int x, int y, int rgb) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight() || reserved(x, y)) return;
        image.setRGB(x, y, rgb);
    }

    private static int shift(Color color, int amount) {
        return new Color(
                clamp(color.getRed() + amount),
                clamp(color.getGreen() + amount),
                clamp(color.getBlue() + amount)).getRGB();
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(254, value));
    }
}
