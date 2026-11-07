package tetris.feature.versus.api;

/** 대전 수치 (R3-ATTACK, R3-CAP, R3-TIME). 값을 바꾸려면 docs/req3/decisions.md를 먼저 고친다 */
public final class VersusRules {
    /** 이 줄 수 이상을 한 번에 지워야 공격이 나간다 (R3-ATTACK, 원문) */
    public static final int MIN_ATTACK_LINES = 2;
    /** 상대 대기열에 쌓일 수 있는 최대 줄 수 (R3-CAP, 원문) */
    public static final int MAX_PENDING_ROWS = 10;
    /** 공격으로 들어온 칸의 보드 값. 블록 색 1~8과 겹치지 않고 아이템이 없다 (R3-GARBAGE, D-04) */
    public static final int ATTACK_CELL = 9;
    public static final int BOARD_ROWS = 20;
    public static final int BOARD_COLS = 10;
    /** TIMED 기본 제한 시간 (D-05) */
    public static final long DEFAULT_TIME_LIMIT_MILLIS = 180_000L;
    /** 호스트 EDT 매치 pulse 간격 제안값. due인 낙하·삭제만 실행한다 (D-07) */
    public static final int MATCH_PULSE_MILLIS = 16;

    private VersusRules() {
    }
}
