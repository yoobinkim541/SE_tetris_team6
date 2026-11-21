package tetris.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;

/**
 * Yes/No 확인창 (CNF-1). Quit, Restart, Reset Scoreboard, Reset Settings 공용. 기본 선택 No, Esc = No.
 * 떠 있는 화면 위에 겹쳐 그리는 오버레이다. 띄우고 닫는 일(추가·제거)은 그 화면이 맡는다.
 */
public class ConfirmDialog extends JPanel {
    private static final int YES = 0;
    private static final int NO = 1;

    private final String message;
    private final Runnable onYes;
    private final Runnable onNo;
    private int selected = NO;
    private final tetris.ui.input.Hits hits = new tetris.ui.input.Hits();

    public ConfirmDialog(String message, Runnable onYes, Runnable onNo) {
        if (message == null || onYes == null || onNo == null)
            throw new IllegalArgumentException("message, onYes, onNo cannot be null");
        this.message = message;
        this.onYes = onYes;
        this.onNo = onNo;
        setOpaque(false);
        setFocusable(true);
        tetris.ui.input.MenuMouse.install(this, hits, this::hoverHit, this::clickHit);

        for (int key : new int[] {KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT}) {
            KeyBindingManager.bindKey(this, key, this::toggleSelection);
        }
        KeyBindingManager.bindKey(this, KeyEvent.VK_ENTER, this::confirm);
        KeyBindingManager.bindKey(this, KeyEvent.VK_ESCAPE, onNo);
    }

    public void toggleSelection() {
        selected = selected == YES ? NO : YES;
        repaint();
    }

    /** Enter: 선택에 따라 onYes / onNo 실행 */
    public void confirm() {
        (selected == YES ? onYes : onNo).run();
    }

    boolean isYesSelected() {
        return selected == YES;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth();
        int unit = Theme.unit(getHeight());

        String[] lines = message.split("\n"); // 긴 문구는 줄을 나눠 상자 안에 들어가게 한다
        g2.setFont(Theme.text(unit, false));
        int textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, g2.getFontMetrics().stringWidth(line));
        int lineHeight = unit * 3 / 2;
        int boxWidth = Math.min(w - unit * 2, textWidth + unit * 4);
        int[] box = Theme.drawDialogBox(g2, w, getHeight(), boxWidth, unit * 8 + lineHeight * lines.length);
        int centerX = w / 2;

        g2.setColor(Theme.TEXT);
        int y = box[1] + unit * 2;
        int lineBoxH = Math.max(unit, lineHeight);
        for (String line : lines) {
            tetris.ui.theme.UiKit.drawInBox(g2, line,
                    new java.awt.Rectangle(box[0] + unit, y, box[2] - unit * 2, lineBoxH),
                    tetris.ui.theme.UiKit.Align.CENTER);
            y += lineHeight;
        }

        int optionY = y + unit * 2;
        hits.begin(null);
        addOption("yes", centerX - unit * 3, optionY, unit);
        addOption("no", centerX + unit * 3, optionY, unit);
        drawOption(g2, Messages.get("confirm.yes"), centerX - unit * 3, optionY, selected == YES, unit);
        drawOption(g2, Messages.get("confirm.no"), centerX + unit * 3, optionY, selected == NO, unit);

        g2.setFont(Theme.text(Math.max(12, unit * 2 / 3), false));
        g2.setColor(Theme.DIM_TEXT);
        tetris.ui.theme.UiKit.drawInBox(g2, Messages.get("guide.confirm"),
                new java.awt.Rectangle(box[0] + unit, box[1] + box[3] - unit * 2, box[2] - unit * 2, unit),
                tetris.ui.theme.UiKit.Align.CENTER);
    }

    private static void drawOption(Graphics2D g, String label, int centerX, int baselineY, boolean isSelected, int unit) {
        int width = unit * 5;
        int height = unit + unit / 2;
        int y = baselineY - height + unit / 4;
        float grow = isSelected ? 1.04f + 0.02f * tetris.ui.theme.UiKit.pulse() : 1f;
        int dw = Math.round(width * grow);
        int dh = Math.round(height * grow);
        int dx = centerX - dw / 2;
        int dy = y + (height - dh) / 2;
        tetris.ui.theme.UiKit.paintButton(g, isSelected, dx, dy, dw, dh, false, tetris.ui.theme.UiKit.pulse());
        g.setFont(unit > 14 ? tetris.ui.theme.GameTheme.display(unit) : Theme.text(unit, true));
        tetris.ui.theme.UiKit.drawInAsset(g, tetris.ui.theme.UiKit.AssetSlot.BUTTON,
                new java.awt.Rectangle(dx, dy, dw, dh), label, tetris.ui.theme.UiKit.Align.CENTER, isSelected);
    }

    private void addOption(String id, int centerX, int baselineY, int unit) {
        int width = unit * 5;
        int height = unit + unit / 2;
        hits.add(id, centerX - width / 2, baselineY - height + unit / 4, width, height);
    }

    public String hitAt(int x, int y) {
        return hits.hit(x, y);
    }

    private void hoverHit(String id) {
        if ("yes".equals(id) && selected != YES) {
            selected = YES;
            repaint();
        } else if ("no".equals(id) && selected != NO) {
            selected = NO;
            repaint();
        }
    }

    private void clickHit(String id) {
        if ("yes".equals(id)) selected = YES;
        else if ("no".equals(id)) selected = NO;
        else return;
        confirm();
    }
}
