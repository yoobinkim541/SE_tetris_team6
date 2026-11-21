package tetris.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private final JTextField field = new CapsuleField();
    private final JLabel scoreLabel = label("", 28, true, Theme.TEXT);
    private final JLabel hintLabel = label(" ", 14, false, Theme.WARNING);

    /** 유효한 이름으로 Enter를 누르면 onSubmit 호출. 무효하면 안내만 표시 */
    public NameInputPanel(Consumer<String> onSubmit) {
        if (onSubmit == null) throw new IllegalArgumentException("onSubmit cannot be null");
        this.onSubmit = onSubmit;
        setOpaque(false);
        setLayout(new GridBagLayout());

        ((AbstractDocument) field.getDocument()).setDocumentFilter(createNameFilter());
        field.setFont(Theme.font("이름", 22, false));
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.setForeground(Color.WHITE);
        field.setCaretColor(tetris.ui.theme.UiKit.CYAN);
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(10, 28, 10, 28));
        field.setPreferredSize(new java.awt.Dimension(320, 48));
        field.setMinimumSize(new java.awt.Dimension(240, 44));
        field.addActionListener(e -> submit());
        field.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { field.requestFocusInWindow(); }
        });
        JLabel save = label("저장", 18, true, tetris.ui.theme.UiKit.CYAN);
        save.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        save.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { clickSave(); }
        });

        addRow(label(Messages.get("name.title"), 34, true, Theme.ACCENT), 0, 24);
        addRow(scoreLabel, 1, 24);
        addRow(label(Messages.get("name.prompt"), 14, false, Theme.DIM_TEXT), 2, 8);
        addRow(field, 3, 8);
        addRow(hintLabel, 4, 8);
        addRow(save, 5, 8);
        addRow(label(Messages.keyGuide(MainFrame.Screen.NAME_INPUT, null), 13, false, Theme.DIM_TEXT), 6, 0);

    }

    /** 새 기록을 받을 준비: 입력칸을 비우고 점수를 보여준다 */
    public void prepare(long score) {
        field.setText("");
        hintLabel.setText(" ");
        scoreLabel.setText("점수  " + score);
    }

    /** 화면이 보인 뒤 입력칸에 포커스를 준다 */
    public void focusField() {
        field.requestFocusInWindow();
    }

    public javax.swing.JTextField nameField() {
        return field;
    }

    /** 저장을 누르거나 테스트를 이 메서드로 확정한다. 입력칸 포커스는 유지한다 */
    public void clickSave() {
        field.requestFocusInWindow();
        submit();
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
        tetris.ui.theme.GameTheme.applyQuality(g2);
        tetris.ui.theme.UiKit.paintScene(g2, getWidth(), getHeight());
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
        JLabel label = new ThemedLabel(text);
        label.setFont(size > 14 ? tetris.ui.theme.GameTheme.display(size) : tetris.ui.theme.GameTheme.text(size, bold));
        label.setForeground(color);
        return label;
    }

    private static final class ThemedLabel extends JLabel {
        private ThemedLabel(String text) {
            super(text, SwingConstants.CENTER);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
            tetris.ui.theme.GameTheme.applyQuality(g2);
            g2.setFont(getFont());
            g2.setColor(getForeground());
            int baseline = (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
            if (getFont().getSize() > 14) {
                tetris.ui.theme.CrystalText.paint(g2, getText(), getFont(), getWidth() / 2, baseline, false);
            } else {
                tetris.ui.theme.UiKit.drawInBox(g2, getText(), new java.awt.Rectangle(0, 0, getWidth(), getHeight()),
                        tetris.ui.theme.UiKit.Align.CENTER);
            }
        }
    }

    private static final class CapsuleField extends JTextField {
        private CapsuleField() {
            super(MAX_LENGTH + 2);
            setOpaque(false);
            setPreferredSize(new java.awt.Dimension(320, 48));
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            return new java.awt.Dimension(320, 48);
        }

        @Override
        public java.awt.Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = Theme.smooth(g);
            tetris.ui.theme.UiKit.paintCapsule(g2, 0, 0, getWidth(), getHeight());
            g2.setFont(getFont());
            g2.setColor(getForeground());
            String value = getText() == null ? "" : getText();
            java.awt.Rectangle hole = tetris.ui.theme.UiKit.contentBox(tetris.ui.theme.UiKit.AssetSlot.CAPSULE,
                    new java.awt.Rectangle(0, 0, getWidth(), getHeight()));
            tetris.ui.theme.UiKit.drawInBox(g2, value.isEmpty() ? " " : value, hole, tetris.ui.theme.UiKit.Align.CENTER);
            if (!isFocusOwner()) return;
            java.awt.geom.Rectangle2D ink = value.isEmpty()
                    ? tetris.ui.theme.GameTheme.drawnBounds(g2.getFont(), "가", g2.getFontRenderContext())
                    : tetris.ui.theme.GameTheme.drawnBounds(g2.getFont(), value, g2.getFontRenderContext());
            int caretX = value.isEmpty()
                    ? hole.x + hole.width / 2
                    : (int) Math.round(hole.x + (hole.width - ink.getWidth()) / 2.0 + ink.getWidth()) + 2;
            int caretY = (int) Math.round(hole.y + hole.height / 2.0 - ink.getHeight() / 2.0);
            g2.setColor(tetris.ui.theme.UiKit.CYAN);
            g2.fillRect(caretX, caretY, 2, Math.max(8, (int) Math.ceil(ink.getHeight())));
        }
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
