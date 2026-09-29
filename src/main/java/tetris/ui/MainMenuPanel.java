package tetris.ui;

import java.util.List;
import javax.swing.JPanel;

/** 시작 메뉴: SE Tetris 제목 + 메뉴 항목 (게임 시작 / 설정 / 스코어보드 / 게임 종료). 기존 LandingScreen + MenuScreen 통합. */
public class MainMenuPanel extends JPanel {
    public MainMenuPanel(List<MenuItem> items) {
        // 항목 목록과 그리기를 분리
    }

    /** delta = -1(위) / +1(아래). 끝에서 순환 */
    public void moveSelection(int delta) {
        // 선택 이동
    }

    /** Enter: 선택된 항목의 action 실행 */
    public void activateSelected() {
        // 실행
    }

    /** 저장 파일 손상 등 시작 시 경고를 1회 표시 */
    public void showWarnings(List<String> warnings) {
        // 경고 표시
    }
}
