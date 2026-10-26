package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** NAM-1, NAM-2, NAM-4: 입력 중 문자 제한과 10자 제한 (IME 조합 문자열 포함) */
public class NameFilterTest {
    private AbstractDocument document;

    @BeforeEach
    void setUp() {
        document = new PlainDocument();
        document.setDocumentFilter(NameInputPanel.createNameFilter());
    }

    private String text() throws BadLocationException {
        return document.getText(0, document.getLength());
    }

    private void type(String text) throws BadLocationException {
        document.insertString(document.getLength(), text, null);
    }

    // JTextComponent가 IME 조합 중인 글자를 넣을 때 붙이는 속성
    private static SimpleAttributeSet composed() {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        attrs.addAttribute(StyleConstants.ComposedTextAttribute, new java.text.AttributedString("x"));
        return attrs;
    }

    @Test
    void acceptsKoreanEnglishDigitsAndInnerSpace() throws BadLocationException {
        type("홍길동");
        type(" A1");
        assertEquals("홍길동 A1", text());
    }

    @Test
    void eleventhCharacterIsRejected() throws BadLocationException {
        type("ABCDEFGHIJ");
        type("K");
        assertEquals("ABCDEFGHIJ", text());
    }

    @Test
    void pasteIsTrimmedToRemainingRoom() throws BadLocationException {
        type("ABCDEFGH");
        type("XYZ");
        assertEquals("ABCDEFGHXY", text());
    }

    @Test
    void disallowedCharactersAreDropped() throws BadLocationException {
        type("A!");
        type("😀B");
        assertEquals("AB", text());
    }

    @Test
    void composedTextIsAcceptedWhileThereIsRoom() throws BadLocationException {
        type("ABCDEFGHI");
        document.insertString(9, "가", composed());
        assertEquals("ABCDEFGHI가", text());
    }

    @Test
    void composedTextIsRejectedWhenFullWithoutLosingCommittedText() throws BadLocationException {
        type("ABCDEFGHI가");
        document.insertString(10, "나", composed()); // 11번째 글자 조합 시작 -> 거부
        assertEquals("ABCDEFGHI가", text());

        // JTextComponent는 거부된 조합 위치를 [9, 10)로 잘못 기억하고 다음 IME 이벤트에서 지우려 한다
        document.remove(9, 1);
        assertEquals("ABCDEFGHI가", text());

        // 그 뒤의 일반 삭제(Backspace)는 정상 처리
        document.remove(9, 1);
        assertEquals("ABCDEFGHI", text());
    }

    @Test
    void replacingSelectionCountsFreedRoom() throws BadLocationException {
        type("ABCDEFGHIJ");
        document.replace(0, 2, "XYZ", null);
        assertEquals("XYCDEFGHIJ", text());
    }
}
