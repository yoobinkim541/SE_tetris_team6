package tetris.ui.raid;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import tetris.ui.fx.AssetStore;
import tetris.ui.theme.UiKit;

/** 화려한 보드 틀. 없으면 UiKit 도형. 코너는 가상 좌표로 줄여 보드를 넓힌다 */
final class RaidFrames {
    private static final int CACHE = 16;
    private final AssetStore assets;
    private final Map<Long, BufferedImage> cache = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> eldest) {
            return size() > CACHE;
        }
    };

    RaidFrames(AssetStore assets) {
        this.assets = assets;
    }

    void paint(Graphics2D g, int index, int x, int y, int width, int height) {
        if (width <= 2 || height <= 2) return;
        BufferedImage image = sliced(index, width, height);
        if (image == null) {
            UiKit.paintFrame(g, index == 0 ? UiKit.Tone.CYAN : UiKit.Tone.MAGENTA, x, y, width, height);
            return;
        }
        g.drawImage(image, x, y, null);
    }

    private BufferedImage sliced(int index, int width, int height) {
        RaidArt art = RaidArt.DATA;
        RaidArt.Insets inset = art.frameInsets(index);
        int corner = art.frameCorner(width, height, index);
        long key = (((long) index) << 48) ^ (((long) width) << 32) ^ (((long) height) << 16) ^ corner
                ^ (inset.cap() << 8);
        BufferedImage cached = cache.get(key);
        if (cached != null) return cached;
        BufferedImage source = assets.image(art.framePath(index));
        if (source == null) return null;
        BufferedImage sliced = slice(source, inset, width, height, corner);
        cache.put(key, sliced);
        return sliced;
    }

    private static BufferedImage slice(BufferedImage source, RaidArt.Insets inset, int width, int height, int corner) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int left = clampInset(inset.left(), sw);
        int right = clampInset(inset.right(), sw - left);
        int top = clampInset(inset.top(), sh);
        int bottom = clampInset(inset.bottom(), sh - top);
        int dc = Math.max(1, Math.min(corner, Math.min(width, height) / 2 - 1));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            blit(g, source, 0, 0, left, top, 0, 0, dc, dc);
            blit(g, source, sw - right, 0, right, top, width - dc, 0, dc, dc);
            blit(g, source, 0, sh - bottom, left, bottom, 0, height - dc, dc, dc);
            blit(g, source, sw - right, sh - bottom, right, bottom, width - dc, height - dc, dc, dc);
            blit(g, source, left, 0, sw - left - right, top, dc, 0, width - 2 * dc, dc);
            blit(g, source, left, sh - bottom, sw - left - right, bottom, dc, height - dc, width - 2 * dc, dc);
            blit(g, source, 0, top, left, sh - top - bottom, 0, dc, dc, height - 2 * dc);
            blit(g, source, sw - right, top, right, sh - top - bottom, width - dc, dc, dc, height - 2 * dc);
            blit(g, source, left, top, sw - left - right, sh - top - bottom, dc, dc, width - 2 * dc, height - 2 * dc);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static int clampInset(int value, int limit) {
        if (limit <= 1) return 1;
        return Math.max(1, Math.min(value, limit - 1));
    }

    private static void blit(Graphics2D g, BufferedImage source, int sx, int sy, int sw, int sh,
                             int dx, int dy, int dw, int dh) {
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return;
        g.drawImage(source, dx, dy, dx + dw, dy + dh, sx, sy, sx + sw, sy + sh, null);
    }
}
