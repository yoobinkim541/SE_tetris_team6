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
    private final tetris.ui.input.Hits hits = new tetris.ui.input.Hits();

    public PausePanel(Runnable onResume, Runnable onRestart, Runnable onQuit) {
        items = List.of(
                new MenuItem(Messages.get("pause.resume"), onResume),
                new MenuItem(Messages.get("pause.restart"), onRestart),
                new MenuItem(Messages.get("pause.quit"), onQuit));
        setOpaque(false);
        setFocusable(true);
        tetris.ui.input.MenuMouse.install(this, hits, this::hoverHit, this::clickHit);

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
        int h = getHeight();
        int unit = Theme.unit(h);
        g2.setColor(new java.awt.Color(0, 0, 0, 230));
        g2.fillRect(0, 0, w, h);
        int inset = tetris.ui.theme.GameTheme.snap8(Math.max(16, unit * 2));
        int boxW = Math.min(w - inset * 2, tetris.ui.theme.GameTheme.snap8(unit * 18));
        int boxH = Math.min(h - inset * 2, tetris.ui.theme.GameTheme.snap8(unit * 16));
        int[] box = Theme.drawDialogBox(g2, w, h, boxW, boxH);
        g2.setFont(tetris.ui.theme.GameTheme.display(Math.max(18, unit * 2)));
        tetris.ui.theme.UiKit.drawInBox(g2, Messages.get("pause.title"),
                new java.awt.Rectangle(box[0] + 8, box[1] + 8, box[2] - 16, Math.max(24, unit * 3)),
                tetris.ui.theme.UiKit.Align.CENTER);

        List<String> labels = items.stream().map(MenuItem::label).toList();
        int fontSize = Math.max(12, unit);
        int buttonWidth = boxW - unit * 4;
        int top = box[1] + unit * 4;
        hits.begin(null);
        int pillH = fontSize + fontSize / 2;
        int pillGap = Math.max(8, (fontSize / 3 + 4) / 8 * 8);
        int pillX = w / 2 - buttonWidth / 2;
        for (int i = 0; i < labels.size(); i++) {
            hits.add(Integer.toString(i), pillX, top + i * (pillH + pillGap), buttonWidth, pillH);
        }
        Theme.drawPills(g2, labels, selected, w / 2, top, fontSize, buttonWidth, false);

        int guideW = Math.min(boxW - unit * 2, w - unit * 2);
        int guideH = Math.max(28, unit * 2);
        int guideX = (w - guideW) / 2;
        int guideY = box[1] + box[3] - guideH - unit;
        tetris.ui.theme.UiKit.paintCapsule(g2, guideX, guideY, guideW, guideH);
        g2.setFont(Theme.text(Math.max(12, unit * 2 / 3), false));
        g2.setColor(Theme.TEXT);
        tetris.ui.theme.UiKit.drawInAsset(g2, tetris.ui.theme.UiKit.AssetSlot.CAPSULE,
                new java.awt.Rectangle(guideX, guideY, guideW, guideH), keyGuide, tetris.ui.theme.UiKit.Align.CENTER);
    }

    public String hitAt(int x, int y) {
        return hits.hit(x, y);
    }

    private void hoverHit(String id) {
        int index = hitIndex(id);
        if (index >= 0 && index != selected) {
            selected = index;
            repaint();
        }
    }

    private void clickHit(String id) {
        int index = hitIndex(id);
        if (index < 0) return;
        selected = index;
        activateSelected();
    }

    private static int hitIndex(String id) {
        if (id == null) return -1;
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
