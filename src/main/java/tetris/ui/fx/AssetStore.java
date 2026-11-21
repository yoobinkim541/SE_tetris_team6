package tetris.ui.fx;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.CardId;
import tetris.feature.raid.api.PatternId;

/**
 * 클래스패스 그림({@code /assets/...})을 읽고 목표 크기별로 한 번만 축소해 둔다.
 * 파일이 없거나 읽기에 실패하면 null. 호출하는 쪽이 도형으로 대신 그린다.
 */
public final class AssetStore {
    public static final String BG_MENU = "/assets/bg/menu.jpg";
    public static final String BG_RAID = "/assets/bg/raid.jpg";
    public static final String BG_VERSUS = "/assets/bg/versus.jpg";
    public static final String LOGO = "/assets/ui/logo.png";
    public static final String CARD_BACK = "/assets/card/back.png";

    /** 화면이 공유하는 저장소. 테스트는 앵커 클래스를 따로 넘긴다 */
    public static final AssetStore SHARED = new AssetStore(AssetStore.class);

    private final Class<?> anchor;
    private final Map<String, BufferedImage> originals = new HashMap<>();
    private final Map<String, Boolean> missing = new HashMap<>();
    private final Map<String, BufferedImage> scaled = new HashMap<>();

    public AssetStore() {
        this(AssetStore.class);
    }

    /** anchor의 클래스로더로 읽는다. 테스트 리소스를 보려면 테스트 클래스를 넘긴다 */
    public AssetStore(Class<?> anchor) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
    }

    /** 원본. 없으면 null. 같은 경로는 한 번만 읽는다 */
    public synchronized BufferedImage image(String path) {
        if (path == null || path.isBlank()) return null;
        if (missing.containsKey(path)) return null;
        BufferedImage cached = originals.get(path);
        if (cached != null) return cached;
        BufferedImage loaded = read(path);
        if (loaded == null) {
            missing.put(path, Boolean.TRUE);
            return null;
        }
        originals.put(path, loaded);
        return loaded;
    }

    /**
     * 목표 크기로 한 번만 스케일해 캐시한다. 같은 경로·크기면 같은 인스턴스.
     * 없거나 크기가 0 이하면 null.
     */
    public synchronized BufferedImage scaled(String path, int width, int height) {
        if (width <= 0 || height <= 0) return null;
        BufferedImage source = image(path);
        if (source == null) return null;
        String key = path + "@" + width + "x" + height;
        BufferedImage cached = scaled.get(key);
        if (cached != null) return cached;
        BufferedImage out = scale(source, 0, 0, width, height, width, height);
        scaled.put(key, out);
        return out;
    }

    /**
     * 칸을 빈틈 없이 덮도록 가운데를 잘라 축소한다. 화면 크기마다 한 번만 만든다.
     * 없으면 null.
     */
    public synchronized BufferedImage cover(String path, int width, int height) {
        if (width <= 0 || height <= 0) return null;
        BufferedImage source = image(path);
        if (source == null) return null;
        String key = path + "@cover:" + width + "x" + height;
        BufferedImage cached = scaled.get(key);
        if (cached != null) return cached;
        double scale = Math.max(width / (double) source.getWidth(), height / (double) source.getHeight());
        int drawnW = Math.max(width, (int) Math.ceil(source.getWidth() * scale));
        int drawnH = Math.max(height, (int) Math.ceil(source.getHeight() * scale));
        int x = (width - drawnW) / 2;
        int y = (height - drawnH) / 2;
        BufferedImage out = scale(source, x, y, drawnW, drawnH, width, height);
        scaled.put(key, out);
        return out;
    }

    /**
     * 비율을 유지한 채 max 칸 안에 넣는다. 로고처럼 늘어나면 안 되는 그림용.
     * 없으면 null.
     */
    public synchronized BufferedImage fit(String path, int maxWidth, int maxHeight) {
        if (maxWidth <= 0 || maxHeight <= 0) return null;
        BufferedImage source = image(path);
        if (source == null) return null;
        double scale = Math.min(maxWidth / (double) source.getWidth(), maxHeight / (double) source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        return scaled(path, width, height);
    }

    /** 보스 단계 그림. 황금은 lv1만, 나머지는 3단계까지. Lv.5 이상은 lv3에 색조만 바꾼다 */
    public synchronized BufferedImage boss(BossId id, int level, int size) {
        if (id == null || size <= 0) return null;
        int stage = id == BossId.GOLDEN_TETRO ? 1 : Math.min(Math.max(level, 1), 3);
        String path = "/assets/boss/" + id.assetKey() + "_lv" + stage + ".png";
        BufferedImage image = scaled(path, size, size);
        if (image == null || level < 5 || id == BossId.GOLDEN_TETRO) return image;
        String key = path + "@hue" + level + ":" + size;
        BufferedImage tinted = scaled.get(key);
        if (tinted != null) return tinted;
        tinted = hueShift(image, (level - 4) * 0.11f);
        scaled.put(key, tinted);
        return tinted;
    }

    public static String patternPath(PatternId id) {
        return "/assets/icon/pattern_" + id.assetKey() + ".png";
    }

    public static String cardPath(CardId id) {
        return "/assets/card/" + id.assetKey() + ".png";
    }

    private BufferedImage read(String path) {
        try (InputStream in = anchor.getResourceAsStream(path)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static BufferedImage scale(BufferedImage source, int x, int y, int drawnW, int drawnH, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, x, y, drawnW, drawnH, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Lv.5 이상. 같은 그림을 색조만 밀어 단계가 더 올랐음을 보여 준다 */
    private static BufferedImage hueShift(BufferedImage source, float amount) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        float shift = amount - (float) Math.floor(amount);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                if (alpha == 0) continue;
                int red = (argb >> 16) & 0xff;
                int green = (argb >> 8) & 0xff;
                int blue = argb & 0xff;
                float[] hsb = java.awt.Color.RGBtoHSB(red, green, blue, null);
                int rgb = java.awt.Color.HSBtoRGB((hsb[0] + shift) % 1f, Math.min(1f, hsb[1] * 1.05f), hsb[2]);
                out.setRGB(x, y, (alpha << 24) | (rgb & 0x00ffffff));
            }
        }
        return out;
    }
}
