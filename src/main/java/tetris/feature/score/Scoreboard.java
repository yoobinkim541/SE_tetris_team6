package tetris.feature.score;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Top 10 스코어보드 도메인 (SBD-1~5, NAM-1~3).
 * 기존 ScoreBoardSort / ScoreReset 역할을 흡수한다. 파일 입출력은 save 계층 담당.
 */
public class Scoreboard {
    public static final int MAX_ENTRIES = 10;
    private static final int MAX_NAME_LENGTH = 10;

    private final List<ScoreEntry> entries = new ArrayList<>();

    public Scoreboard(List<ScoreEntry> loaded) {
        if (loaded == null) throw new IllegalArgumentException("loaded cannot be null");

        entries.addAll(loaded);
        entries.sort(Comparator.comparingLong(ScoreEntry::score).reversed()); // 안정 정렬: 동점은 읽은 순서 유지
        trimToMax();
    }

    /** 진입 조건: 기록 수 < 10 이거나 score > 10위 점수 */
    public boolean qualifies(long score) {
        return entries.size() < MAX_ENTRIES || score > entries.get(MAX_ENTRIES - 1).score();
    }

    /** 점수 >= 새 점수인 마지막 기록 바로 뒤에 삽입 후 10개로 자름. 반환: 순위 index(0-기반), 탈락이면 -1 */
    public int add(ScoreEntry entry) {
        if (entry == null) throw new IllegalArgumentException("entry cannot be null");

        int index = entries.size();
        while (index > 0 && entries.get(index - 1).score() < entry.score()) index--;
        if (index >= MAX_ENTRIES) return -1;

        entries.add(index, entry);
        trimToMax();
        return index;
    }

    /** 읽기 전용 목록 (점수 내림차순, 동점은 먼저 기록된 순) */
    public List<ScoreEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Reset Scoreboard: 전체 삭제 */
    public void clear() {
        entries.clear();
    }

    /** trim 후 1~10자, 한글/영문/숫자와 문자 사이 공백만 허용 */
    public static boolean isValidName(String name) {
        if (name == null) return false;

        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) return false;
        return trimmed.matches("[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9 ]+");
    }

    private void trimToMax() {
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
    }
}
