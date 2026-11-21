package tetris.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 제목·버튼 글자. 14px 이하는 효과 없이 그린다.
 * 그 위는 단색(흰색 또는 옅은 하늘색)과 얇은 남색 외곽선, 아주 약한 그림자만 캐시한다.
 * 그라데이션·글로우·하이라이트 띠는 쓰지 않는다.
 */
public final class CrystalText {
    public static final int PLAIN = 0;
    public static final int SELECTED = 1;
    private static final int LIMIT = 48;

    private static final class Plate {
        final BufferedImage image;
        final float centerOff; // 기준선에서 글자 모양 세로 중심까지 (글꼴 좌표)
        final float inkX;      // 그림 안에서 글자 모양의 가로 중심
        final float inkY;      // 그림 안에서 글자 모양의 세로 중심

        Plate(BufferedImage image, float centerOff, float inkX, float inkY) {
            this.image = image;
            this.centerOff = centerOff;
            this.inkX = inkX;
            this.inkY = inkY;
        }
    }

    private static final Map<String, Plate> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Plate> eldest) {
            return size() > LIMIT;
        }
    };

    private CrystalText() {
    }

    public static void paint(Graphics2D g, String text, Font font, int centerX, int baseline, boolean selected) {
        paint(g, text, font, centerX, baseline, selected ? SELECTED : PLAIN);
    }

    /** style 0은 보통, 1은 선택(조금 더 밝고 아주 살짝 커짐) */
    public static void paint(Graphics2D g, String text, Font font, int centerX, int baseline, int style) {
        if (g == null || text == null || text.isEmpty() || font == null) return;
        if (font.getSize() <= 14) {
            Font previous = g.getFont();
            g.setFont(font);
            GameTheme.drawCentered(g, text, centerX, baseline);
            if (previous != null) g.setFont(previous);
            return;
        }
        boolean selected = style == SELECTED;
        float grow = selected ? 1.02f + 0.01f * UiKit.pulse() : 1f;
        Plate plate = cached(text, font, style);
        int w = Math.round(plate.image.getWidth() * grow);
        int h = Math.round(plate.image.getHeight() * grow);
        float sx = w / (float) plate.image.getWidth();
        float sy = h / (float) plate.image.getHeight();
        // 글자 모양의 실제 중심을 (centerX, 기준선 + centerOff)에 맞춘다. 커져도 중심은 그대로다.
        // 예전에는 그림 폭의 절반에 0.5px를 더한 값을 중심으로 써서 0.5~1px 치우쳤다
        int x = Math.round(centerX - plate.inkX * sx);
        int y = Math.round(baseline + plate.centerOff - plate.inkY * sy);
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(plate.image, x, y, w, h, null);
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    }

    private static Plate cached(String text, Font font, int style) {
        String key = text + "\0" + font.getSize() + "\0" + style + "\0" + font.getFontName();
        Plate hit = CACHE.get(key);
        if (hit != null) return hit;
        boolean selected = style == SELECTED;
        FontRenderContext context = new FontRenderContext(new AffineTransform(), true, true);
        Shape raw = outline(font, text, context);
        Rectangle2D bounds = raw.getBounds2D();
        int pad = 6;
        float tx = pad - (float) bounds.getX();
        float ty = pad - (float) bounds.getY();
        Shape shape = AffineTransform.getTranslateInstance(tx, ty).createTransformedShape(raw);
        int width = Math.max(1, (int) Math.ceil(bounds.getWidth()) + pad * 2);
        int height = Math.max(1, (int) Math.ceil(bounds.getHeight()) + pad * 2);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D layer = image.createGraphics();
        try {
            GameTheme.applyQuality(layer);
            layer.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            layer.translate(1, 1);
            layer.setColor(new Color(8, 14, 36, 70));
            layer.fill(shape);
            layer.translate(-1, -1);
            layer.setColor(new Color(8, 14, 36, 220));
            layer.setStroke(new BasicStroke(Math.max(1.25f, font.getSize() / 18f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            layer.draw(shape);
            // 메뉴·제목 글자는 모두 흰색. 선택 상태는 글자 색이 아니라 버튼 그림·화살표·크기로 보인다
            layer.setColor(Color.WHITE);
            layer.fill(shape);
        } finally {
            layer.dispose();
        }
        float centerOff = (float) (bounds.getY() + bounds.getHeight() / 2.0);
        float inkX = (float) (tx + bounds.getCenterX());
        float inkY = (float) (ty + bounds.getCenterY());
        Plate plate = new Plate(image, centerOff, inkX, inkY);
        CACHE.put(key, plate);
        return plate;
    }

    private static Shape outline(Font font, String text, FontRenderContext context) {
        GeneralPath path = new GeneralPath();
        float x = 0f;
        float targetH = capHeight(font, context);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                x += Math.max(1f, font.getSize() * 0.33f);
                continue;
            }
            Font face = GameTheme.faceFor(font, c, context);
            GlyphVector glyph = face.createGlyphVector(context, String.valueOf(c));
            float advance = (float) glyph.getGlyphPosition(glyph.getNumGlyphs()).getX();
            Shape raw = glyph.getOutline(0, 0);
            Rectangle2D box = raw.getBounds2D();
            if (face.equals(font) || box.getHeight() < 0.5) {
                path.append(glyph.getOutline(x, 0), false);
                x += Math.max(1f, advance);
                continue;
            }
            float scale = targetH / (float) box.getHeight();
            float drawn = (float) box.getWidth() * scale;
            AffineTransform at = new AffineTransform();
            at.translate(x - box.getX() * scale, 0);
            at.scale(scale, scale);
            path.append(at.createTransformedShape(raw), false);
            x += Math.max(drawn, advance * scale);
        }
        return path;
    }

    private static float capHeight(Font font, FontRenderContext context) {
        String sample = font.canDisplay('가') ? "가" : "A";
        Rectangle2D box = font.createGlyphVector(context, sample).getVisualBounds();
        return Math.max(1f, (float) box.getHeight());
    }
}
