package tetris.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/** 시작 메뉴: SE Tetris 제목 + 메뉴 항목 (게임 시작 / 설정 / 스코어보드 / 게임 종료). 기존 LandingScreen + MenuScreen 통합. */
public class MainMenuPanel extends JPanel {
    private final List<MenuItem> items;
    private final List<String> warnings = new ArrayList<>();
    private int selected;

    public MainMenuPanel(List<MenuItem> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("items cannot be empty");
        this.items = List.copyOf(items);
        setBackground(Theme.BACKGROUND);
        setFocusable(true);

        KeyBindingManager.bindKey(this, KeyEvent.VK_UP, () -> moveSelection(-1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_DOWN, () -> moveSelection(1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, this::activateSelected);
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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        int h = getHeight();
        int unit = Theme.unit(h);

        g2.setFont(Theme.text(unit * 3, true));
        g2.setColor(Theme.ACCENT);
        Theme.drawCentered(g2, Messages.get("app.title"), w / 2, h / 4);

        List<String> labels = items.stream().map(MenuItem::label).toList();
        Theme.drawMenu(g2, labels, selected, w / 2, h * 2 / 5, unit + unit / 3);

        g2.setFont(Theme.text(Math.max(10, unit * 3 / 4), false));
        g2.setColor(Theme.WARNING);
        int y = h - unit * 3 - warnings.size() * unit * 3 / 2;
        for (String warning : warnings) {
            Theme.drawCentered(g2, warning, w / 2, y);
            y += unit * 3 / 2;
        }

        Theme.drawGuide(g2, Messages.keyGuide(MainFrame.Screen.MAIN_MENU, null), w, h);
    }
}
