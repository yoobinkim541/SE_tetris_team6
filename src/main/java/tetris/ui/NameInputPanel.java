package tetris.ui;

import javax.swing.JPanel;

/** 이름 입력 (Top 10 진입 시에만). 10자 제한은 DocumentFilter로 입력 중에 적용 (NAM-4). Esc 무동작. */
public class NameInputPanel extends JPanel {
    /** 유효한 이름으로 Enter를 누르면 onSubmit 호출. 무효하면 안내만 표시 */
    public NameInputPanel(java.util.function.Consumer<String> onSubmit) {
        // 텍스트 필드 + 필터
    }
}
