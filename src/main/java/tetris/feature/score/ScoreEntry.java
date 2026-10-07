package tetris.feature.score;

import java.time.Instant;
import java.util.regex.Pattern;

/** 스코어보드 기록 1건 (SBD-4). 규칙을 어기는 기록은 만들어지지 않는다 */
public record ScoreEntry(String name, long score, int level, int lines, Instant recordedAt) {
    private static final int MAX_NAME_LENGTH = 10;
    private static final Pattern NAME_CHARACTERS = Pattern.compile("[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9 ]+");

    public ScoreEntry {
        if (!isValidName(name)) throw new IllegalArgumentException("invalid name : " + name);
        if (score < 0) throw new IllegalArgumentException("score should be 0 or more. Value : " + score);
        if (level < 1) throw new IllegalArgumentException("level should be 1 or more. Value : " + level);
        if (lines < 0) throw new IllegalArgumentException("lines should be 0 or more. Value : " + lines);
        if (recordedAt == null) throw new IllegalArgumentException("recordedAt cannot be null");

        name = name.trim();
    }

    /** NAM-1~3: 앞뒤 공백을 뺀 1~10자, 한글·영문·숫자와 문자 사이의 공백만 허용 */
    public static boolean isValidName(String name) {
        if (name == null) return false;

        String trimmed = name.trim();
        return !trimmed.isEmpty()
                && trimmed.length() <= MAX_NAME_LENGTH
                && NAME_CHARACTERS.matcher(trimmed).matches();
    }
}
