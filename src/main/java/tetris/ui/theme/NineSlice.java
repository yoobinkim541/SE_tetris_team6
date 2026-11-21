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
    private String tintBase;
    private float tintHue;
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

    /** 변형 파일이 없으면 base를 hue만큼 밀어 쓴다. 파일이 있으면 파일을 우선한다 */
    public NineSlice(String path, String tintBase, float hue, int cap, Axis axis) {
        this(path, cap, axis);
        this.tintBase = tintBase;
        this.tintHue = hue;
    }

    public boolean available() {
        return source() != null;
    }

    /** 캐시에 같은 크기 그림이 있으면 그 인스턴스. 테스트가 재할당이 없는지 본다 */
    public BufferedImage scaled(int width, int height) {
        return scaled(width, height, Integer.MAX_VALUE);
    }

    private BufferedImage scaled(int width, int height, int maxCorner) {
        if (width <= 0 || height <= 0 || source() == null) return null;
        int corner = maxCorner == Integer.MAX_VALUE ? 0 : Math.max(0, maxCorner);
        long key = (((long) width) << 32) | (height & 0xffffffffL);
        if (corner > 0) key ^= ((long) corner) << 48;
        BufferedImage cached = cache.get(key);
        if (cached != null) return cached;
        BufferedImage made = axis == Axis.HORIZONTAL ? sliceHorizontal(width, height) : sliceNine(width, height, corner);
        cache.put(key, made);
        return made;
    }

    public void paint(Graphics2D g, int x, int y, int width, int height) {
        paint(g, x, y, width, height, Integer.MAX_VALUE);
    }

    /**
     * 화면 배율(전체 화면·Windows 배율)이 걸린 Graphics에 작은 그림을 늘려 그리면 Java2D가 소프트웨어 변환으로 매 프레임 느리게 그린다.
     * 장치 픽셀 크기로 한 번 잘라 두고 변환 없이 1:1로 찍는다.
     */
    public void paint(Graphics2D g, int x, int y, int width, int height, int maxCorner) {
        java.awt.geom.AffineTransform t = g.getTransform();
        boolean axisAligned = t.getShearX() == 0 && t.getShearY() == 0 && t.getScaleX() > 0 && t.getScaleY() > 0;
        if (!axisAligned || (t.getScaleX() == 1 && t.getScaleY() == 1)) {
            BufferedImage image = scaled(width, height, maxCorner);
            if (image != null) g.drawImage(image, x, y, null);
            return;
        }
        java.awt.geom.Point2D topLeft = t.transform(new java.awt.geom.Point2D.Double(x, y), null);
        java.awt.geom.Point2D bottomRight = t.transform(new java.awt.geom.Point2D.Double(x + width, y + height), null);
        int dx = (int) Math.round(topLeft.getX());
        int dy = (int) Math.round(topLeft.getY());
        int dw = Math.max(1, (int) Math.round(bottomRight.getX()) - dx);
        int dh = Math.max(1, (int) Math.round(bottomRight.getY()) - dy);
        int corner = maxCorner == Integer.MAX_VALUE ? Integer.MAX_VALUE
                : Math.max(1, (int) Math.round(maxCorner * t.getScaleX()));
        BufferedImage image = scaled(dw, dh, corner);
        if (image == null) return;
        g.setTransform(new java.awt.geom.AffineTransform());
        try {
            g.drawImage(image, dx, dy, null);
        } finally {
            g.setTransform(t);
        }
    }
    /** 장식 캡을 뺀 가로 여백. 원본 캡 픽셀을 그린 높이에 맞춰 줄인다 */
    public int insetX(int width, int height) {
        BufferedImage src = source();
        if (src == null || width <= 0 || height <= 0) return Math.max(4, width / 8);
        float scale = axis == Axis.HORIZONTAL ? height / (float) Math.max(1, src.getHeight())
                : Math.min(width / (float) src.getWidth(), height / (float) src.getHeight());
        int inset = Math.round(Math.min(cap, src.getWidth() / 2) * scale);
        return Math.max(2, Math.min(inset, Math.max(2, width / 3)));
    }

    /** 위아래 테두리. 가로 3등분은 원본 높이의 1/6, 9등분은 캡 비율 */
    public int insetY(int width, int height) {
        BufferedImage src = source();
        if (src == null || height <= 0) return Math.max(2, height / 6);
        if (axis == Axis.HORIZONTAL) return Math.max(2, Math.min(height / 6, height / 3));
        float scale = Math.min(width / (float) Math.max(1, src.getWidth()), height / (float) src.getHeight());
        int inset = Math.round(Math.min(cap, src.getHeight() / 5) * scale);
        return Math.max(2, Math.min(inset, Math.max(2, height / 3)));
    }


    private BufferedImage source() {
        if (!resolved) {
            resolved = true;
            source = tintBase == null ? assets.image(path) : assets.imageOrTint(path, tintBase, tintHue);
        }
        return source;
    }

    private BufferedImage sliceNine(int width, int height, int maxCorner) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int sc = Math.min(cap, Math.min(sw, sh) / 2);
        int dc = Math.min(sc, Math.min(width, height) / 2);
        if (maxCorner > 0) dc = Math.min(dc, maxCorner);
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
