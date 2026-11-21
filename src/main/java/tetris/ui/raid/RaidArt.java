package tetris.ui.raid;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * {@code raid-art.json}의 칸. 파일이 바뀌면 다음 실행이 새 인셋을 읽는다.
 * 읽기 실패 시 현재 에셋의 좌표로 그린다.
 */
final class RaidArt {
    static final String SCENE = "/assets/raid/scene-raid.jpg";
    static final String SCENE_WIDE = "/assets/raid/scene-raid-wide.jpg";
    static final String HP_FRAME = "/assets/raid/hp-frame.png";
    static final String NAMETAB_P1 = "/assets/raid/nametab-p1.png";
    static final String NAMETAB_P2 = "/assets/raid/nametab-p2.png";
    static final String FRAME_CYAN = "/assets/raid/frame-ornate-cyan.png";
    static final String FRAME_MAGENTA = "/assets/raid/frame-ornate-magenta.png";

    static final RaidArt DATA = load();

    final int hpWidth;
    final int hpHeight;
    final Rect hpBar;
    final Rect hpPercent;
    final int tabWidth;
    final int tabHeight;
    final Rect tabTextP1;
    final Rect tabTextP2;
    final Insets frameCyan;
    final Insets frameMagenta;
    final int ledgeBottom;
    final int railCyan;
    final int railMagenta;

    private RaidArt(int hpWidth, int hpHeight, Rect hpBar, Rect hpPercent, int tabWidth, int tabHeight,
                    Rect tabTextP1, Rect tabTextP2, Insets frameCyan, Insets frameMagenta, int ledgeBottom,
                    int railCyan, int railMagenta) {
        this.hpWidth = hpWidth;
        this.hpHeight = hpHeight;
        this.hpBar = hpBar;
        this.hpPercent = hpPercent;
        this.tabWidth = tabWidth;
        this.tabHeight = tabHeight;
        this.tabTextP1 = tabTextP1;
        this.tabTextP2 = tabTextP2;
        this.frameCyan = frameCyan;
        this.frameMagenta = frameMagenta;
        this.ledgeBottom = ledgeBottom;
        this.railCyan = railCyan;
        this.railMagenta = railMagenta;
    }

    Rect tabText(int index) {
        return index == 0 ? tabTextP1 : tabTextP2;
    }

    Insets frameInsets(int index) {
        return index == 0 ? frameCyan : frameMagenta;
    }

    String framePath(int index) {
        return index == 0 ? FRAME_CYAN : FRAME_MAGENTA;
    }

    String tabPath(int index) {
        return index == 0 ? NAMETAB_P1 : NAMETAB_P2;
    }

    int rail(int index) {
        int value = index == 0 ? railCyan : railMagenta;
        return value > 0 ? value : 22;
    }

    /** 그린 띠 두께. json의 railThickness를 쓰고, 작은 틀만 더 줄인다 */
    int frameCorner(int width, int height, int index) {
        int room = Math.min(width, height) / 3;
        if (room < 8) return Math.max(4, room);
        return Math.min(rail(index), room);
    }

    private static RaidArt load() {
        Rect bar = new Rect(202, 60, 608, 45);
        Rect percent = new Rect(867, 61, 111, 43);
        Rect p1 = new Rect(164, 25, 306, 94);
        Rect p2 = new Rect(58, 40, 198, 56);
        Insets cyan = new Insets(64, 64, 64, 64);
        Insets magenta = new Insets(64, 64, 64, 64);
        int hpW = 1080;
        int hpH = 161;
        int tabW = 520;
        int tabH = 136;
        int ledge = 719;
        int railC = 22;
        int railM = 22;
        String json = read("/assets/raid/raid-art.json");
        if (!json.isEmpty()) {
            String hp = objectAfter(json, "hp-frame.png");
            hpW = sizeAt(hp, 0, hpW);
            hpH = sizeAt(hp, 1, hpH);
            bar = rectOf(hp, "bar", bar);
            percent = rectOf(hp, "percentPod", percent);
            String tab1 = objectAfter(json, "nametab-p1.png");
            String tab2 = objectAfter(json, "nametab-p2.png");
            tabW = sizeAt(tab1, 0, tabW);
            tabH = sizeAt(tab1, 1, tabH);
            p1 = arrayOf(tab1, "text", p1);
            p2 = arrayOf(tab2, "text", p2);
            cyan = insetsOf(objectAfter(json, "frame-ornate-cyan.png"), cyan);
            magenta = insetsOf(objectAfter(json, "frame-ornate-magenta.png"), magenta);
            railC = field(objectAfter(json, "frame-ornate-cyan.png"), "railThickness", railC);
            railM = field(objectAfter(json, "frame-ornate-magenta.png"), "railThickness", railM);
            int found = field(objectAfter(json, "ledge-golem.png"), "ledgeBottom", -1);
            if (found >= 0) ledge = found;
        }
        return new RaidArt(hpW, hpH, bar, percent, tabW, tabH, p1, p2, cyan, magenta, ledge, railC, railM);
    }

    private static String read(String path) {
        try (InputStream in = RaidArt.class.getResourceAsStream(path)) {
            if (in == null) return "";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String objectAfter(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int brace = json.indexOf('{', i);
        if (brace < 0) return "";
        int depth = 0;
        for (int p = brace; p < json.length(); p++) {
            char c = json.charAt(p);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(brace, p + 1);
        }
        return "";
    }

    private static int sizeAt(String block, int index, int fallback) {
        int i = block.indexOf("\"size\"");
        if (i < 0) return fallback;
        int bracket = block.indexOf('[', i);
        if (bracket < 0) return fallback;
        int end = block.indexOf(']', bracket);
        if (end < 0) return fallback;
        String[] parts = block.substring(bracket + 1, end).split(",");
        if (index >= parts.length) return fallback;
        return parse(parts[index], fallback);
    }

    private static Rect rectOf(String block, String field, Rect fallback) {
        String obj = objectAfter(block, field);
        if (obj.isEmpty()) return fallback;
        return new Rect(field(obj, "x", fallback.x), field(obj, "y", fallback.y),
                field(obj, "w", fallback.w), field(obj, "h", fallback.h));
    }

    private static Rect arrayOf(String block, String field, Rect fallback) {
        int i = block.indexOf("\"" + field + "\"");
        if (i < 0) return fallback;
        int bracket = block.indexOf('[', i);
        int end = bracket < 0 ? -1 : block.indexOf(']', bracket);
        if (end < 0) return fallback;
        String[] parts = block.substring(bracket + 1, end).split(",");
        if (parts.length < 4) return fallback;
        return new Rect(parse(parts[0], fallback.x), parse(parts[1], fallback.y),
                parse(parts[2], fallback.w), parse(parts[3], fallback.h));
    }

    private static Insets insetsOf(String block, Insets fallback) {
        String obj = objectAfter(block, "sliceInsets");
        if (obj.isEmpty()) return fallback;
        return new Insets(field(obj, "top", fallback.top), field(obj, "right", fallback.right),
                field(obj, "bottom", fallback.bottom), field(obj, "left", fallback.left));
    }

    private static int field(String block, String name, int fallback) {
        int i = block.indexOf("\"" + name + "\"");
        if (i < 0) return fallback;
        int colon = block.indexOf(':', i);
        if (colon < 0) return fallback;
        return parse(block.substring(colon + 1), fallback);
    }

    private static int parse(String raw, int fallback) {
        int n = 0;
        int sign = 1;
        boolean digit = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '-' && !digit) sign = -1;
            else if (c >= '0' && c <= '9') {
                digit = true;
                n = n * 10 + (c - '0');
            } else if (digit) break;
        }
        return digit ? sign * n : fallback;
    }

    record Rect(int x, int y, int w, int h) {
    }

    record Insets(int top, int right, int bottom, int left) {
        int cap() {
            return Math.max(1, Math.max(Math.max(top, bottom), Math.max(left, right)));
        }
    }
}
