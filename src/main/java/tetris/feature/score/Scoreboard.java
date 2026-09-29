package tetris.feature.score;

import java.util.List;

/**
 * Top 10 스코어보드 도메인 (SBD-1~5, NAM-1~3).
 * 기존 ScoreBoardSort / ScoreReset 역할을 흡수한다. 파일 입출력은 save 계층 담당.
 */
public class Scoreboard {
    public static final int MAX_ENTRIES = 10;

    public Scoreboard(List<ScoreEntry> loaded) {
        // 정렬·10개 제한을 적용해 보관
    }

    /** 진입 조건: 기록 수 < 10 이거나 score > 10위 점수 */
    public boolean qualifies(long score) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 점수 >= 새 점수인 마지막 기록 바로 뒤에 삽입 후 10개로 자름. 반환: 순위 index(0-기반), 탈락이면 -1 */
    public int add(ScoreEntry entry) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 읽기 전용 목록 (점수 내림차순, 동점은 먼저 기록된 순) */
    public List<ScoreEntry> entries() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Reset Scoreboard: 전체 삭제 */
    public void clear() {
        // 비우기
    }

    /** trim 후 1~10자, 한글/영문/숫자와 문자 사이 공백만 허용 */
    public static boolean isValidName(String name) {
        throw new UnsupportedOperationException("TODO");
    }
}
