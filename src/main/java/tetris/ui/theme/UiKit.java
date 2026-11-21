package tetris.ui.theme;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import tetris.ui.fx.A11yColors;
import tetris.ui.fx.AssetStore;

/**
 * 크리스털 화면 부품. 그림이 있으면 9등분으로 늘리고, 없으면 같은 말투의 도형으로 그린다.
 * 크기별 그림은 한 번만 만든다.
 */
public final class UiKit {
    public static final String CROWN = "/assets/ui/board-crown.png";
    public static final String GEM = "/assets/ui/gem-tile.png";
    public static final String FRAME_CYAN = "/assets/ui/frame-cyan.png";
    public static final String FRAME_MAGENTA = "/assets/ui/frame-magenta.png";
    public static final String FRAME_VIOLET = "/assets/ui/frame-violet.png";
    public static final String TAB_CYAN = "/assets/ui/tab-cyan.png";
    public static final String TAB_MAGENTA = "/assets/ui/tab-magenta.png";
    public static final String BUTTON = "/assets/ui/button.png";
    public static final String BUTTON_SELECTED = "/assets/ui/button-selected.png";
    public static final String CAPSULE = "/assets/ui/capsule.png";
    public static final String BADGE_P1 = "/assets/ui/badge-p1.png";
    public static final String BADGE_P2 = "/assets/ui/badge-p2.png";
    public static final String BANNER = "/assets/ui/banner-warning.png";
    public static final String ISLAND_1 = "/assets/ui/island-1.png";
    public static final String ISLAND_2 = "/assets/ui/island-2.png";
    public static final String MOON = "/assets/ui/moon.png";

    public static final Color NIGHT = new Color(0x070B18);
    public static final Color NIGHT_MID = new Color(0x16122C);
    public static final Color CYAN = new Color(0x3DE0FF);
    public static final Color MAGENTA = new Color(0xFF4AD8);
    public static final Color VIOLET = new Color(0x9B6BFF);

    private static final NineSlice FRAME_C = new NineSlice(FRAME_CYAN, 64, NineSlice.Axis.NINE);
    private static final NineSlice FRAME_M = new NineSlice(FRAME_MAGENTA, 64, NineSlice.Axis.NINE);
    private static final NineSlice FRAME_V = new NineSlice(FRAME_VIOLET, 64, NineSlice.Axis.NINE);
    private static final NineSlice TAB_C = new NineSlice(TAB_CYAN, 48, NineSlice.Axis.NINE);
    private static final NineSlice TAB_M = new NineSlice(TAB_MAGENTA, 48, NineSlice.Axis.NINE);
    private static final NineSlice BUTTON_IDLE = new NineSlice(BUTTON, 64, NineSlice.Axis.HORIZONTAL);
    private static final NineSlice BUTTON_ON = new NineSlice(BUTTON_SELECTED, 64, NineSlice.Axis.HORIZONTAL);
    private static final NineSlice CAPSULE_SLICE = new NineSlice(CAPSULE, 48, NineSlice.Axis.HORIZONTAL);
    private static final NineSlice BANNER_SLICE = new NineSlice(BANNER, 48, NineSlice.Axis.HORIZONTAL);

    private static final Map<Long, BufferedImage> GEMS = new HashMap<>();
    private static final Map<String, BufferedImage> GLOWS = new HashMap<>();

    private UiKit() {
    }

    public static void paintScene(Graphics2D g, int width, int height) {
        paintNight(g, width, height);
        if (width <= 0 || height <= 0) return;
        g.setColor(new Color(230, 236, 255, 180));
        for (int i = 0; i < 40; i++) {
            int x = (i * 97 + 13) % Math.max(1, width);
            int y = (i * 53 + 7) % Math.max(1, height);
            int s = 1 + i % 2;
            g.fillRect(x, y, s, s);
        }
        int moon = Math.max(36, Math.min(width, height) / 8);
        paintMoon(g, width - moon - moon / 3, moon / 3, moon);
        int island = Math.min(72, Math.max(40, Math.min(width, height) / 8));
        paintIsland(g, 0, -island / 3, -island / 5, island);
        paintIsland(g, 1, width - island / 2, height - island / 3, island);
    }

    public static void paintNight(Graphics2D g, int width, int height) {
        if (width <= 0 || height <= 0) return;
        g.setPaint(new LinearGradientPaint(0, 0, 0, height,
                new float[] {0f, 0.55f, 1f},
                new Color[] {new Color(0x1A1038), NIGHT, new Color(0x0A1830)}));
        g.fillRect(0, 0, width, height);
        g.setPaint(null);
    }
    public static void paintMoon(Graphics2D g, int x, int y, int size) {
        BufferedImage image = AssetStore.SHARED.fit(MOON, size, size);
        if (image != null) {
            g.drawImage(image, x, y, null);
            return;
        }
        g.setColor(new Color(0xE7EEF8));
        g.fillOval(x, y, size, size);
        g.setColor(new Color(0xC5D0E0));
        g.fillOval(x + size / 5, y + size / 4, size / 5, size / 5);
        g.fillOval(x + size / 2, y + size / 3, size / 7, size / 7);
    }

    public static void paintIsland(Graphics2D g, int which, int x, int y, int size) {
        BufferedImage image = AssetStore.SHARED.fit(which == 1 ? ISLAND_2 : ISLAND_1, size, size);
        if (image != null) {
            g.drawImage(image, x, y, null);
            return;
        }
        Color crystal = which == 1 ? MAGENTA : CYAN;
        g.setColor(new Color(0x1C2438));
        int[] xs = {x, x + size / 5, x + size * 4 / 5, x + size, x + size * 3 / 4, x + size / 4};
        int[] ys = {y + size * 3 / 5, y + size / 3, y + size / 4, y + size * 3 / 5, y + size, y + size};
        g.fillPolygon(xs, ys, xs.length);
        g.setColor(crystal);
        int[] cx = {x + size / 2, x + size * 2 / 5, x + size * 3 / 5};
        int[] cy = {y + size / 8, y + size / 2, y + size / 2};
        g.fillPolygon(cx, cy, 3);
    }

    public static void paintCrown(Graphics2D g, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        BufferedImage image = AssetStore.SHARED.image(CROWN);
        if (image != null) {
            int drawW = width;
            int drawH = Math.max(1, width * image.getHeight() / image.getWidth());
            BufferedImage scaled = AssetStore.SHARED.scaled(CROWN, drawW, drawH);
            if (scaled != null) {
                var clip = g.getClip();
                g.clipRect(x, y, width, height);
                g.drawImage(scaled, x, y + height - scaled.getHeight(), null);
                g.setClip(clip);
                return;
            }
        }
        g.setColor(new Color(0x6A4A9A));
        int base = y + height - 1;
        int[] xs = {x, x + width / 6, x + width / 3, x + width / 2, x + width * 2 / 3, x + width * 5 / 6, x + width};
        int[] ys = {base, y + height / 3, base - height / 5, y, base - height / 5, y + height / 3, base};
        g.fillPolygon(xs, ys, xs.length);
        g.setColor(CYAN);
        fillDiamond(g, x + width / 2, y + 2, Math.max(3, height / 5));
        g.setColor(MAGENTA);
        fillDiamond(g, x + width / 6, y + height / 3, Math.max(2, height / 7));
        fillDiamond(g, x + width * 5 / 6, y + height / 3, Math.max(2, height / 7));
    }

    public enum Tone {
        CYAN, MAGENTA, VIOLET
    }

    public static void paintFrame(Graphics2D g, Tone tone, int x, int y, int width, int height) {
        if (width <= 2 || height <= 2) return;
        NineSlice slice = switch (tone) {
            case MAGENTA -> FRAME_M;
            case VIOLET -> FRAME_V;
            default -> FRAME_C;
        };
        if (slice.available()) {
            slice.paint(g, x, y, width, height);
            return;
        }
        Color glow = toneColor(tone);
        float stroke = Math.max(2f, Math.min(width, height) / 36f);
        g.setStroke(new BasicStroke(stroke));
        g.setColor(withAlpha(glow, 210));
        g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, Math.min(28, height / 6), Math.min(28, height / 6));
        g.setStroke(new BasicStroke(1f));
        g.setColor(withAlpha(Color.WHITE, 90));
        g.drawRoundRect(x + 3, y + 3, width - 7, height - 7, Math.min(22, height / 7), Math.min(22, height / 7));
        int gem = Math.max(4, Math.min(width, height) / 18);
        g.setColor(glow);
        fillDiamond(g, x + width / 2, y + 1, gem);
        fillDiamond(g, x + width / 2, y + height - 1, gem);
    }

    public static void paintTab(Graphics2D g, boolean cyan, int x, int y, int width, int height) {
        if (width <= 2 || height <= 2) return;
        NineSlice slice = cyan ? TAB_C : TAB_M;
        if (slice.available()) {
            slice.paint(g, x, y, width, height, tabCorner(width, height));
            return;
        }
        Color glow = cyan ? CYAN : MAGENTA;
        g.setColor(new Color(8, 12, 28, 168));
        g.fillRoundRect(x, y, width, height, 14, 14);
        g.setColor(withAlpha(glow, 200));
        g.setStroke(new BasicStroke(Math.max(1.5f, Math.min(width, height) / 24f)));
        g.drawRoundRect(x, y, width - 1, height - 1, 14, 14);
        g.setStroke(new BasicStroke(1f));
    }

    public static void paintButton(Graphics2D g, boolean selected, int x, int y, int width, int height, boolean colorBlind, float pulse) {
        if (width <= 2 || height <= 2) return;
        Color glow = colorBlind ? A11yColors.ORANGE : (selected ? CYAN : MAGENTA);
        if (selected) {
            Composite previous = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f + 0.35f * pulse));
            g.setColor(glow);
            g.fillRoundRect(x - 4, y - 3, width + 8, height + 6, height, height);
            g.setComposite(previous);
        }
        NineSlice slice = selected ? BUTTON_ON : BUTTON_IDLE;
        if (!slice.available()) slice = BUTTON_IDLE;
        if (slice.available()) {
            slice.paint(g, x, y, width, height);
            return;
        }
        g.setPaint(new LinearGradientPaint(x, y, x, y + height,
                new float[] {0f, 1f},
                new Color[] {new Color(18, 28, 58, 230), new Color(8, 10, 24, 230)}));
        g.fillRoundRect(x, y, width, height, height, height);
        g.setPaint(null);
        g.setColor(withAlpha(glow, selected ? 230 : 150));
        g.setStroke(new BasicStroke(selected ? 2.5f : 1.5f));
        g.drawRoundRect(x, y, width - 1, height - 1, height, height);
        g.setStroke(new BasicStroke(1f));
    }

    /** 선택된 알약 왼쪽의 작은 화살. pulse 0..1 */
    public static void paintArrow(Graphics2D g, int x, int y, int size, boolean colorBlind, float pulse) {
        int bob = (int) ((pulse - 0.5f) * size / 3);
        int[] xs = {x, x + size, x};
        int[] ys = {y - size / 2 + bob, y + bob, y + size / 2 + bob};
        g.setColor(colorBlind ? A11yColors.ORANGE : CYAN);
        g.fillPolygon(xs, ys, 3);
    }

    public static void paintCapsule(Graphics2D g, int x, int y, int width, int height) {
        if (width <= 2 || height <= 2) return;
        if (CAPSULE_SLICE.available()) {
            CAPSULE_SLICE.paint(g, x, y, width, height);
            return;
        }
        g.setColor(new Color(8, 14, 32, 190));
        g.fillRoundRect(x, y, width, height, height, height);
        g.setColor(withAlpha(CYAN, 180));
        g.drawRoundRect(x, y, width - 1, height - 1, height, height);
    }

    /** 이름 칸. 에셋 캡슐의 뾰족한 끝 대신 둥근 유리 알약 */
    public static void paintGlassCapsule(Graphics2D g, int x, int y, int width, int height) {
        if (width <= 2 || height <= 2) return;
        int arc = height;
        g.setColor(new Color(CYAN.getRed(), CYAN.getGreen(), CYAN.getBlue(), 70));
        g.fillRoundRect(x - 3, y - 3, width + 6, height + 6, arc + 6, arc + 6);
        g.setColor(new Color(8, 16, 36, 214));
        g.fillRoundRect(x, y, width, height, arc, arc);
        g.setStroke(new BasicStroke(1.8f));
        g.setColor(withAlpha(CYAN, 230));
        g.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 46));
        g.drawRoundRect(x + 3, y + 3, width - 7, Math.max(4, height / 2), arc, arc);
    }

    /** 탭 9등분 모서리. 가운데에 제목 띠가 남도록 줄인다 */
    public static int tabCorner(int width, int height) {
        int side = Math.min(Math.max(width, 1), Math.max(height, 1));
        return Math.max(8, Math.min(48, side / 5));
    }

    /** 제목이 모서리 장식에 닿지 않는 최소 여백 */
    public static int tabTitleInset(int width, int height) {
        int corner = tabCorner(width, height);
        int inset = Math.max(12, corner / 2 + 4);
        return Math.min(inset, Math.max(8, Math.min(width, height) / 4));
    }

    public static void paintGauge(Graphics2D g, int x, int y, int width, int height, float filled, Color fill) {
        if (width <= 2 || height <= 2) return;
        g.setColor(new Color(6, 10, 22, 200));
        g.fillRoundRect(x, y, width, height, height, height);
        g.setColor(withAlpha(VIOLET, 160));
        g.drawRoundRect(x, y, width - 1, height - 1, height, height);
        int inner = Math.max(2, height / 5);
        int span = width - inner * 2;
        int amount = Math.round(span * Math.max(0f, Math.min(1f, filled)));
        if (amount <= 0) return;
        g.setPaint(new LinearGradientPaint(x, y, x + width, y,
                new float[] {0f, 1f},
                new Color[] {CYAN, fill == null ? MAGENTA : fill}));
        g.fillRoundRect(x + inner, y + inner, amount, height - inner * 2, height, height);
        g.setPaint(null);
    }

    public static void paintBadge(Graphics2D g, boolean playerOne, int x, int y, int size) {
        BufferedImage image = AssetStore.SHARED.fit(playerOne ? BADGE_P1 : BADGE_P2, size, size);
        if (image != null) {
            g.drawImage(image, x + (size - image.getWidth()) / 2, y + (size - image.getHeight()) / 2, null);
            return;
        }
        g.setColor(playerOne ? CYAN : MAGENTA);
        if (playerOne) fillDiamond(g, x + size / 2, y + size / 2, size / 2);
        else fillStar(g, x + size / 2, y + size / 2, size / 2);
        g.setColor(NIGHT);
        g.setFont(GameTheme.digits(Math.max(8, size / 4)));
        String mark = playerOne ? "P1" : "P2";
        int tx = x + (size - g.getFontMetrics().stringWidth(mark)) / 2;
        int ty = y + (size + g.getFontMetrics().getAscent()) / 2 - 2;
        g.drawString(mark, tx, ty);
    }

    public static void paintBanner(Graphics2D g, int x, int y, int width, int height) {
        if (width <= 2 || height <= 2) return;
        if (BANNER_SLICE.available()) {
            BANNER_SLICE.paint(g, x, y, width, height);
            return;
        }
        g.setColor(new Color(80, 16, 40, 210));
        g.fillRoundRect(x, y, width, height, 16, 16);
        g.setColor(MAGENTA);
        g.drawRoundRect(x, y, width - 1, height - 1, 16, 16);
    }

    /**
     * 칸을 이미 정확한 색으로 채운 뒤에 부른다. 가장자리 4px는 건드리지 않아
     * 감사 픽셀(+4)이 팔레트 색 그대로 남는다.
     */
    public static void paintGemInset(Graphics2D g, int x, int y, int size, Color tint) {
        int inset = 5;
        if (size <= inset * 2 + 2 || tint == null) return;
        int inner = size - inset * 2;
        BufferedImage gem = tintedGem(inner, tint);
        if (gem != null) {
            Composite previous = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.42f));
            g.drawImage(gem, x + inset, y + inset, null);
            g.setComposite(previous);
            return;
        }
        Color hi = shift(tint, 36);
        Color lo = shift(tint, -28);
        g.setColor(hi);
        g.drawLine(x + inset, y + inset, x + size - inset - 1, y + inset);
        g.setColor(lo);
        g.drawLine(x + inset, y + size - inset - 1, x + size - inset - 1, y + size - inset - 1);
    }

    /** 네온 글자. 같은 문장·크기·색은 한 번만 블러한다 */
    public static void paintGlowText(Graphics2D g, String text, Font font, Color color, int centerX, int baseline) {
        if (text == null || text.isEmpty() || font == null) return;
        g.setFont(font);
        int width = g.getFontMetrics().stringWidth(text);
        int ascent = g.getFontMetrics().getAscent();
        int descent = g.getFontMetrics().getDescent();
        String key = text + "|" + font.getSize() + "|" + color.getRGB() + "|" + font.getName();
        BufferedImage cached = GLOWS.get(key);
        if (cached == null) {
            int pad = Math.max(6, font.getSize() / 3);
            int w = Math.max(1, width + pad * 2);
            int h = Math.max(1, ascent + descent + pad * 2);
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D layer = image.createGraphics();
            try {
                layer.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                layer.setFont(font);
                layer.setColor(withAlpha(color, 90));
                for (int ox = -2; ox <= 2; ox++) {
                    for (int oy = -2; oy <= 2; oy++) {
                        if (ox == 0 && oy == 0) continue;
                        layer.drawString(text, pad + ox, pad + ascent + oy);
                    }
                }
                layer.setColor(color);
                layer.drawString(text, pad, pad + ascent);
            } finally {
                layer.dispose();
            }
            if (GLOWS.size() > 64) GLOWS.clear();
            GLOWS.put(key, image);
            cached = image;
        }
        g.drawImage(cached, centerX - cached.getWidth() / 2, baseline - g.getFontMetrics().getAscent() - Math.max(6, font.getSize() / 3), null);
    }

    public static float pulse() {
        return (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 180_000_000.0));
    }

    public static Color toneColor(Tone tone) {
        return switch (tone) {
            case MAGENTA -> MAGENTA;
            case VIOLET -> VIOLET;
            default -> CYAN;
        };
    }

    private static BufferedImage tintedGem(int size, Color tint) {
        BufferedImage source = AssetStore.SHARED.image(GEM);
        if (source == null || size <= 0) return null;
        long key = (((long) size) << 32) | (tint.getRGB() & 0xffffffffL);
        BufferedImage cached = GEMS.get(key);
        if (cached != null) return cached;
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        int tr = tint.getRed();
        int tg = tint.getGreen();
        int tb = tint.getBlue();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = scaled.getRGB(x, y);
                int a = (argb >>> 24) & 0xff;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xff;
                int gc = (argb >> 8) & 0xff;
                int b = argb & 0xff;
                int nr = Math.min(254, r * tr / 255);
                int ng = Math.min(254, gc * tg / 255);
                int nb = Math.min(254, b * tb / 255);
                if (nr == 0 && ng == 0 && nb == 0) nb = 1;
                scaled.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        GEMS.put(key, scaled);
        return scaled;
    }

    private static Color shift(Color color, int amount) {
        return new Color(
                clampChannel(color.getRed() + amount),
                clampChannel(color.getGreen() + amount),
                clampChannel(color.getBlue() + amount));
    }

    private static int clampChannel(int value) {
        return Math.max(1, Math.min(254, value));
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static void fillDiamond(Graphics2D g, int cx, int cy, int radius) {
        int[] xs = {cx, cx + radius, cx, cx - radius};
        int[] ys = {cy - radius, cy, cy + radius, cy};
        g.fillPolygon(xs, ys, 4);
    }

    private static void fillStar(Graphics2D g, int cx, int cy, int radius) {
        int[] xs = new int[10];
        int[] ys = new int[10];
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            int arm = i % 2 == 0 ? radius : radius / 2;
            xs[i] = cx + (int) (Math.cos(angle) * arm);
            ys[i] = cy + (int) (Math.sin(angle) * arm);
        }
        g.fillPolygon(xs, ys, 10);
    }

    /** 버튼 안 글자를 가운데에 둔다 */
    public static void drawCentered(Graphics2D g, String text, int centerX, int baseline) {
        GameTheme.drawCentered(g, text, centerX, baseline);
    }

    public static RoundRectangle2D buttonShape(int x, int y, int width, int height) {
        return new RoundRectangle2D.Float(x, y, width, height, height, height);
    }
}
