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

    public ConfirmDialog(String message, Runnable onYes, Runnable onNo) {
        if (message == null || onYes == null || onNo == null)
            throw new IllegalArgumentException("message, onYes, onNo cannot be null");
        this.message = message;
        this.onYes = onYes;
        this.onNo = onNo;
        setOpaque(false);

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
        int y = box[1] + unit * 3;
        for (String line : lines) {
            Theme.drawCentered(g2, line, centerX, y);
            y += lineHeight;
        }

        int optionY = y + unit * 2;
        drawOption(g2, Messages.get("confirm.yes"), centerX - unit * 3, optionY, selected == YES, unit);
        drawOption(g2, Messages.get("confirm.no"), centerX + unit * 3, optionY, selected == NO, unit);

        g2.setFont(Theme.text(Math.max(9, unit * 2 / 3), false));
        g2.setColor(Theme.DIM_TEXT);
        Theme.drawCentered(g2, Messages.get("guide.confirm"), centerX, box[1] + box[3] - unit);
    }

    private static void drawOption(Graphics2D g, String label, int centerX, int baselineY, boolean isSelected, int unit) {
        g.setFont(Theme.text(unit + unit / 4, isSelected));
        g.setColor(isSelected ? Theme.ACCENT : Theme.TEXT);
        Theme.drawCentered(g, isSelected ? "[ " + label + " ]" : label, centerX, baselineY);
    }
}
