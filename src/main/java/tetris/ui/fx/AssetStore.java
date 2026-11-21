package tetris.ui.fx;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.CardId;
import tetris.feature.raid.api.PatternId;

/**
 * 클래스패스 그림({@code /assets/...})을 읽고 목표 크기별로 한 번만 축소해 둔다.
 * 파일이 없거나 읽기에 실패하면 null이고, 같은 경로는 한 번만 stderr에 경고한다. 실패는 2초 뒤, 그리고 화면을 들어올 때 다시 읽는다.
 *
 * <p>축소본은 장치 픽셀 크기를 키로 하는 상한 LRU다. 화면을 떠나면 그 화면 그림만 버리고,
 * 보스는 지금 것만 둔다. 표시 크기를 만든 뒤에는 디코드한 원본을 버려 필요할 때 다시 읽는다.
 */
public final class AssetStore {
    public static final String BG_MENU = "/assets/bg/menu.jpg";
    public static final String BG_RAID = "/assets/bg/raid.jpg";
    public static final String BG_VERSUS = "/assets/bg/versus.jpg";
    public static final String LOGO = "/assets/ui/logo.png";
    public static final String CARD_BACK = "/assets/card/back.png";

    private static final int MAX_SCALED = 32;
    private static final long MAX_SCALED_BYTES = 48L * 1024 * 1024;

    /** 화면이 공유하는 저장소. 테스트는 앵커 클래스를 따로 넘긴다 */
    public static final AssetStore SHARED = new AssetStore(AssetStore.class);

    private final Class<?> anchor;
    private final Map<String, BufferedImage> originals = new LinkedHashMap<>();
    private final Map<String, Long> missing = new LinkedHashMap<>();
    private final LinkedHashMap<String, BufferedImage> scaled = new LinkedHashMap<>(32, 0.75f, true);
    private String screen = "menu";
    private BossId boss;

    public AssetStore() {
        this(AssetStore.class);
    }

    /** anchor의 클래스로더로 읽는다. 테스트 리소스를 보려면 테스트 클래스를 넘긴다 */
    public AssetStore(Class<?> anchor) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
    }

    /**
     * Graphics 변환의 배율(Windows 125/150% 포함)로 사용자 픽셀을 장치 픽셀로 올린다.
     * 배율이 1 미만이면 1로 둔다.
     */
    public static int devicePixels(Graphics2D g, int userPixels) {
        if (userPixels <= 0) return 0;
        double scale = 1;
        if (g != null) {
            AffineTransform transform = g.getTransform();
            scale = Math.hypot(transform.getScaleX(), transform.getShearY());
        }
        if (scale < 1 || Double.isNaN(scale)) scale = 1;
        return Math.max(1, (int) Math.ceil(userPixels * scale));
    }

    /** 이 화면이 아닌 그림 캐시를 버린다. 공용 UI는 남긴다 */
    public synchronized void enterScreen(String name) {
        String next = name == null || name.isBlank() ? "shared" : name;
        missing.clear();
        if (next.equals(screen)) return;
        screen = next;
        dropOwned(scaled.keySet().iterator(), next);
        dropOwned(originals.keySet().iterator(), next);
    }

    public synchronized String screen() {
        return screen;
    }

    /** 지금 보스 그림만 남긴다. 다른 보스 원본·축소본은 버린다 */
    public synchronized void retainBoss(BossId id) {
        if (id == null || id == boss) return;
        boss = id;
        String key = id.assetKey();
        scaled.entrySet().removeIf(entry -> otherBoss(entry.getKey(), key));
        originals.keySet().removeIf(path -> otherBoss(path, key));
    }

    public synchronized long scaledBytes() {
        long bytes = 0;
        for (BufferedImage image : scaled.values()) bytes += bytes(image);
        return bytes;
    }

    public synchronized int scaledCount() {
        return scaled.size();
    }

    /** 원본. 없으면 null. 같은 경로는 한 번만 읽는다. 축소본을 만든 뒤에는 버려 두므로 다시 읽을 수 있다 */
    public synchronized BufferedImage image(String path) {
        return image(path, true);
    }

    /**
     * 변형 파일이 있으면 그 파일. 없으면 base를 색조만 바꿔 캐시한다.
     * 원본이 표시 크기보다 작아도 실패하지 않는다.
     */
    public synchronized BufferedImage imageOrTint(String path, String basePath, float hue) {
        BufferedImage direct = image(path, false);
        if (direct != null) return direct;
        if (basePath == null) return null;
        String key = (path == null ? "" : path) + "@tint:" + basePath + ":" + hue;
        BufferedImage cached = scaled.get(key);
        if (cached != null) return cached;
        BufferedImage base = image(basePath, true);
        if (base == null) return null;
        BufferedImage tinted = hueShift(base, hue);
        remember(key, tinted);
        releaseOriginal(basePath);
        return tinted;
    }

    private BufferedImage image(String path, boolean rememberMissing) {
        if (path == null || path.isBlank()) return null;
        if (recentlyMissing(path)) return null;
        BufferedImage cached = originals.get(path);
        if (cached != null) return cached;
        BufferedImage loaded = read(path);
        if (loaded == null) {
            if (rememberMissing) missing.put(path, System.nanoTime() + MISSING_RETRY_NS);
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
        remember(key, out);
        releaseOriginal(path);
        return out;
    }

    /**
     * 칸을 빈틈 없이 덮도록 가운데를 잘라 축소한다. 화면 크기마다 한 번만 만든다.
     * 없으면 null. width·height는 장치 픽셀이다.
     */
    public synchronized BufferedImage cover(String path, int width, int height) {
        if (width <= 0 || height <= 0) return null;
        BufferedImage source = image(path);
        if (source == null) return null;
        String key = path + "@cover:" + width + "x" + height;
        BufferedImage cached = scaled.get(key);
        if (cached != null) return cached;
        double factor = Math.max(width / (double) source.getWidth(), height / (double) source.getHeight());
        int drawnW = Math.max(width, (int) Math.ceil(source.getWidth() * factor));
        int drawnH = Math.max(height, (int) Math.ceil(source.getHeight() * factor));
        int x = (width - drawnW) / 2;
        int y = (height - drawnH) / 2;
        BufferedImage out = scale(source, x, y, drawnW, drawnH, width, height);
        remember(key, out);
        releaseOriginal(path);
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
        double factor = Math.min(maxWidth / (double) source.getWidth(), maxHeight / (double) source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
        return scaled(path, width, height);
    }

    /** 보스 단계 그림. 황금은 lv1만, 나머지는 3단계까지. Lv.5 이상은 lv3에 색조만 바꾼다. 다른 보스는 버린다 */
    public synchronized BufferedImage boss(BossId id, int level, int size) {
        if (id == null || size <= 0) return null;
        retainBoss(id);
        int stage = id == BossId.GOLDEN_TETRO ? 1 : Math.min(Math.max(level, 1), 3);
        String path = "/assets/boss/" + id.assetKey() + "_lv" + stage + ".png";
        BufferedImage image = scaled(path, size, size);
        if (image == null || level < 5 || id == BossId.GOLDEN_TETRO) return image;
        String key = path + "@hue" + level + ":" + size;
        BufferedImage tinted = scaled.get(key);
        if (tinted != null) return tinted;
        tinted = hueShift(image, (level - 4) * 0.11f);
        remember(key, tinted);
        return tinted;
    }

    /** 레이드 턱 그림. 원본보다 키우지 않고, 단계가 높으면 색조만 민다 */
    public synchronized BufferedImage ledge(BossId id, int level, int maxWidth) {
        if (id == null || maxWidth <= 0) return null;
        retainBoss(id);
        String path = "/assets/raid/ledge-" + id.assetKey() + ".png";
        return fitHue(path, maxWidth, Integer.MAX_VALUE, level >= 3 ? 0.08f : level >= 2 ? 0.04f : 0f);
    }

    /** HP 프레임. 없으면 null. 원본보다 키우지 않는다 */
    public synchronized BufferedImage hpFrame(int maxWidth) {
        if (maxWidth <= 0) return null;
        return fitHue("/assets/raid/hp-frame.png", maxWidth, Integer.MAX_VALUE, 0f);
    }

    private BufferedImage fitHue(String path, int maxWidth, int maxHeight, float hue) {
        BufferedImage source = image(path);
        if (source == null) return null;
        double factor = Math.min(1.0, Math.min(maxWidth / (double) source.getWidth(), maxHeight / (double) source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
        BufferedImage fitted = scaled(path, width, height);
        if (fitted == null || hue == 0f) return fitted;
        String key = path + "@fitHue" + hue + ":" + width + "x" + height;
        BufferedImage cached = scaled.get(key);
        if (cached != null) return cached;
        cached = hueShift(fitted, hue);
        remember(key, cached);
        return cached;
    }

    public static String patternPath(PatternId id) {
        return "/assets/icon/pattern_" + id.assetKey() + ".png";
    }

    public static String cardPath(CardId id) {
        return "/assets/card/" + id.assetKey() + ".png";
    }

    private void remember(String key, BufferedImage image) {
        scaled.put(key, image);
        trim();
    }

    private void trim() {
        while (scaled.size() > 1 && (scaled.size() > MAX_SCALED || scaledBytes() > MAX_SCALED_BYTES)) {
            Iterator<String> iterator = scaled.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private void releaseOriginal(String path) {
        originals.remove(path);
    }

    private void dropOwned(Iterator<String> keys, String keep) {
        while (keys.hasNext()) {
            String key = keys.next();
            String owner = ownerOf(key);
            if (!"shared".equals(owner) && !owner.equals(keep)) keys.remove();
        }
    }

    static String ownerOf(String key) {
        if (key == null) return "shared";
        if (key.contains("/assets/bg/menu")) return "menu";
        if (key.contains("/assets/bg/versus")) return "battle";
        if (key.contains("/assets/bg/raid") || key.contains("/assets/raid/")
                || key.contains("/assets/boss/") || key.contains("/assets/card/")
                || key.contains("/assets/icon/")) return "raid";
        return "shared";
    }

    private static boolean otherBoss(String key, String assetKey) {
        boolean bossArt = key.contains("/assets/boss/") || key.contains("/assets/raid/ledge-");
        return bossArt && !key.contains(assetKey);
    }

    private static long bytes(BufferedImage image) {
        if (image == null) return 0;
        return (long) image.getWidth() * image.getHeight() * 4L;
    }

    private static final long MISSING_RETRY_NS = 2_000_000_000L;
    private static final java.util.Set<String> WARNED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private boolean recentlyMissing(String path) {
        Long until = missing.get(path);
        if (until == null) return false;
        if (System.nanoTime() < until) return true;
        missing.remove(path);
        return false;
    }

    private BufferedImage read(String path) {
        try (InputStream in = anchor.getResourceAsStream(path)) {
            if (in == null) {
                warnOnce(path, "클래스패스에 없음");
                return null;
            }
            BufferedImage image = ImageIO.read(in);
            if (image == null) warnOnce(path, "그림을 읽지 못함");
            return image;
        } catch (IOException | RuntimeException e) {
            warnOnce(path, e.getClass().getSimpleName());
            return null;
        }
    }

    private static void warnOnce(String path, String reason) {
        if (path == null || !WARNED.add(path)) return;
        System.err.println("리소스를 열지 못했습니다: " + path + " (" + reason + ")");
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
