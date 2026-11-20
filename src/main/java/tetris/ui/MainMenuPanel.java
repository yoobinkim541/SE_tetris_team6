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
import tetris.ui.fx.StarfieldBackground;

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
        repaint();
    }

    /** Enter: 선택된 항목의 action 실행 */
    public void activateSelected() {
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

        BufferedImage logo = AssetStore.SHARED.fit(AssetStore.LOGO, (int) (w * 0.62), Math.max(unit * 4, h / 5));
        if (logo != null) {
            g2.drawImage(logo, (w - logo.getWidth()) / 2, Math.max(unit, h / 10), null);
        } else {
            g2.setFont(Theme.text(unit * 3, true));
            g2.setColor(Theme.ACCENT);
            Theme.drawCentered(g2, Messages.get("app.title"), w / 2, h / 4);
        }

        List<String> labels = items.stream().map(MenuItem::label).toList();
        int fontSize = unit + unit / 3;
        int menuTop = h * 2 / 5 - fontSize;
        if (title != null) menuTop += fontSize;
        int menuHeight = labels.size() * fontSize * 2 + fontSize;
        int limit = h - unit * 4 - menuTop;
        while (menuHeight > limit && fontSize > 12) {
            fontSize--;
            menuHeight = labels.size() * fontSize * 2 + fontSize;
        }
        g2.setColor(new java.awt.Color(8, 10, 24, 150));
        g2.fillRoundRect(w / 5, menuTop - fontSize, w * 3 / 5, menuHeight + fontSize, 18, 18);
        if (title != null) {
            g2.setFont(Theme.text(fontSize, true));
            g2.setColor(tetris.ui.fx.A11yColors.accent(colorBlind.getAsBoolean()));
            Theme.drawCentered(g2, title, w / 2, menuTop - fontSize / 2);
        }
        Theme.drawMenu(g2, labels, selected, w / 2, h * 2 / 5 + (title == null ? 0 : fontSize), fontSize,
                colorBlind.getAsBoolean());

        g2.setFont(Theme.text(Math.max(10, unit * 3 / 4), false));
        g2.setColor(Theme.WARNING);
        int y = h - unit * 3 - warnings.size() * unit * 3 / 2;
        for (String warning : warnings) {
            Theme.drawCentered(g2, warning, w / 2, y);
            y += unit * 3 / 2;
        }

        Theme.drawGuide(g2, Messages.keyGuide(submenu ? MainFrame.Screen.SOLO_MENU : MainFrame.Screen.MAIN_MENU, null), w, h);
    }
}
