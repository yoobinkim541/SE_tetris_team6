package tetris.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** 로비 코드: 헷갈리는 글자 없는 6글자, 입력 정규화, 3+3 표시 */
class LobbyCodeTest {
    @Test
    void 생성한_코드는_6글자이고_허용_문자만_쓴다() {
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            String code = LobbyCode.generate(random);
            assertEquals(LobbyCode.LENGTH, code.length());
            for (char c : code.toCharArray()) assertTrue(LobbyCode.ALPHABET.indexOf(c) >= 0, code);
        }
    }

    @Test
    void 허용_문자에는_0_O_1_I_L이_없다() {
        for (char c : "0O1IL".toCharArray()) assertEquals(-1, LobbyCode.ALPHABET.indexOf(c));
        assertEquals(31, LobbyCode.ALPHABET.length());
    }

    @Test
    void 입력은_공백_하이픈을_무시하고_대문자로_맞춘다() {
        assertEquals(Optional.of("K7QM2X"), LobbyCode.normalize(" k7q-m2x "));
        assertEquals(Optional.of("K7QM2X"), LobbyCode.normalize("K7Q M2X"));
    }

    @Test
    void 길이나_문자가_틀리면_빈_값이다() {
        assertEquals(Optional.empty(), LobbyCode.normalize(null));
        assertEquals(Optional.empty(), LobbyCode.normalize("K7QM2"));
        assertEquals(Optional.empty(), LobbyCode.normalize("K7QM2XA"));
        assertEquals(Optional.empty(), LobbyCode.normalize("K7QM20"));
        assertEquals(Optional.empty(), LobbyCode.normalize("K7QMIX"));
    }

    @Test
    void 표시는_3글자씩_하이픈으로_나눈다() {
        assertEquals("K7Q-M2X", LobbyCode.display("K7QM2X"));
    }
}
