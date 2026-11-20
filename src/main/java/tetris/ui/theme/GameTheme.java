package tetris.ui.theme;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;

/**
 * 화면 공통 서체와 크기 토큰. Do Hyeon은 제목·메뉴·라벨, Orbitron은 숫자.
 * 파일을 못 열면 기존 논리 글꼴로 돌아간다.
 */
public final class GameTheme {
    private static final String DO_HYEON_PATH = "/fonts/DoHyeon-Regular.ttf";
    private static final String ORBITRON_PATH = "/fonts/Orbitron.ttf";

    private static final Font DO_HYEON = load(DO_HYEON_PATH);
    private static final Font ORBITRON = load(ORBITRON_PATH);

    static {
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (DO_HYEON != null) environment.registerFont(DO_HYEON);
        if (ORBITRON != null) environment.registerFont(ORBITRON);
    }

    private GameTheme() {
    }

    public static boolean doHyeonLoaded() {
        return DO_HYEON != null;
    }

    public static boolean orbitronLoaded() {
        return ORBITRON != null;
    }

    /** 한글이 나오는 본문. 굵기는 Do Hyeon에 굵은 컷이 없어 합성 굵기만 쓴다 */
    public static Font text(int size, boolean bold) {
        int safe = Math.max(1, size);
        if (DO_HYEON != null) return DO_HYEON.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) safe);
        return new Font(Font.DIALOG, bold ? Font.BOLD : Font.PLAIN, safe);
    }

    /** 점수·시간·레벨 숫자 */
    public static Font digits(int size) {
        int safe = Math.max(1, size);
        if (ORBITRON != null) return ORBITRON.deriveFont(Font.PLAIN, (float) safe);
        return new Font(Font.DIALOG, Font.BOLD, safe);
    }

    /**
     * 숫자만 Orbitron. 한글이 섞이면 Do Hyeon. 그 글꼴이 못 그리는 글자가 있으면 Dialog.
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

    private static Font load(String path) {
        try (InputStream in = GameTheme.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (FontFormatException | IOException e) {
            return null;
        }
    }
}
