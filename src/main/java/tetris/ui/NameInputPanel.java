package tetris.ui;

import javax.swing.JPanel;
import javax.swing.text.DocumentFilter;

/** 이름 입력 (Top 10 진입 시에만). 10자 제한은 DocumentFilter로 입력 중에 적용 (NAM-4). Esc 무동작. */
public class NameInputPanel extends JPanel {
    /** 유효한 이름으로 Enter를 누르면 onSubmit 호출. 무효하면 안내만 표시 */
    public NameInputPanel(java.util.function.Consumer<String> onSubmit) {
        // 텍스트 필드 + 필터
    }

    /**
     * 이름 입력용 DocumentFilter (NAM-1, NAM-2, NAM-4).
     * 허용 문자(한글/영문/숫자/문자 사이 공백)만 받고, IME 조합 중에도 10자를 넘지 않게 막는다.
     */
    public static DocumentFilter createNameFilter() {
        throw new UnsupportedOperationException("TODO");
    }
}
