package tetris.relay;

import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * 인터넷 대전 로비 코드 (challenge). 헷갈리는 글자(0 O 1 I L)를 뺀 31종 문자로 만든 6글자다.
 * 사람이 불러 주기 쉽도록 표시할 때는 3글자씩 나눈다.
 */
public final class LobbyCode {
    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 6;

    private LobbyCode() {
    }

    public static String generate(Random random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }

    /** 공백·하이픈을 지우고 대문자로 맞춘다. 형식이 틀리면 빈 값 */
    public static Optional<String> normalize(String input) {
        if (input == null) return Optional.empty();
        String code = input.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (code.length() != LENGTH) return Optional.empty();
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) return Optional.empty();
        }
        return Optional.of(code);
    }

    /** {@code K7QM2X} → {@code K7Q-M2X} */
    public static String display(String code) {
        return code.substring(0, 3) + "-" + code.substring(3);
    }
}
