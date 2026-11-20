package tetris.ui.theme;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import tetris.ui.fx.AssetStore;

/**
 * 9등분·가로 3등분 그림. 목표 크기마다 한 번만 잘라 두고 다시 그린다.
 * 원본이 없으면 {@link #available()}이 false이고, 부르는 쪽이 도형으로 대신 그린다.
 */
public final class NineSlice {
    public enum Axis {
        NINE, HORIZONTAL
    }

    private static final int CACHE_LIMIT = 48;

    private final String path;
    private final int cap;
    private final Axis axis;
    private final AssetStore assets;
    private BufferedImage source;
    private boolean resolved;
    private final Map<Long, BufferedImage> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    public NineSlice(String path, int cap, Axis axis) {
        this(path, cap, axis, AssetStore.SHARED);
    }

    public NineSlice(String path, int cap, Axis axis, AssetStore assets) {
        this.path = path;
        this.cap = Math.max(1, cap);
        this.axis = axis == null ? Axis.NINE : axis;
        this.assets = assets == null ? AssetStore.SHARED : assets;
    }

    public boolean available() {
        return source() != null;
    }

    /** 캐시에 같은 크기 그림이 있으면 그 인스턴스. 테스트가 재할당이 없는지 본다 */
    public BufferedImage scaled(int width, int height) {
        if (width <= 0 || height <= 0 || source() == null) return null;
        long key = (((long) width) << 32) | (height & 0xffffffffL);
        BufferedImage cached = cache.get(key);
        if (cached != null) return cached;
        BufferedImage made = axis == Axis.HORIZONTAL ? sliceHorizontal(width, height) : sliceNine(width, height);
        cache.put(key, made);
        return made;
    }

    public void paint(Graphics2D g, int x, int y, int width, int height) {
        BufferedImage image = scaled(width, height);
        if (image != null) g.drawImage(image, x, y, null);
    }

    private BufferedImage source() {
        if (!resolved) {
            resolved = true;
            source = assets.image(path);
        }
        return source;
    }

    private BufferedImage sliceNine(int width, int height) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int sc = Math.min(cap, Math.min(sw, sh) / 2);
        int dc = Math.min(sc, Math.min(width, height) / 2);
        if (sc <= 0 || dc <= 0) return scaleWhole(width, height);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            blit(g, 0, 0, sc, sc, 0, 0, dc, dc);
            blit(g, sw - sc, 0, sc, sc, width - dc, 0, dc, dc);
            blit(g, 0, sh - sc, sc, sc, 0, height - dc, dc, dc);
            blit(g, sw - sc, sh - sc, sc, sc, width - dc, height - dc, dc, dc);
            blit(g, sc, 0, sw - 2 * sc, sc, dc, 0, width - 2 * dc, dc);
            blit(g, sc, sh - sc, sw - 2 * sc, sc, dc, height - dc, width - 2 * dc, dc);
            blit(g, 0, sc, sc, sh - 2 * sc, 0, dc, dc, height - 2 * dc);
            blit(g, sw - sc, sc, sc, sh - 2 * sc, width - dc, dc, dc, height - 2 * dc);
            blit(g, sc, sc, sw - 2 * sc, sh - 2 * sc, dc, dc, width - 2 * dc, height - 2 * dc);
        } finally {
            g.dispose();
        }
        return out;
    }

    private BufferedImage sliceHorizontal(int width, int height) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int sc = Math.min(cap, sw / 2);
        int dc = Math.min(sc, width / 2);
        if (sc <= 0 || dc <= 0) return scaleWhole(width, height);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            blit(g, 0, 0, sc, sh, 0, 0, dc, height);
            blit(g, sc, 0, sw - 2 * sc, sh, dc, 0, width - 2 * dc, height);
            blit(g, sw - sc, 0, sc, sh, width - dc, 0, dc, height);
        } finally {
            g.dispose();
        }
        return out;
    }

    private BufferedImage scaleWhole(int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private void blit(Graphics2D g, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh) {
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return;
        g.drawImage(source, dx, dy, dx + dw, dy + dh, sx, sy, sx + sw, sy + sh, null);
    }
}
