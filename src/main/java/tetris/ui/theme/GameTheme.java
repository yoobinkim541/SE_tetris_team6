package tetris.ui.theme;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.geom.AffineTransform;
import java.awt.RenderingHints;
import javax.swing.UIManager;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedString;

/**
 * 화면 공통 서체와 크기 토큰. Do Hyeon은 본문, Black Han Sans는 제목·버튼, Orbitron은 숫자.
 * 파일을 못 열면 기존 논리 글꼴로 돌아간다.
 */
public final class GameTheme {
    private static final String DO_HYEON_PATH = "/fonts/DoHyeon-Regular.ttf";
    private static final String ORBITRON_PATH = "/fonts/Orbitron.ttf";
    private static final String DISPLAY_PATH = "/fonts/BlackHanSans-Regular.ttf";

    private static final Font DO_HYEON = load(DO_HYEON_PATH);
    private static final Font ORBITRON = load(ORBITRON_PATH);
    private static final Font DISPLAY = load(DISPLAY_PATH);

    static {
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (DO_HYEON != null) environment.registerFont(DO_HYEON);
        if (ORBITRON != null) environment.registerFont(ORBITRON);
        if (DISPLAY != null) environment.registerFont(DISPLAY);
        installSwingDefaults();
    }

    private GameTheme() {
    }

    public static boolean doHyeonLoaded() {
        return DO_HYEON != null;
    }

    public static boolean orbitronLoaded() {
        return ORBITRON != null;
    }

    public static boolean displayLoaded() {
        return DISPLAY != null;
    }

    /** 제목·버튼. Black Han Sans, 없으면 Do Hyeon */
    public static Font display(int size) {
        int safe = Math.max(1, size);
        if (DISPLAY != null) return DISPLAY.deriveFont(Font.PLAIN, (float) safe);
        return text(safe, true);
    }

    /** 도형·글자 안티앨리어싱. 연출이 낮아도 유지한다 */
    public static void applyQuality(Graphics2D g) {
        if (g == null) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static volatile boolean swingDefaultsInstalled;

    /**
     * Swing 기본 글꼴을 Do Hyeon으로 둔다. 이미 설치했으면 다시 넣지 않는다.
     * 글꼴을 못 열었으면 기본값을 그대로 둔다. 창을 띄우기 전에 한 번 부른다.
     */
    public static void installSwingDefaults() {
        if (swingDefaultsInstalled || DO_HYEON == null) return;
        swingDefaultsInstalled = true;
        String[] keys = {
                "Panel.font", "Label.font", "Button.font", "ToggleButton.font",
                "RadioButton.font", "CheckBox.font", "TextField.font", "FormattedTextField.font",
                "PasswordField.font", "TextArea.font", "TextPane.font", "EditorPane.font",
                "ComboBox.font", "List.font", "Table.font", "TableHeader.font",
                "ToolTip.font", "OptionPane.font", "OptionPane.messageFont", "OptionPane.buttonFont",
                "TitledBorder.font", "Menu.font", "MenuItem.font", "PopupMenu.font",
                "TabbedPane.font", "Spinner.font", "ProgressBar.font"
        };
        for (String key : keys) {
            Font current = UIManager.getFont(key);
            int size = current == null ? 13 : Math.max(1, current.getSize());
            int style = current == null ? Font.PLAIN : current.getStyle();
            UIManager.put(key, DO_HYEON.deriveFont(style, (float) size));
        }
    }

    /** 한글이 나오는 본문. 굵기는 Do Hyeon에 굵은 컷이 없어 합성 굵기만 쓴다 */
    public static Font text(int size, boolean bold) {
        int safe = Math.max(1, size);
        if (DO_HYEON != null) return DO_HYEON.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) safe);
        return new Font(Font.DIALOG, bold ? Font.BOLD : Font.PLAIN, safe);
    }

    /** 점수·시간·HP 숫자. Black Han Sans는 자릿 폭이 같고 0에 빗금이 없다 */
    public static Font digits(int size) {
        int safe = Math.max(1, size);
        if (DISPLAY != null) return DISPLAY.deriveFont(Font.PLAIN, (float) safe);
        return text(safe, true);
    }

    /**
     * 숫자만 숫자 전용 글꼴. 한글이 섞이면 Do Hyeon. 그 글꼴이 못 그리는 글자가 있으면 Dialog.
     * {@code canDisplayUpTo}가 -1인 글꼴만 고른다.
     */
    public static Font fontFor(String text, int size, boolean preferDigits) {
        int safe = Math.max(1, size);
        Font numeric = digits(safe);
        Font hangul = text(safe, false);
        Font dialog = new Font(Font.DIALOG, Font.PLAIN, safe);
        if (text == null || text.isEmpty()) return hangul;
        if (preferDigits && numericOnly(text) && numeric.canDisplayUpTo(text) == -1) return numeric;
        if (hangul.canDisplayUpTo(text) == -1) return hangul;
        if (dialog.canDisplayUpTo(text) == -1) return dialog;
        return dialog;
    }

    public static boolean canDisplay(String text, int size, boolean preferDigits) {
        String value = text == null ? "" : text;
        return fontFor(value, size, preferDigits).canDisplayUpTo(value) == -1;
    }

    /**
     * 고른 글꼴이 못 그리는 글자만 논리 글꼴로 바꾼다. 테스트는 이 글꼴의 {@code canDisplay}를 본다.
     */
    public static Font runFont(String text, int index, int size, boolean preferDigits) {
        Font chosen = primary(text, size, preferDigits);
        if (text == null || index < 0 || index >= text.length() || chosen.canDisplay(text.charAt(index))) return chosen;
        Font alt = fallback(chosen);
        return alt.canDisplay(text.charAt(index)) ? alt : chosen;
    }

    public static void draw(Graphics2D g, String text, int x, int baseline) {
        if (g == null || text == null || text.isEmpty()) return;
        Font chosen = g.getFont() == null ? text(14, false) : g.getFont();
        java.awt.font.FontRenderContext context = g.getFontRenderContext();
        if (allGlyphsDraw(chosen, text, context)) {
            g.drawString(text, x, baseline);
            return;
        }
        AttributedString styled = new AttributedString(text);
        int i = 0;
        while (i < text.length()) {
            Font face = faceFor(chosen, text.charAt(i), context);
            int j = i + 1;
            while (j < text.length() && faceFor(chosen, text.charAt(j), context).equals(face)) j++;
            styled.addAttribute(TextAttribute.FONT, face, i, j);
            i = j;
        }
        g.drawString(styled.getIterator(), x, baseline);
    }

    /** 공백이 아닌 글자의 시각 경계가 비면 그 글꼴은 그 글자를 못 그린다. (글꼴, 문자)로 기억한다 */
    static boolean glyphDraws(Font font, char c, java.awt.font.FontRenderContext context) {
        if (c == ' ' || c == '\n' || c == '\t') return true;
        if (font == null || !font.canDisplay(c)) return false;
        long key = (((long) font.getFontName().hashCode()) << 32) ^ (((long) font.getSize()) << 16) ^ c;
        Boolean hit = GLYPH_DRAWS.get(key);
        if (hit != null) return hit;
        java.awt.font.FontRenderContext frc = context == null
                ? new java.awt.font.FontRenderContext(new java.awt.geom.AffineTransform(), true, true) : context;
        java.awt.font.GlyphVector glyphs = font.createGlyphVector(frc, String.valueOf(c));
        java.awt.geom.Rectangle2D bounds = glyphs.getGlyphVisualBounds(0).getBounds2D();
        boolean ok = bounds.getWidth() > 0.2 && bounds.getHeight() > 0.2;
        if (ok && isArrow(c) && bounds.getWidth() < font.getSize() * 0.35) ok = false;
        GLYPH_DRAWS.put(key, ok);
        return ok;
    }

    private static boolean isArrow(char c) {
        return c == '←' || c == '→' || c == '↑' || c == '↓' || c == '◀' || c == '▶';
    }

    static Font faceFor(Font preferred, char c, java.awt.font.FontRenderContext context) {
        if (glyphDraws(preferred, c, context)) return preferred;
        int size = preferred == null ? 14 : Math.max(1, preferred.getSize());
        Font dialog = new Font(Font.DIALOG, Font.PLAIN, size);
        if (isArrow(c) && glyphDraws(dialog, c, context)) return dialog;
        Font hangul = text(size, false);
        if (glyphDraws(hangul, c, context)) return hangul;
        return dialog;
    }

    private static boolean allGlyphsDraw(Font font, String text, java.awt.font.FontRenderContext context) {
        for (int i = 0; i < text.length(); i++) {
            if (!glyphDraws(font, text.charAt(i), context)) return false;
        }
        return true;
    }

    private static final java.util.Map<Long, Boolean> GLYPH_DRAWS = new java.util.HashMap<>();

    static boolean slashedZero(Font font) {
        return false;
    }

    /**
     * 제목·버튼·배너·큰 결과 글자. 위는 밝은 하늘색, 아래는 분홍/보라.
     * 남색 외곽선, 은은한 글로우, 윗면 하이라이트. 14px 이하는 효과 없이 그린다.
     * 캐시 키는 문자열, 크기, 스타일(0 보통, 1 선택)이다.
     * 선택 상태는 더 밝고 살짝 커졌다 작아진다.
     */
    public static void paintTitle(Graphics2D g, String text, Font font, int centerX, int baseline, int style) {
        CrystalText.paint(g, text, font, centerX, baseline, style);
    }

    /** 화면 제목. Black Han Sans로 효과를 그린다 */
    public static void paintTitle(Graphics2D g, String text, int size, int centerX, int baseline, boolean selected) {
        paintTitle(g, text, display(size), centerX, baseline, selected ? 1 : 0);
    }

    /** 8px 격자. 0 이하는 0 */
    public static int snap8(int value) {
        if (value <= 0) return 0;
        return Math.max(8, (value + 4) / 8 * 8);
    }


    /** 실제로 그릴 글꼴의 시각 경계. 빈 글리프는 대체 글꼴 경계로 바꾼다 */
    public static java.awt.geom.Rectangle2D drawnBounds(Font font, String text, java.awt.font.FontRenderContext context) {
        Font face = font == null ? text(14, false) : font;
        java.awt.font.FontRenderContext frc = context == null
                ? new java.awt.font.FontRenderContext(new java.awt.geom.AffineTransform(), true, true) : context;
        if (text == null || text.isBlank()) return face.createGlyphVector(frc, "가Ag0").getVisualBounds();
        java.awt.geom.Rectangle2D union = null;
        float cursor = 0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\n' || c == '\t') {
                cursor += Math.max(1f, face.getSize() * 0.33f);
                continue;
            }
            Font glyphFace = faceFor(face, c, frc);
            java.awt.font.GlyphVector glyph = glyphFace.createGlyphVector(frc, String.valueOf(c));
            java.awt.geom.Rectangle2D box = glyph.getVisualBounds();
            float advance = (float) glyph.getGlyphPosition(glyph.getNumGlyphs()).getX();
            if (box.getWidth() > 0.2 && box.getHeight() > 0.2) {
                java.awt.geom.Rectangle2D placed = new java.awt.geom.Rectangle2D.Double(
                        cursor + box.getX(), box.getY(), box.getWidth(), box.getHeight());
                union = union == null ? placed : union.createUnion(placed);
            }
            cursor += Math.max(1f, advance);
        }
        return union == null ? face.createGlyphVector(frc, "가Ag0").getVisualBounds() : union;
    }

    public static int width(Graphics2D g, String text) {
        if (g == null || text == null || text.isEmpty()) return 0;
        Font chosen = g.getFont() == null ? text(14, false) : g.getFont();
        if (chosen.canDisplayUpTo(text) == -1) return g.getFontMetrics(chosen).stringWidth(text);
        Font alt = fallback(chosen);
        int measured = 0;
        int i = 0;
        while (i < text.length()) {
            boolean ok = chosen.canDisplay(text.charAt(i));
            int j = i + 1;
            while (j < text.length() && chosen.canDisplay(text.charAt(j)) == ok) j++;
            measured += g.getFontMetrics(ok ? chosen : alt).stringWidth(text.substring(i, j));
            i = j;
        }
        return measured;
    }

    public static void drawCentered(Graphics2D g, String text, int centerX, int baseline) {
        draw(g, text, centerX - width(g, text) / 2, baseline);
    }

    public static void drawRight(Graphics2D g, String text, int rightX, int baseline) {
        draw(g, text, rightX - width(g, text), baseline);
    }

    private static Font primary(String text, int size, boolean preferDigits) {
        int safe = Math.max(1, size);
        if (preferDigits && text != null && numericOnly(text)) return digits(safe);
        return text(safe, false);
    }

    private static Font fallback(Font chosen) {
        int style = chosen == null ? Font.PLAIN : chosen.getStyle();
        int size = chosen == null ? 12 : chosen.getSize();
        return new Font(Font.DIALOG, style, Math.max(1, size));
    }

    public static Font title(int windowHeight) {
        return text(Math.max(18, windowHeight / 14), true);
    }

    public static Font menu(int windowHeight) {
        return text(Math.max(14, windowHeight / 28), true);
    }

    public static Font label(int windowHeight) {
        return text(Math.max(11, windowHeight / 40), false);
    }

    public static Font popup(int windowHeight) {
        return text(Math.max(22, windowHeight / 12), true);
    }

    private static boolean numericOnly(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c) || c == ':' || c == '.' || c == ',' || c == ' ') continue;
            return false;
        }
        return true;
    }

    private static final java.util.Set<String> WARNED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static Font load(String path) {
        try (InputStream in = GameTheme.class.getResourceAsStream(path)) {
            if (in == null) {
                warnOnce(path, "클래스패스에 없음");
                return null;
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (FontFormatException | IOException e) {
            warnOnce(path, e.getClass().getSimpleName());
            return null;
        }
    }

    private static void warnOnce(String path, String reason) {
        if (path == null || !WARNED.add(path)) return;
        System.err.println("리소스를 열지 못했습니다: " + path + " (" + reason + ")");
    }
}
