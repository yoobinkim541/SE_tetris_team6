package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.HeadlessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.score.ScoreEntry;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.BattleMode;
import tetris.ui.raid.RaidPanel;
import tetris.ui.raid.RaidSamples;
import tetris.ui.theme.GameTheme;
import tetris.ui.versus.BattleKeyMap;
import tetris.ui.versus.BattlePanel;
import tetris.ui.versus.BattleResultPanel;
import tetris.ui.versus.P2pLobbyPanel;

/**
 * 디자인 시스템 가드. 화면은 테마 서체와 직접 그린 컨트롤만 쓴다.
 *
 * <p>소스 규칙 ({@code src/main/java/tetris/ui/**}). 예외 허용 목록은 이 테스트의
 * {@code SOURCE_EXCLUSIONS}에 적은 {@code versus/DevRttPanel.java}, {@code versus/BattleDemo.java} 둘뿐이다.
 * {@code UIManager} 기본 글꼴을 깔아도 이 소스 검사는 면제되지 않는다.
 * <ul>
 *   <li>{@code JOptionPane} 사용과 {@code JOptionPane} import, {@code new JButton}, {@code new JComboBox},
 *       {@code new JCheckBox}, {@code new JRadioButton}, {@code new JList} 는 금지.
 *       배열 생성 {@code new JButton[]} 은 위젯이 아니다.</li>
 *   <li>{@code GameTheme}·{@code UiKit} 밖에서 {@code new Font(}, {@code Font.decode},
 *       문자열 {@code "Dialog"} {@code "SansSerif"} {@code "Serif"} {@code "Monospaced"} 는 금지.</li>
 *   <li>입력용 재고 {@code new JTextField} 는 그 식의 가장 안쪽 클래스(익명 클래스 포함)가
 *       {@code setUI(} 를 두거나, {@code setOpaque(false)} 와 그 클래스가 선언한 {@code paintComponent} 를
 *       함께 가질 때만 허용한다. 바깥 클래스의 그리기는 안쪽 필드를 면제하지 않는다.</li>
 * </ul>
 *
 * <p>런타임 규칙: 사용자 화면을 기존 UI 테스트처럼 패널로 만들고 컴포넌트 트리를 걷는다.
 * 보이는 재고 {@code JButton}/{@code JComboBox} 는 Look and Feel UI 가 아니면 실패한다.
 * 보이는 {@code JTextField} 는 커스텀 UI 이거나, 불투명하지 않고 {@code paintComponent} 를 선언한 서브클래스일 때만 통과한다.
 * 글꼴이 있는 컴포넌트는 패밀리(또는 글꼴 이름)가 {@link GameTheme} 서체 — Do Hyeon, Black Han Sans, Orbitron,
 * 파일이 없을 때의 공개 폴백 — 가 아니면 실패한다. 글꼴이 아직 없는 컴포넌트는 패밀리 위반으로 세지 않는다.
 * headless 에서 만들 수 없는 화면은 이유와 함께 건너뛰고, 조용히 통과시키지 않는다.
 */
class DesignSystemGuardTest {
    /** 소스 검사에서만 빼는 개발 도구. 이 둘 외에는 예외가 없다. */
    private static final Set<String> SOURCE_EXCLUSIONS = Set.of(
            "versus/DevRttPanel.java",
            "versus/BattleDemo.java");
    private static final Pattern TYPE_DECL = Pattern.compile("\\b(?:class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern NEW_FONT = Pattern.compile("new\\s+(?:[\\w.]+\\.)?Font\\s*\\(");
    private static final Pattern FONT_DECODE = Pattern.compile("\\bFont\\s*\\.\\s*decode\\s*\\(");
    private static final Pattern JOPTION = Pattern.compile("\\bJOptionPane\\b");
    private static final String[] LOGICAL_FACES = {"Dialog", "SansSerif", "Serif", "Monospaced"};
    private static final Pattern SET_UI = Pattern.compile("setUI\\s*\\(");
    private static final Pattern OPAQUE_FALSE = Pattern.compile("setOpaque\\s*\\(\\s*false\\s*\\)");
    private static final Pattern PAINT_DECL = Pattern.compile("\\bvoid\\s+paintComponent\\s*\\(");
    private static final String[] STOCK_TYPES = {
            "JButton", "JComboBox", "JCheckBox", "JRadioButton", "JList", "JTextField"
    };

    @org.junit.jupiter.api.Test
    void sourceScanRejectsStockWidgetsAndOffThemeFonts() throws Exception {
        assertScannerRules();
        Path root = uiRoot();
        List<String> violations = new ArrayList<>();
        boolean sawMainFrame = false;
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                Path relative = root.relativize(file);
                if (excluded(relative)) continue;
                if (relative.getFileName().toString().equals("MainFrame.java")) sawMainFrame = true;
                String source = Files.readString(file);
                violations.addAll(scanSource(displayPath(relative), source, fontAuthority(relative)));
            }
        }
        assertTrue(sawMainFrame, "ui 소스를 찾지 못했다: " + root);
        violations.sort(Comparator.naturalOrder());
        writeReport("design-system-guard-source.txt", String.join("\n", violations));
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @org.junit.jupiter.api.Test
    void runtimeWalkRejectsStockWidgetsAndOffThemeFonts() throws Exception {
        List<String> violations = new ArrayList<>();
        List<String> skips = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> walkScreens(violations, skips));
        violations.sort(Comparator.naturalOrder());
        skips.sort(Comparator.naturalOrder());
        for (String skip : skips) System.out.println("design-system skip: " + skip);
        String message = formatReport(violations, skips);
        writeReport("design-system-guard-runtime.txt", message);
        assertTrue(violations.isEmpty(), message);
    }

    /** 스캐너가 규칙을 느슨하게 읽지 않는지. 저장소가 깨끗해져도 이 검사는 남는다. */
    private static void assertScannerRules() {
        assertFalse(scanSource("Fake.java", "class Fake {\n  JButton b = new JButton(\"x\");\n}\n", false).isEmpty());
        assertFalse(scanSource("Fake.java", "class Fake {\n  Font f = new Font(Font.DIALOG, Font.PLAIN, 12);\n}\n", false).isEmpty());
        assertFalse(scanSource("theme/NineSlice.java", "class NineSlice {\n  Font f = new Font(Font.DIALOG, Font.PLAIN, 12);\n}\n", false).isEmpty());
        assertTrue(scanSource("theme/GameTheme.java", "class GameTheme {\n  Font f = new Font(Font.DIALOG, Font.PLAIN, 12);\n  String face = \"Dialog\";\n}\n", true).isEmpty());
        assertTrue(scanSource("theme/UiKit.java", "class UiKit {\n  Font f = Font.decode(\"Monospaced\");\n}\n", true).isEmpty());
        assertFalse(scanSource("Fake.java", "class Fake {\n  JTextField f = new JTextField();\n  void paint() { setOpaque(false); }\n}\n", false).isEmpty());
        assertTrue(scanSource("Fake.java", """
                class Fake {
                  JTextField f = new JTextField();
                  void init() { setOpaque(false); }
                  protected void paintComponent(java.awt.Graphics g) {}
                }
                """, false).isEmpty());
        assertTrue(scanSource("Fake.java", """
                class Fake {
                  JTextField f = new JTextField();
                  void init() { f.setUI(f.getUI()); }
                }
                """, false).isEmpty());
        assertFalse(scanSource("Fake.java", """
                class Outer {
                  JTextField f = new JTextField();
                  class Inner {
                    void init() { setOpaque(false); }
                    protected void paintComponent(java.awt.Graphics g) {}
                  }
                }
                """, false).isEmpty());
        assertTrue(scanSource("Fake.java", """
                class Outer {
                  class Inner {
                    JTextField f = new JTextField();
                    void init() { setOpaque(false); }
                    protected void paintComponent(java.awt.Graphics g) {}
                  }
                }
                """, false).isEmpty());
        assertFalse(scanSource("Fake.java", "class Fake { void ask() { JOptionPane.showMessageDialog(null, \"x\"); } }\n", false).isEmpty());
        assertTrue(scanSource("Fake.java", "class Fake { JButton[] buttons = new JButton[] { existing }; }\n", false).isEmpty());
        assertTrue(scanSource("Fake.java", "class Fake { // new JButton(\n  int x;\n}\n", false).isEmpty());
        assertTrue(excluded(Path.of("versus/DevRttPanel.java")));
        assertTrue(excluded(Path.of("versus/BattleDemo.java")));
        assertFalse(excluded(Path.of("versus/P2pLobbyPanel.java")));
        assertFalse(fontAuthority(Path.of("Theme.java")));
        assertFalse(fontAuthority(Path.of("theme/NineSlice.java")));
        assertTrue(fontAuthority(Path.of("theme/GameTheme.java")));
        assertTrue(fontAuthority(Path.of("theme/UiKit.java")));
        assertFalse(scanSource("Theme.java", "class T { String face = \"Dialog\"; }\n", false).isEmpty());
        assertFalse(scanSource("Theme.java", "class T { String face = \"SansSerif\"; }\n", false).isEmpty());
        assertFalse(scanSource("Theme.java", "class T { String face = \"Serif\"; }\n", false).isEmpty());
        assertFalse(scanSource("Theme.java", "class T { Font.decode(\"Monospaced\"); }\n", false).isEmpty());
        assertTrue(scanSource("Theme.java", "class T { // \"Dialog\"\n  int x;\n}\n", false).isEmpty());
        assertFalse(scanSource("MainFrame.java", "import javax.swing.JOptionPane;\nclass M {}\n", false).isEmpty());
    }

    private static void walkScreens(List<String> violations, List<String> skips) {
        Set<String> families = themeFamilies();
        if (families.isEmpty()) {
            violations.add("runtime → GameTheme 서체 패밀리를 읽지 못했다");
        }
        skips.add("main-frame — JFrame 은 java.awt.headless=true 에서 HeadlessException 이라 "
                + "MainFrame 카드 전환은 만들지 않는다. 각 화면은 기존 UI 테스트처럼 패널을 직접 만든다.");
        Runnable noop = () -> { };
        open("main-menu", () -> new MainMenuPanel(MainFrame.mainMenuItems(noop, noop, noop, noop, noop, noop)),
                violations, skips, families);
        open("solo-menu", () -> new MainMenuPanel(
                MainFrame.soloMenuItems(noop, noop, noop), noop, "솔로 플레이", () -> false),
                violations, skips, families);
        open("multi-menu", () -> new MainMenuPanel(
                MainFrame.multiMenuItems(noop, noop, noop, noop, noop, noop, noop), noop, "멀티플레이", () -> false),
                violations, skips, families);
        open("settings", () -> new SettingPanel(new Settings(), () -> true, () -> true, noop),
                violations, skips, families);
        open("settings-keys", () -> {
            SettingPanel panel = new SettingPanel(new Settings(), () -> true, () -> true, noop);
            panel.openControlKeys();
            return panel;
        }, violations, skips, families);
        open("scoreboard", () -> {
            ScoreBoardPanel panel = new ScoreBoardPanel(noop, noop);
            panel.show(List.of(new ScoreEntry("홍길동", 1200, 3, 10, Instant.EPOCH)), 0, true);
            return panel;
        }, violations, skips, families);
        open("game-over", () -> {
            GameOverPanel panel = new GameOverPanel(noop);
            panel.show(new GameSnapshot(new int[20][10], null, null, 1200, 3, 10, GameState.GAME_OVER));
            return panel;
        }, violations, skips, families);
        open("name-input", () -> {
            NameInputPanel panel = new NameInputPanel(name -> { });
            panel.prepare(1200);
            return panel;
        }, violations, skips, families);
        open("pause", () -> new PausePanel(noop, noop, noop), violations, skips, families);
        open("confirm", () -> new ConfirmDialog("정말 종료할까요?", noop, noop), violations, skips, families);
        open("single-game", () -> new GamePanel(new Settings(), new PausePanel(noop, noop, noop), snapshot -> { }),
                violations, skips, families);
        open("single-game-paused", () -> {
            GamePanel panel = new GamePanel(new Settings(), new PausePanel(noop, noop, noop), snapshot -> { });
            panel.onChanged(new GameSnapshot(new int[20][10], null, null, 1200, 3, 10, GameState.PAUSED));
            return panel;
        }, violations, skips, families);
        open("single-game-confirm", () -> {
            GamePanel panel = new GamePanel(new Settings(), new PausePanel(noop, noop, noop), snapshot -> { });
            panel.showConfirm(new ConfirmDialog("다시 시작할까요?", noop, noop));
            return panel;
        }, violations, skips, families);
        open("versus", () -> new BattlePanel(false, new BattleKeyMap().guide(), "로컬 일반 대전", BattleMode.CLASSIC, null),
                violations, skips, families);
        open("versus-result", () -> new BattleResultPanel(noop, noop), violations, skips, families);
        open("p2p-result", () -> new BattleResultPanel("로비로", noop, "다시 대전", noop, noop),
                violations, skips, families);
        open("raid-cards", () -> {
            RaidPanel panel = new RaidPanel(false);
            panel.setSnapshot(RaidSamples.phase(RaidMode.COOP, RaidPhase.CARD_PICK));
            return panel;
        }, violations, skips, families);
        open("raid-result", () -> {
            RaidPanel panel = new RaidPanel(false);
            panel.setSnapshot(RaidSamples.phase(RaidMode.SOLO, RaidPhase.FINISHED));
            return panel;
        }, violations, skips, families);
        open("p2p-lobby", () -> new P2pLobbyPanel(new P2pLobbyPanel.Actions() {
            @Override public void host(String port) { }
            @Override public void connect(String address, String port) { }
            @Override public void selectMode(BattleMode mode) { }
            @Override public void selectRaid(tetris.feature.raid.api.RaidMode mode) { }
            @Override public void toggleReady() { }
            @Override public void cancelOrBack() { }
            @Override public void hostInternet() { }
            @Override public void joinInternet(String code) { }
            @Override public void refreshRooms() { }
            @Override public void joinPublic(int routeIndex, String id) { }
        }), violations, skips, families);
    }

    private static void open(String screen, ScreenFactory factory, List<String> violations, List<String> skips,
                             Set<String> families) {
        Component root;
        try {
            root = factory.create();
        } catch (HeadlessException e) {
            skips.add(screen + " — HeadlessException: " + e.getMessage());
            return;
        } catch (RuntimeException e) {
            skips.add(screen + " — 생성 실패: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }
        if (root == null) {
            skips.add(screen + " — 생성 결과가 null");
            return;
        }
        root.setSize(960, 720);
        if (root instanceof Container container) container.doLayout();
        walk(screen, root, "", violations, families);
    }

    private static void walk(String screen, Component component, String path, List<String> violations, Set<String> families) {
        if (component == null || !component.isVisible()) return;
        String here = path + "/" + component.getClass().getSimpleName();
        String text = shownText(component);
        if (text != null) here += "[\"" + text + "\"]";
        if (component instanceof JButton || component instanceof JComboBox || component instanceof JTextField) {
            JComponent widget = (JComponent) component;
            if (!allowedWidget(widget)) {
                String ui = widget.getUI() == null ? "null" : widget.getUI().getClass().getName();
                violations.add(screen + " → " + here + " stock " + component.getClass().getSimpleName() + " UI=" + ui);
            }
        }
        Font font = component.getFont();
        if (font != null && !themeFont(font, families)) {
            violations.add(screen + " → " + here + " font family \"" + font.getFamily()
                    + "\" name \"" + font.getName() + "\"");
        }
        if (component instanceof Container container) {
            Component[] children = container.getComponents();
            for (int i = 0; i < children.length; i++) {
                walk(screen, children[i], here + "#" + (i + 1), violations, families);
            }
        }
    }

    /** 재고 버튼·콤보는 커스텀 UI 만 통과. 입력 필드는 소스 규칙과 같이 불투명 해제 + paintComponent 도 통과. */
    private static boolean allowedWidget(JComponent component) {
        if (!stockUi(component)) return true;
        return component instanceof JTextField && !component.isOpaque() && declaresPaintComponent(component.getClass());
    }

    private static boolean stockUi(JComponent component) {
        if (component.getUI() == null) return true;
        String name = component.getUI().getClass().getName();
        return name.startsWith("javax.swing.plaf.")
                || name.startsWith("com.sun.java.swing.plaf.")
                || name.startsWith("sun.swing.");
    }

    private static boolean declaresPaintComponent(Class<?> type) {
        for (Class<?> cursor = type; cursor != null && cursor != JComponent.class; cursor = cursor.getSuperclass()) {
            try {
                cursor.getDeclaredMethod("paintComponent", Graphics.class);
                return true;
            } catch (NoSuchMethodException ignored) {
                // 상위 클래스를 계속 본다
            }
        }
        return false;
    }

    private static boolean themeFont(Font font, Set<String> families) {
        return families.contains(font.getFamily())
                || families.contains(font.getFamily(Locale.ENGLISH))
                || families.contains(font.getName())
                || families.contains(font.getFontName(Locale.ENGLISH));
    }

    private static Set<String> themeFamilies() {
        Set<String> names = new LinkedHashSet<>();
        remember(names, GameTheme.text(16, false));
        remember(names, GameTheme.text(16, true));
        remember(names, GameTheme.digits(16));
        remember(names, GameTheme.title(720));
        remember(names, GameTheme.menu(720));
        remember(names, GameTheme.label(720));
        remember(names, GameTheme.popup(720));
        remember(names, displayFont());
        Font display = displayFont();
        if (display != null) remember(names, display.deriveFont(Font.BOLD, 16f));
        return names;
    }

    private static void remember(Set<String> names, Font font) {
        if (font == null) return;
        names.add(font.getFamily());
        names.add(font.getFamily(Locale.ENGLISH));
        names.add(font.getName());
        names.add(font.getFontName(Locale.ENGLISH));
    }

    private static Font displayFont() {
        try {
            var field = GameTheme.class.getDeclaredField("DISPLAY");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Font font) return font;
        } catch (ReflectiveOperationException ignored) {
            // 필드가 없으면 리소스 경로로 다시 읽는다
        }
        try (var in = GameTheme.class.getResourceAsStream("/fonts/BlackHanSans-Regular.ttf")) {
            if (in == null) return null;
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String shownText(Component component) {
        String text = null;
        if (component instanceof JLabel label) text = label.getText();
        else if (component instanceof AbstractButton button) text = button.getText();
        else if (component instanceof JTextField field) text = field.getText();
        else if (component instanceof JComboBox<?> box && box.getSelectedItem() != null) text = String.valueOf(box.getSelectedItem());
        if (text == null) return null;
        text = text.replace('\n', ' ').replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return null;
        return text.length() <= 40 ? text : text.substring(0, 37) + "...";
    }

    static List<String> scanSource(String displayPath, String source, boolean fontAuthority) {
        String code = maskNonCode(source);
        List<Body> bodies = classBodies(code);
        List<String> violations = new ArrayList<>();
        if (!fontAuthority) {
            collect(violations, displayPath, code, NEW_FONT, "new Font");
            collect(violations, displayPath, code, FONT_DECODE, "Font.decode");
            collectLogicalFaces(violations, displayPath, source);
        }
        Matcher options = JOPTION.matcher(code);
        while (options.find()) {
            String label = joptionImport(source, options.start()) ? "JOptionPane import" : "JOptionPane";
            violations.add(at(displayPath, code, options.start()) + " " + label);
        }
        for (String type : STOCK_TYPES) {
            Pattern pattern = Pattern.compile("new\\s+(?:[\\w.]+\\.)?" + type + "\\s*[<(]");
            Matcher matcher = pattern.matcher(code);
            while (matcher.find()) {
                if ("JTextField".equals(type) && textFieldAllowed(code, bodies, matcher.start())) continue;
                violations.add(at(displayPath, code, matcher.start()) + " new " + type);
            }
        }
        return violations;
    }

    private static void collectLogicalFaces(List<String> violations, String displayPath, String source) {
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                i = Math.min(n, i + 2);
                continue;
            }
            if (c == '\'') {
                i++;
                while (i < n) {
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        i += 2;
                        continue;
                    }
                    if (source.charAt(i) == '\'' || source.charAt(i) == '\n') {
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == '"') {
                int start = i;
                if (i + 2 < n && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                    i += 3;
                    int content = i;
                    while (i + 2 < n && !(source.charAt(i) == '"' && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"')) i++;
                    String body = source.substring(content, Math.min(i, n)).strip();
                    if (logicalFace(body)) violations.add(at(displayPath, source, start) + " \"" + body + "\"");
                    i = Math.min(n, i + 3);
                    continue;
                }
                i++;
                StringBuilder body = new StringBuilder();
                while (i < n) {
                    char d = source.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        body.append(source.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (d == '"' || d == '\n') break;
                    body.append(d);
                    i++;
                }
                if (logicalFace(body.toString())) violations.add(at(displayPath, source, start) + " \"" + body + "\"");
                if (i < n && source.charAt(i) == '"') i++;
                continue;
            }
            i++;
        }
    }

    private static boolean logicalFace(String text) {
        for (String face : LOGICAL_FACES) if (face.equals(text)) return true;
        return false;
    }

    private static boolean joptionImport(String source, int index) {
        int start = source.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
        int end = source.indexOf('\n', index);
        if (end < 0) end = source.length();
        String line = source.substring(start, end).trim();
        return line.startsWith("import ") && line.contains("JOptionPane");
    }

    private static boolean textFieldAllowed(String code, List<Body> bodies, int index) {
        Body body = innermost(bodies, index);
        if (body == null) return false;
        String own = ownText(code, bodies, body);
        if (SET_UI.matcher(own).find()) return true;
        return OPAQUE_FALSE.matcher(own).find() && PAINT_DECL.matcher(own).find();
    }

    private static String ownText(String code, List<Body> bodies, Body body) {
        StringBuilder own = new StringBuilder(code.substring(body.open, body.end));
        for (Body nested : bodies) {
            if (nested.open <= body.open || nested.end > body.end || nested == body) continue;
            int from = nested.open - body.open;
            int to = Math.min(own.length(), nested.end - body.open);
            for (int i = Math.max(0, from); i < to; i++) {
                if (own.charAt(i) != '\n') own.setCharAt(i, ' ');
            }
        }
        return own.toString();
    }

    private static Body innermost(List<Body> bodies, int index) {
        Body best = null;
        for (Body body : bodies) {
            if (index < body.open || index > body.end) continue;
            if (best == null || body.end - body.open < best.end - best.open) best = body;
        }
        return best;
    }

    private static List<Body> classBodies(String code) {
        List<Body> bodies = new ArrayList<>();
        Matcher named = TYPE_DECL.matcher(code);
        while (named.find()) {
            int brace = code.indexOf('{', named.end());
            if (brace < 0) continue;
            int end = match(code, brace, '{', '}');
            if (end > brace) bodies.add(new Body(brace, end));
        }
        for (int i = 0; i < code.length() - 3; i++) {
            if (!code.startsWith("new", i)) continue;
            if (i > 0 && Character.isJavaIdentifierPart(code.charAt(i - 1))) continue;
            int type = skipSpace(code, i + 3);
            if (type >= code.length() || !Character.isJavaIdentifierStart(code.charAt(type))) continue;
            int cursor = type;
            while (cursor < code.length()) {
                char c = code.charAt(cursor);
                if (Character.isJavaIdentifierPart(c) || c == '.' || c == '<' || c == '>' || c == ',' || c == '?' || c == ' ') {
                    cursor++;
                    continue;
                }
                break;
            }
            cursor = skipSpace(code, cursor);
            if (cursor >= code.length() || code.charAt(cursor) != '(') continue;
            int paren = match(code, cursor, '(', ')');
            if (paren < 0) continue;
            int brace = skipSpace(code, paren + 1);
            if (brace < code.length() && code.charAt(brace) == '{') {
                int end = match(code, brace, '{', '}');
                if (end > brace) bodies.add(new Body(brace, end));
                i = brace;
            }
        }
        return bodies;
    }

    private static void collect(List<String> violations, String displayPath, String code, Pattern pattern, String label) {
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) violations.add(at(displayPath, code, matcher.start()) + " " + label);
    }

    private static String at(String displayPath, String code, int index) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < index && i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                line++;
                column = 1;
            } else column++;
        }
        return displayPath + ":" + line + ":" + column;
    }

    private static String maskNonCode(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '"' && i + 2 < n && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                out.append("\"\"\"");
                i += 3;
                while (i < n && !(source.charAt(i) == '"' && i + 2 < n && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("\"\"\"");
                    i += 3;
                }
                continue;
            }
            if (c == '"') {
                out.append('"');
                i++;
                while (i < n) {
                    char d = source.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        out.append(' ');
                        out.append(source.charAt(i + 1) == '\n' ? '\n' : ' ');
                        i += 2;
                        continue;
                    }
                    if (d == '"' || d == '\n') {
                        out.append(d);
                        i++;
                        break;
                    }
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '\'') {
                out.append('\'');
                i++;
                while (i < n) {
                    char d = source.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        out.append(' ');
                        out.append(' ');
                        i += 2;
                        continue;
                    }
                    if (d == '\'' || d == '\n') {
                        out.append(d);
                        i++;
                        break;
                    }
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static int match(String code, int open, char left, char right) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == left) depth++;
            else if (c == right && --depth == 0) return i;
        }
        return -1;
    }

    private static int skipSpace(String code, int index) {
        while (index < code.length() && Character.isWhitespace(code.charAt(index))) index++;
        return index;
    }

    private static boolean excluded(Path relative) {
        return SOURCE_EXCLUSIONS.contains(relative.toString().replace('\\', '/'));
    }

    /** {@code new Font}·논리 글꼴 문자열을 둘 수 있는 파일. GameTheme와 UiKit뿐. */
    private static boolean fontAuthority(Path relative) {
        String path = relative.toString().replace('\\', '/');
        return path.equals("theme/GameTheme.java") || path.equals("theme/UiKit.java");
    }

    private static Path uiRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path ui = dir.resolve("src/main/java/tetris/ui");
            if (Files.isDirectory(ui)) return ui;
            dir = dir.getParent();
        }
        throw new IllegalStateException("src/main/java/tetris/ui 를 찾지 못했다");
    }

    private static String displayPath(Path relative) {
        return "src/main/java/tetris/ui/" + relative.toString().replace('\\', '/');
    }

    private static String formatReport(List<String> violations, List<String> skips) {
        StringBuilder report = new StringBuilder();
        report.append("allowed font families: ").append(themeFamilies()).append('\n');
        if (!skips.isEmpty()) {
            report.append("skipped:\n");
            for (String skip : skips) report.append(skip).append('\n');
        }
        report.append("violations:\n");
        for (String violation : violations) report.append(violation).append('\n');
        return report.toString();
    }

    private static void writeReport(String name, String body) {
        try {
            Path out = Path.of("build").resolve(name);
            Files.createDirectories(out.getParent());
            Files.writeString(out, body == null ? "" : body);
        } catch (Exception ignored) {
            // 보고서 파일은 보조다. 단언 메시지가 본 목록이다.
        }
    }


    @FunctionalInterface
    private interface ScreenFactory {
        Component create();
    }

    private record Body(int open, int end) {
    }
}
