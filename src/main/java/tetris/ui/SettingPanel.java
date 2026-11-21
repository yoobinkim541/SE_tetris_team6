package tetris.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.JPanel;
import tetris.DevMode;
import tetris.feature.game.Difficulty;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;
import tetris.feature.setting.ControlStyle;

/**
 * 설정 화면 (SET-2, SET-8): Screen Size / Difficulty / Control Keys / Color Blind / 조작 방식 / Reset.
 * 보인다(일반 실행 화면은 2차 설정 화면과 같다. 켜져 있으면 끌 수 있어야 하므로 보인다).
 * 유효한 변경은 즉시 적용·저장한다 (SET-3). 저장 실패 시 이번 실행에는 적용하고 안내만 한다 (SET-4).
 */
public class SettingPanel extends JPanel {
    private enum Item {
        SCREEN_SIZE, DIFFICULTY, CONTROL_KEYS, COLOR_BLIND, CONTROL_STYLE, EFFECTS, SFX, BGM, DEV_NET_OVERLAY, RESET_SCOREBOARD, RESET_SETTINGS
    }

    private static final Item[] ALL_ITEMS = Item.values();
    private static final GameAction[] ACTIONS = GameAction.values();

    private final Settings settings;
    private final BooleanSupplier onChanged;
    private final BooleanSupplier onResetScoreboard;
    private final Runnable onBack;
    private final BooleanSupplier devFlag;

    private boolean inControlKeys;   // Control Keys 하위 화면을 보고 있는지
    private int selected;            // 지금 보이는 목록에서의 선택
    private int listSelection;       // 하위 화면에 들어가기 전 설정 목록의 선택 (돌아올 때 복원)
    private GameAction capturing;    // 새 키 입력 대기 중인 기능, 없으면 null
    private KeyEventDispatcher keyCapture;
    private ConfirmDialog confirmDialog;
    private String notice = "";
    private boolean noticeIsError;   // 경고색으로 그릴지 (거부·저장 실패)
    private final tetris.ui.input.Hits hits = new tetris.ui.input.Hits();
    /**
     * onChanged: 설정 값이 바뀐 뒤 호출. 화면 반영과 저장을 하고 저장 성공 여부를 돌려준다.
     * onResetScoreboard: 스코어보드를 비우고 저장 성공 여부를 돌려준다. onBack: Main Menu로 (Esc)
     */
    public SettingPanel(Settings settings, BooleanSupplier onChanged, BooleanSupplier onResetScoreboard, Runnable onBack) {
        this(settings, onChanged, onResetScoreboard, onBack, DevMode::flagEnabled);
    }

    /** devFlag: {@code --dev}로 실행했는지. 켜져 있으면 개발자 항목을 보인다 (테스트는 값을 직접 넘긴다) */
    public SettingPanel(Settings settings, BooleanSupplier onChanged, BooleanSupplier onResetScoreboard, Runnable onBack,
                        BooleanSupplier devFlag) {
        if (settings == null || onChanged == null || onResetScoreboard == null || onBack == null || devFlag == null)
            throw new IllegalArgumentException("arguments cannot be null");
        this.settings = settings;
        this.onChanged = onChanged;
        this.onResetScoreboard = onResetScoreboard;
        this.onBack = onBack;
        this.devFlag = devFlag;

        setLayout(null);
        setBackground(Theme.BACKGROUND);
        setFocusable(true);

        BooleanSupplier listActive = () -> confirmDialog == null && capturing == null;
        KeyBindingManager.bindKey(this, KeyEvent.VK_UP, listActive, () -> moveSelection(-1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_DOWN, listActive, () -> moveSelection(1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_LEFT, listActive, () -> changeValue(-1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_RIGHT, listActive, () -> changeValue(1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, listActive, this::activateSelected);
        KeyBindingManager.bindKey(this, KeyEvent.VK_ESCAPE, listActive, this::back);
        tetris.ui.input.MenuMouse.install(this, hits, this::hoverHit, this::clickHit);
    }

    /** 화면에 들어올 때마다 설정 목록 맨 위에서 시작 */
    public void reset() {
        cancelKeyCapture();
        closeConfirm();
        inControlKeys = false;
        selected = 0;
        notice = "";
        repaint();
    }

    public void moveSelection(int delta) {
        selected = Theme.cycle(selected, delta, inControlKeys ? ACTIONS.length : items().size());
        notice = "";
        repaint();
    }

    /** Enter: 순환/토글/하위 화면/확인창 */
    public void activateSelected() {
        if (inControlKeys) {
            beginKeyCapture(ACTIONS[selected]);
            return;
        }
        notice = "";
        switch (items().get(selected)) {
            case SCREEN_SIZE -> changeScreenSize(1);
            case DIFFICULTY -> changeDifficulty(1);
            case CONTROL_KEYS -> openControlKeys();
            case COLOR_BLIND -> {
                settings.setColorBlindMode(!settings.isColorBlindMode());
                applyChange("");
            }
            case CONTROL_STYLE -> changeControlStyle(1);
            case EFFECTS -> changeEffects(1);
            case SFX -> toggleSfx();
            case BGM -> toggleBgm();
            case DEV_NET_OVERLAY -> {
                settings.setDevNetworkOverlay(!settings.isDevNetworkOverlay());
                applyChange("");
            }
            case RESET_SCOREBOARD -> confirm(Messages.get("confirm.resetScoreboard"), () -> {
                boolean saved = onResetScoreboard.getAsBoolean();
                setNotice(Messages.get(saved ? "settings.scoreboardCleared" : "scoreboard.saveFailed"), !saved);
            });
            case RESET_SETTINGS -> confirm(Messages.get("confirm.resetSettings"), () -> {
                settings.resetToDefaults();
                applyChange(Messages.get("settings.settingsReset"));
            });
        }
        clampSelection(); // 개발자 항목을 끄거나 설정을 초기화하면 목록이 한 줄 줄어든다
        repaint();
    }

    /** Control Keys 하위 화면(7개 기능과 현재 키 목록)을 연다 */
    public void openControlKeys() {
        listSelection = selected;
        inControlKeys = true;
        selected = 0;
        notice = "";
        repaint();
    }

    /** 하위 화면을 닫고 설정 목록으로 돌아간다 (Esc) */
    public void closeControlKeys() {
        cancelKeyCapture();
        inControlKeys = false;
        selected = listSelection;
        notice = "";
        repaint();
    }

    /** 선택한 기능의 새 키 입력 대기 시작: "새 키를 누르세요 (Esc 취소)" 표시 */
    public void beginKeyCapture(GameAction action) {
        cancelKeyCapture();
        capturing = action;
        setNotice(Messages.get("settings.pressKey"), false);
        // 대기 중에는 모든 키 눌림을 가로채 메뉴 바인딩으로 가지 않게 한다. 예약키도 받아야 안내할 수 있다
        keyCapture = e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) cancelKeyCapture();
                else captureKey(e.getKeyCode());
            }
            return true;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyCapture);
        repaint();
    }

    /** 새 키 입력 대기 취소 (Esc). 키 설정은 바뀌지 않는다 */
    public void cancelKeyCapture() {
        if (keyCapture != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyCapture);
            keyCapture = null;
        }
        if (capturing != null) notice = "";
        capturing = null;
        repaint();
    }

    /** Control Keys: 새 키 입력을 받아 KeyMap.assign 결과에 따라 안내 문구 표시 */
    public void captureKey(int keyCode) {
        if (capturing == null) return;
        GameAction action = capturing;
        cancelKeyCapture();

        KeyMap keyMap = settings.getKeyMap();
        switch (keyMap.assign(action, keyCode)) {
            case OK -> applyChange(Messages.get("settings.keyChanged"));
            case UNCHANGED -> notice = "";
            case RESERVED_KEY -> setNotice(Messages.get("settings.reservedKey"), true);
            case DUPLICATE -> setNotice(
                    Messages.format("settings.duplicateKey", Messages.actionName(keyMap.actionOf(keyCode))), true);
        }
        repaint();
    }

    boolean isCapturing() {
        return capturing != null;
    }

    boolean isInControlKeys() {
        return inControlKeys;
    }

    String getNotice() {
        return notice;
    }

    /** ←/→: 선택한 항목이 순환형일 때만 값을 바꾼다. 설정 목록에서만 */
    public void changeValue(int delta) {
        if (inControlKeys) return;
        notice = "";
        switch (items().get(selected)) {
            case SCREEN_SIZE -> changeScreenSize(delta);
            case DIFFICULTY -> changeDifficulty(delta);
            case CONTROL_STYLE -> changeControlStyle(delta);
            case EFFECTS -> changeEffects(delta);
            case SFX -> toggleSfx();
            case BGM -> toggleBgm();
            default -> { }
        }
    }

    // Small -> Medium -> Large -> 전체 화면 순환. 창 크기는 전체 화면을 꺼면 돌아온다
    private void changeScreenSize(int delta) {
        int index = settings.isFullscreen() ? 3 : settings.getScreenSize().ordinal();
        int next = Theme.cycle(index, delta, 4);
        if (next == 3) settings.setFullscreen(true);
        else {
            settings.setFullscreen(false);
            settings.setScreenSize(ScreenSize.values()[next]);
        }
        applyChange("");
    }

    private String screenSizeLabel() {
        return Messages.get(settings.isFullscreen() ? "size.FULLSCREEN" : "size." + settings.getScreenSize().name());
    }

    // Easy -> Normal -> Hard 순환 (SET-8). 다음 게임부터 적용된다
    private void changeDifficulty(int delta) {
        Difficulty[] difficulties = Difficulty.values();
        settings.setDifficulty(difficulties[Theme.cycle(settings.getDifficulty().ordinal(), delta, difficulties.length)]);
        applyChange("");
    }

    private void changeControlStyle(int delta) {
        ControlStyle[] styles = ControlStyle.values();
        settings.setControlStyle(styles[Theme.cycle(settings.getControlStyle().ordinal(), delta, styles.length)]);
        applyChange("");
    }

    private void changeEffects(int delta) {
        if (delta == 0) return;
        settings.setEffectsHigh(!settings.isEffectsHigh());
        applyChange("");
    }

    private void toggleSfx() {
        settings.setSfxEnabled(!settings.isSfxEnabled());
        applyChange("");
    }

    private void toggleBgm() {
        settings.setBgmEnabled(!settings.isBgmEnabled());
        applyChange("");
    }

    private void applyChange(String successNotice) {
        boolean saved = onChanged.getAsBoolean();
        setNotice(saved ? successNotice : Messages.get("settings.saveFailed"), !saved);
        clampSelection();
        repaint();
    }

    /** 지금 보이는 설정 항목. 개발자 항목은 --dev 실행이거나 이미 켜져 있을 때만 들어간다 */
    private List<Item> items() {
        boolean showDev = devFlag.getAsBoolean() || settings.isDevNetworkOverlay();
        List<Item> visible = new ArrayList<>(ALL_ITEMS.length);
        for (Item item : ALL_ITEMS) {
            if (item != Item.DEV_NET_OVERLAY || showDev) visible.add(item);
        }
        return visible;
    }

    private void clampSelection() {
        if (!inControlKeys) selected = Math.min(selected, items().size() - 1);
    }

    private void setNotice(String text, boolean isError) {
        notice = text;
        noticeIsError = isError;
    }

    private void back() {
        if (inControlKeys) closeControlKeys();
        else onBack.run();
    }

    // 기본 선택 No. Yes면 onYes 실행 후 닫는다
    private void confirm(String message, Runnable onYes) {
        confirmDialog = new ConfirmDialog(message, () -> {
            onYes.run();
            closeConfirm();
        }, this::closeConfirm);
        add(confirmDialog, 0);
        revalidate();
        repaint();
    }

    private void closeConfirm() {
        if (confirmDialog == null) return;
        remove(confirmDialog);
        confirmDialog = null;
        repaint();
    }

    @Override
    public void doLayout() {
        for (Component child : getComponents()) child.setBounds(0, 0, getWidth(), getHeight());
    }

    // ── 그리기 ───────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        int h = getHeight();
        hits.begin(null);
        tetris.ui.theme.UiKit.paintScene(g2, w, h);
        int unit = Math.max(16, (Theme.unit(h) / 8) * 8);
        int pad = 8;
        String title = inControlKeys ? Messages.get("settings.controlKeys") : Messages.get("settings.title");
        int titleSize = Math.max(16, unit * 2);
        int titleBaseline = pad * 6 + titleSize;
        paintHeading(g2, title, titleSize, w / 2, titleBaseline);

        List<String[]> rows = inControlKeys ? keyRows() : settingRows();
        int rowH = Math.max(40, (unit * 2 / 8) * 8);
        int y = ((titleBaseline + pad * 2) / 8) * 8;
        int labelX = pad * 4;
        int rowW = Math.max(pad * 8, w - pad * 8);
        boolean colorBlind = settings.isColorBlindMode();
        for (int i = 0; i < rows.size(); i++) {
            java.awt.Rectangle row = new java.awt.Rectangle(labelX, y, rowW, rowH - pad);
            boolean isSelected = i == selected;
            tetris.ui.theme.UiKit.paintButton(g2, isSelected, row.x, row.y, row.width, row.height,
                    colorBlind, tetris.ui.theme.UiKit.pulse());
            hits.add("row:" + i, row);
            String value = rows.get(i)[1];
            int font = Math.max(14, unit);
            g2.setFont(Theme.font(rows.get(i)[0], font, false));
            g2.setColor(isSelected ? java.awt.Color.WHITE : Theme.TEXT);
            tetris.ui.theme.UiKit.drawInAsset(g2, tetris.ui.theme.UiKit.AssetSlot.BUTTON, row,
                    rows.get(i)[0], tetris.ui.theme.UiKit.Align.LEFT, isSelected);
            g2.setFont(Theme.font(value, font, false));
            tetris.ui.theme.UiKit.drawInAsset(g2, tetris.ui.theme.UiKit.AssetSlot.BUTTON, row,
                    value, tetris.ui.theme.UiKit.Align.RIGHT, isSelected);
            if (value.startsWith("◀") && value.endsWith("▶")) {
                int textWidth = tetris.ui.theme.GameTheme.width(g2, value);
                java.awt.Rectangle hole = tetris.ui.theme.UiKit.contentBox(tetris.ui.theme.UiKit.AssetSlot.BUTTON, row);
                int valueRight = hole.x + hole.width;
                int valueLeft = valueRight - textWidth;
                int arrow = Math.max(24, g2.getFontMetrics().stringWidth("◀") + pad);
                hits.add("dec:" + i, valueLeft - 4, row.y, arrow, row.height);
                hits.add("inc:" + i, valueRight - arrow + 4, row.y, arrow, row.height);
            }
            y += rowH;
        }

        if (!notice.isEmpty()) {
            g2.setFont(Theme.text(Math.max(14, unit * 4 / 5), true));
            g2.setColor(java.awt.Color.WHITE);
            String shown = noticeIsError && !notice.startsWith("⚠") ? "⚠ " + notice : notice;
            tetris.ui.theme.UiKit.drawInBox(g2, shown,
                    new java.awt.Rectangle(pad, y, w - pad * 2, unit * 2), tetris.ui.theme.UiKit.Align.CENTER);
        }

        String guide = inControlKeys ? Messages.get("guide.controlKeys") : Messages.keyGuide(MainFrame.Screen.SETTINGS, null);
        Theme.drawGuide(g2, guide, w, h);
    }

    private static void paintHeading(Graphics2D g, String text, int size, int centerX, int baseline) {
        java.awt.Font font = tetris.ui.theme.GameTheme.display(size);
        g.setFont(font);
        g.setColor(java.awt.Color.WHITE);
        tetris.ui.theme.GameTheme.paintTitle(g, text, font, centerX, baseline, 0);
    }

    /** 테스트가 그린 사각형을 그대로 누른다 */
    public String hitAt(int x, int y) {
        return hits.hit(x, y);
    }

    private void hoverHit(String id) {
        if (capturing != null || confirmDialog != null || id == null) return;
        int index = hitIndex(id);
        if (index >= 0 && index != selected) {
            selected = index;
            notice = "";
            repaint();
        }
    }

    private void clickHit(String id) {
        if (capturing != null || confirmDialog != null || id == null) return;
        int index = hitIndex(id);
        if (index < 0) return;
        selected = index;
        if (id.startsWith("dec:")) changeValue(-1);
        else if (id.startsWith("inc:")) changeValue(1);
        else activateSelected();
    }

    private static int hitIndex(String id) {
        int colon = id.indexOf(':');
        if (colon < 0 || colon == id.length() - 1) return -1;
        try {
            return Integer.parseInt(id.substring(colon + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private List<String[]> settingRows() {
        List<String[]> rows = new ArrayList<>();
        for (Item item : items()) {
            rows.add(switch (item) {
                case SCREEN_SIZE -> new String[] {Messages.get("settings.screenSize"),
                        "◀ " + screenSizeLabel() + " ▶"};
                case DIFFICULTY -> new String[] {Messages.get("settings.difficulty"),
                        "◀ " + Messages.difficultyName(settings.getDifficulty()) + " ▶"};
                case CONTROL_KEYS -> new String[] {Messages.get("settings.controlKeys"), "설정 ▶"};
                case COLOR_BLIND -> new String[] {Messages.get("settings.colorBlind"),
                        settings.isColorBlindMode() ? "켬" : "끔"};
                case CONTROL_STYLE -> new String[] {Messages.get("settings.controlStyle"),
                        "◀ " + Messages.get("style." + settings.getControlStyle().name()) + " ▶"};
                case EFFECTS -> new String[] {Messages.get("settings.effects"),
                        "◀ " + Messages.get(settings.isEffectsHigh() ? "settings.effects.high" : "settings.effects.low") + " ▶"};
                case SFX -> new String[] {Messages.get("settings.sfx"),
                        Messages.get(settings.isSfxEnabled() ? "settings.enabled" : "settings.disabled")};
                case BGM -> new String[] {Messages.get("settings.bgm"),
                        Messages.get(settings.isBgmEnabled() ? "settings.enabled" : "settings.disabled")};
                case DEV_NET_OVERLAY -> new String[] {Messages.get("settings.devNetOverlay"),
                        Messages.get(settings.isDevNetworkOverlay() ? "settings.on" : "settings.off")};
                case RESET_SCOREBOARD -> new String[] {Messages.get("settings.resetScoreboard"), ""};
                case RESET_SETTINGS -> new String[] {Messages.get("settings.resetSettings"), ""};
            });
        }
        return rows;
    }

    private List<String[]> keyRows() {
        List<String[]> rows = new ArrayList<>();
        KeyMap keyMap = settings.getKeyMap();
        for (GameAction action : ACTIONS) {
            String key = action == capturing ? "…" : Messages.keyName(keyMap, action);
            rows.add(new String[] {Messages.actionName(action), key});
        }
        return rows;
    }
}
