package tetris.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.JPanel;

/** Pause 오버레이: Resume / Restart / Quit to Main Menu (PAU-2). GamePanel 위에 겹쳐 그린다. */
public class PausePanel extends JPanel {
    private final List<MenuItem> items;
    private int selected;
    private String keyGuide = "";

    public PausePanel(Runnable onResume, Runnable onRestart, Runnable onQuit) {
        items = List.of(
                new MenuItem(Messages.get("pause.resume"), onResume),
                new MenuItem(Messages.get("pause.restart"), onRestart),
                new MenuItem(Messages.get("pause.quit"), onQuit));
        setOpaque(false);

        KeyBindingManager.bindKey(this, KeyEvent.VK_UP, () -> moveSelection(-1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_DOWN, () -> moveSelection(1));
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, this::activateSelected);
        KeyBindingManager.bindKey(this, KeyEvent.VK_ESCAPE, onResume); // Pause 키는 KeyBindingManager가 처리
    }

    public void moveSelection(int delta) {
        selected = Theme.cycle(selected, delta, items.size());
        repaint();
    }

    /** Enter: 선택 항목 실행 (Restart/Quit은 ConfirmDialog를 거침) */
    public void activateSelected() {
        items.get(selected).action().run();
    }

    /** 새로 Pause할 때마다 첫 항목(Resume)부터 */
    void resetSelection() {
        selected = 0;
    }

    void setKeyGuide(String keyGuide) {
        this.keyGuide = keyGuide;
        repaint();
    }

    int getSelectedIndex() {
        return selected;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        int unit = Theme.unit(getHeight());
        int[] box = Theme.drawDialogBox(g2, w, getHeight(), Math.min(w - unit * 2, unit * 16), unit * 13);

        g2.setFont(Theme.text(unit * 2, true));
        g2.setColor(Theme.ACCENT);
        Theme.drawCentered(g2, Messages.get("pause.title"), w / 2, box[1] + unit * 3);

        List<String> labels = items.stream().map(MenuItem::label).toList();
        Theme.drawMenu(g2, labels, selected, w / 2, box[1] + unit * 4 + unit / 2, unit + unit / 4);

        g2.setFont(Theme.text(Math.max(9, unit * 2 / 3), false));
        g2.setColor(Theme.DIM_TEXT);
        Theme.drawCentered(g2, keyGuide, w / 2, box[1] + box[3] - unit);
    }
}
