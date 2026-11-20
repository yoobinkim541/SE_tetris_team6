package tetris.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import tetris.ui.fx.AssetStore;
import tetris.ui.fx.GameSounds;
import tetris.ui.fx.StarfieldBackground;
import tetris.ui.theme.UiKit;

/** 시작 메뉴: SE Tetris 제목 + 메뉴 항목 (게임 시작 / 설정 / 스코어보드 / 게임 종료). 기존 LandingScreen + MenuScreen 통합. */
public class MainMenuPanel extends JPanel {
    private final List<MenuItem> items;
    private final List<String> warnings = new ArrayList<>();
    private final StarfieldBackground stars;
    private final String title;
    private final BooleanSupplier colorBlind;
    private final boolean submenu;
    private int selected;

    public MainMenuPanel(List<MenuItem> items) {
        this(items, null, null, () -> false);
    }

    public MainMenuPanel(List<MenuItem> items, Runnable onBack, String title, BooleanSupplier colorBlind) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("items cannot be empty");
        this.items = List.copyOf(items);
        this.title = title;
        this.colorBlind = colorBlind == null ? () -> false : colorBlind;
        this.submenu = onBack != null;
        setBackground(Theme.BACKGROUND);
        setFocusable(true);
        stars = new StarfieldBackground(this, AssetStore.BG_MENU);

        KeyBindingManager.bindKey(this, KeyEvent.VK_UP, () -> moveSelection(-1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_DOWN, () -> moveSelection(1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, this::activateSelected);
        if (onBack != null) KeyBindingManager.bindKey(this, KeyEvent.VK_ESCAPE, onBack);
    }


    /** delta = -1(위) / +1(아래). 끝에서 순환 */
    public void moveSelection(int delta) {
        selected = Theme.cycle(selected, delta, items.size());
        GameSounds.SHARED.menuMove();
        repaint();
    }

    /** Enter: 선택된 항목의 action 실행 */
    public void activateSelected() {
        GameSounds.SHARED.menuSelect();
        warnings.clear(); // 경고는 메뉴를 처음 떠날 때까지만 보여준다
        repaint();
        items.get(selected).action().run();
    }

    /** 저장 파일 손상 등 시작 시 경고를 1회 표시 */
    public void showWarnings(List<String> warnings) {
        this.warnings.clear();
        this.warnings.addAll(warnings);
        repaint();
    }

    int getSelectedIndex() {
        return selected;
    }

    public void resetSelection() {
        selected = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        int h = getHeight();
        int unit = Theme.unit(h);
        stars.paint(g2, w, h);
        UiKit.paintMoon(g2, w - unit * 5, unit, unit * 4);
        UiKit.paintIsland(g2, 0, unit / 2, h / 3, unit * 4);
        UiKit.paintIsland(g2, 1, w - unit * 5, h * 3 / 5, unit * 4);

        BufferedImage logo = AssetStore.SHARED.fit(AssetStore.LOGO, (int) (w * 0.72), Math.max(unit * 4, h / 5));
        int menuTop;
        if (logo != null) {
            int logoY = Math.max(unit / 2, h / 14);
            g2.drawImage(logo, (w - logo.getWidth()) / 2, logoY, null);
            menuTop = logoY + logo.getHeight() + unit;
        } else {
            g2.setFont(Theme.text(unit * 3, true));
            g2.setColor(UiKit.CYAN);
            Theme.drawCentered(g2, Messages.get("app.title"), w / 2, h / 5);
            menuTop = h / 4;
        }

        List<String> labels = items.stream().map(MenuItem::label).toList();
        int fontSize = unit + unit / 4;
        int buttonWidth = Math.min(w - unit * 4, (int) (w * 0.62));
        int buttonHeight = fontSize + fontSize / 2;
        int gap = Math.max(6, fontSize / 3);
        int menuHeight = labels.size() * (buttonHeight + gap) + (title == null ? 0 : fontSize * 2);
        int limit = h - unit * 4 - menuTop;
        while (menuHeight > limit && fontSize > 11) {
            fontSize--;
            buttonHeight = fontSize + fontSize / 2;
            gap = Math.max(4, fontSize / 3);
            menuHeight = labels.size() * (buttonHeight + gap) + (title == null ? 0 : fontSize * 2);
        }
        if (title != null) {
            g2.setFont(Theme.text(fontSize + 2, true));
            g2.setColor(tetris.ui.fx.A11yColors.accent(colorBlind.getAsBoolean()));
            Theme.drawCentered(g2, title, w / 2, menuTop + fontSize);
            menuTop += fontSize * 2;
        }
        Theme.drawPills(g2, labels, selected, w / 2, menuTop, fontSize, buttonWidth, colorBlind.getAsBoolean());

        g2.setFont(Theme.text(Math.max(10, unit * 3 / 4), false));
        g2.setColor(Theme.WARNING);
        int y = h - unit * 4 - warnings.size() * unit;
        for (String warning : warnings) {
            Theme.drawCentered(g2, warning, w / 2, y);
            y += unit;
        }

        Theme.drawGuide(g2, Messages.keyGuide(submenu ? MainFrame.Screen.SOLO_MENU : MainFrame.Screen.MAIN_MENU, null), w, h);
    }
}
