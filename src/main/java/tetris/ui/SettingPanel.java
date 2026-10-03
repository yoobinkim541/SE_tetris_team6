package tetris.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.JPanel;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;

/**
 * 설정 화면 (SET-2): Screen Size / Control Keys / Color Blind / Reset Scoreboard / Reset Settings. 기존 SettingScreen 대체.
 * 유효한 변경은 즉시 적용·저장한다 (SET-3). 저장 실패 시 이번 실행에는 적용하고 안내만 한다 (SET-4).
 */
public class SettingPanel extends JPanel {
    private enum Item { SCREEN_SIZE, CONTROL_KEYS, COLOR_BLIND, RESET_SCOREBOARD, RESET_SETTINGS }

    private static final Item[] ITEMS = Item.values();
    private static final GameAction[] ACTIONS = GameAction.values();

    private final Settings settings;
    private final BooleanSupplier onChanged;
    private final BooleanSupplier onResetScoreboard;
    private final Runnable onBack;

    private boolean inControlKeys;   // Control Keys 하위 화면을 보고 있는지
    private int selected;            // 지금 보이는 목록에서의 선택
    private int listSelection;       // 하위 화면에 들어가기 전 설정 목록의 선택 (돌아올 때 복원)
    private GameAction capturing;    // 새 키 입력 대기 중인 기능, 없으면 null
    private KeyEventDispatcher keyCapture;
    private ConfirmDialog confirmDialog;
    private String notice = "";
    private boolean noticeIsError;   // 경고색으로 그릴지 (거부·저장 실패)

    /**
     * onChanged: 설정 값이 바뀐 뒤 호출. 화면 반영과 저장을 하고 저장 성공 여부를 돌려준다.
     * onResetScoreboard: 스코어보드를 비우고 저장 성공 여부를 돌려준다. onBack: Main Menu로 (Esc)
     */
    public SettingPanel(Settings settings, BooleanSupplier onChanged, BooleanSupplier onResetScoreboard, Runnable onBack) {
        if (settings == null || onChanged == null || onResetScoreboard == null || onBack == null)
            throw new IllegalArgumentException("arguments cannot be null");
        this.settings = settings;
        this.onChanged = onChanged;
        this.onResetScoreboard = onResetScoreboard;
        this.onBack = onBack;

        setLayout(null);
        setBackground(Theme.BACKGROUND);
        setFocusable(true);

        BooleanSupplier listActive = () -> confirmDialog == null && capturing == null;
        KeyBindingManager.bindKey(this, KeyEvent.VK_UP, listActive, () -> moveSelection(-1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_DOWN, listActive, () -> moveSelection(1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_LEFT, listActive, () -> changeScreenSize(-1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_RIGHT, listActive, () -> changeScreenSize(1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, listActive, this::activateSelected);
        KeyBindingManager.bindKey(this, KeyEvent.VK_ESCAPE, listActive, this::back);
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
        selected = Theme.cycle(selected, delta, inControlKeys ? ACTIONS.length : ITEMS.length);
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
        switch (ITEMS[selected]) {
            case SCREEN_SIZE -> changeScreenSize(1);
            case CONTROL_KEYS -> openControlKeys();
            case COLOR_BLIND -> {
                settings.setColorBlindMode(!settings.isColorBlindMode());
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

    // ←/→ 또는 Enter: Small -> Medium -> Large 순환. 설정 목록의 Screen Size에서만
    private void changeScreenSize(int delta) {
        if (inControlKeys || ITEMS[selected] != Item.SCREEN_SIZE) return;
        ScreenSize[] sizes = ScreenSize.values();
        settings.setScreenSize(sizes[Theme.cycle(settings.getScreenSize().ordinal(), delta, sizes.length)]);
        applyChange("");
    }

    private void applyChange(String successNotice) {
        boolean saved = onChanged.getAsBoolean();
        setNotice(saved ? successNotice : Messages.get("settings.saveFailed"), !saved);
        repaint();
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
        super.paintComponent(g);
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        int h = getHeight();
        int unit = Theme.unit(h);

        String title = inControlKeys ? Messages.get("settings.controlKeys") : Messages.get("settings.title");
        g2.setFont(Theme.text(unit * 2, true));
        g2.setColor(Theme.ACCENT);
        Theme.drawCentered(g2, title, w / 2, unit * 4);

        List<String[]> rows = inControlKeys ? keyRows() : settingRows();
        int lineHeight = unit * 2;
        int y = unit * 7;
        int labelX = w / 8;
        int valueRight = w * 7 / 8;
        for (int i = 0; i < rows.size(); i++) {
            boolean isSelected = i == selected;
            g2.setFont(Theme.text(unit, isSelected));
            g2.setColor(isSelected ? Theme.ACCENT : Theme.TEXT);
            g2.drawString((isSelected ? "▶ " : "   ") + rows.get(i)[0], labelX, y);
            Theme.drawRight(g2, rows.get(i)[1], valueRight, y);
            y += lineHeight;
        }

        if (!notice.isEmpty()) {
            g2.setFont(Theme.text(Math.max(10, unit * 4 / 5), true));
            g2.setColor(noticeIsError ? Theme.WARNING : Theme.ACCENT);
            Theme.drawCentered(g2, notice, w / 2, y + unit);
        }

        String guide = inControlKeys ? Messages.get("guide.controlKeys") : Messages.keyGuide(MainFrame.Screen.SETTINGS, null);
        Theme.drawGuide(g2, guide, w, h);
    }

    private List<String[]> settingRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {Messages.get("settings.screenSize"), "◀ " + Messages.get("size." + settings.getScreenSize().name()) + " ▶"});
        rows.add(new String[] {Messages.get("settings.controlKeys"), "›"});
        rows.add(new String[] {Messages.get("settings.colorBlind"),
                Messages.get(settings.isColorBlindMode() ? "settings.on" : "settings.off")});
        rows.add(new String[] {Messages.get("settings.resetScoreboard"), ""});
        rows.add(new String[] {Messages.get("settings.resetSettings"), ""});
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
