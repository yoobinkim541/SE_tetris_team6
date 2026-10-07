package tetris.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.StyleConstants;
import tetris.feature.score.ScoreEntry;

/** 이름 입력 (Top 10 진입 시에만). 10자 제한은 DocumentFilter로 입력 중에 적용 (NAM-4). Esc 무동작. */
public class NameInputPanel extends JPanel {
    static final int MAX_LENGTH = 10; // NAM-2

    private final Consumer<String> onSubmit;
    private final JTextField field = new JTextField(MAX_LENGTH + 2);
    private final JLabel scoreLabel = label("", 28, true, Theme.TEXT);
    private final JLabel hintLabel = label(" ", 14, false, Theme.WARNING);

    /** 유효한 이름으로 Enter를 누르면 onSubmit 호출. 무효하면 안내만 표시 */
    public NameInputPanel(Consumer<String> onSubmit) {
        if (onSubmit == null) throw new IllegalArgumentException("onSubmit cannot be null");
        this.onSubmit = onSubmit;
        setBackground(Theme.BACKGROUND);
        setLayout(new GridBagLayout());

        ((AbstractDocument) field.getDocument()).setDocumentFilter(createNameFilter());
        field.setFont(Theme.text(22, true));
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.setBackground(Theme.PANEL);
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        field.addActionListener(e -> submit());

        addRow(label(Messages.get("name.title"), 34, true, Theme.ACCENT), 0, 24);
        addRow(scoreLabel, 1, 24);
        addRow(label(Messages.get("name.prompt"), 14, false, Theme.DIM_TEXT), 2, 8);
        addRow(field, 3, 8);
        addRow(hintLabel, 4, 24);
        addRow(label(Messages.keyGuide(MainFrame.Screen.NAME_INPUT, null), 13, false, Theme.DIM_TEXT), 5, 0);
    }

    /** 새 기록을 받을 준비: 입력칸을 비우고 점수를 보여준다 */
    public void prepare(long score) {
        field.setText("");
        hintLabel.setText(" ");
        scoreLabel.setText(Messages.get("game.score") + "  " + score);
    }

    /** 화면이 보인 뒤 입력칸에 포커스를 준다 */
    public void focusField() {
        field.requestFocusInWindow();
    }

    private void submit() {
        String name = field.getText();
        if (!ScoreEntry.isValidName(name)) { // NAM-3: 빈 이름·공백만 있는 이름은 저장하지 않고 안내
            hintLabel.setText(Messages.get("name.invalid"));
            return;
        }
        onSubmit.accept(name.trim());
    }

    private void addRow(Component component, int row, int bottomGap) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new Insets(0, 0, bottomGap, 0);
        add(component, constraints);
    }

    private static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(Theme.text(size, bold));
        label.setForeground(color);
        return label;
    }

    /**
     * 이름 입력용 DocumentFilter (NAM-1, NAM-2, NAM-4).
     * 허용 문자(한글/영문/숫자/문자 사이 공백)만 받고, IME 조합 중에도 10자를 넘지 않게 막는다.
     */
    public static DocumentFilter createNameFilter() {
        return new NameFilter();
    }

    /** NAM-1. ScoreEntry.isValidName의 허용 문자와 같다 */
    static boolean isAllowedChar(char c) {
        return (c >= '가' && c <= '힣') || (c >= 'ㄱ' && c <= 'ㅎ') || (c >= 'ㅏ' && c <= 'ㅣ')
                || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ';
    }

    /**
     * 일반 입력(타자·붙여넣기)은 허용 문자만 남기고 남은 자리만큼 자른다.
     * IME 조합 문자열은 자르지 않고 통째로 받거나 거부한다. 거부하면 JTextComponent가 조합 위치를
     * [삽입 위치 - 길이, 삽입 위치)로 잘못 기억해 다음 IME 이벤트에서 이미 입력된 글자를 지우려 하므로, 그 삭제 한 번을 무시한다.
     */
    private static final class NameFilter extends DocumentFilter {
        private int ignoredRemoveOffset = -1;
        private int ignoredRemoveLength;

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                throws BadLocationException {
            replace(fb, offset, 0, text, attrs);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null || text.isEmpty()) {
                fb.replace(offset, length, text, attrs);
                return;
            }
            int room = MAX_LENGTH - (fb.getDocument().getLength() - length);

            if (isComposedText(attrs)) {
                if (text.length() <= room && text.chars().allMatch(c -> isAllowedChar((char) c))) {
                    fb.replace(offset, length, text, attrs);
                } else {
                    ignoredRemoveOffset = offset - text.length();
                    ignoredRemoveLength = text.length();
                }
                return;
            }

            StringBuilder accepted = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (accepted.length() >= room) break;
                if (isAllowedChar(c)) accepted.append(c);
            }
            if (accepted.isEmpty() && length == 0) return;
            fb.replace(offset, length, accepted.toString(), attrs);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            boolean ignore = offset == ignoredRemoveOffset && length == ignoredRemoveLength;
            ignoredRemoveOffset = -1;
            if (!ignore) fb.remove(offset, length);
        }

        private static boolean isComposedText(AttributeSet attrs) {
            return attrs != null && attrs.isDefined(StyleConstants.ComposedTextAttribute);
        }
    }
}
