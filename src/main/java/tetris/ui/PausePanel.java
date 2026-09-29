package tetris.ui;

import javax.swing.JPanel;

/** Pause 오버레이: Resume / Restart / Quit to Main Menu (PAU-2). */
public class PausePanel extends JPanel {
    public PausePanel(Runnable onResume, Runnable onRestart, Runnable onQuit) {
        // 항목별 동작 연결
    }

    public void moveSelection(int delta) {
        // 선택 이동
    }

    public void activateSelected() {
        // Enter: 선택 항목 실행 (Restart/Quit은 ConfirmDialog를 거침)
    }
}
