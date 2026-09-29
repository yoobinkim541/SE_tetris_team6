package tetris.ui;

import javax.swing.JPanel;

/** Yes/No 확인창 (CNF-1). Quit, Restart, Reset Scoreboard, Reset Settings 공용. 기본 선택 No, Esc = No. */
public class ConfirmDialog extends JPanel {
    public ConfirmDialog(String message, Runnable onYes, Runnable onNo) {
        // 메시지·콜백 보관
    }

    public void toggleSelection() {
        // Yes/No 이동
    }

    public void confirm() {
        // Enter: 선택에 따라 onYes / onNo 실행
    }
}
