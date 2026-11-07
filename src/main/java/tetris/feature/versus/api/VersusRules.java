package tetris.feature.versus.api;

/** 원문에서 고정된 대전 수치 (R3-ATTACK, R3-CAP). 값을 바꾸려면 docs/req3/decisions.md를 먼저 고친다 */
public final class VersusRules {
    /** 이 줄 수 이상을 한 번에 지워야 공격이 나간다 */
    public static final int MIN_ATTACK_LINES = 2;
    /** 상대 대기열에 쌓일 수 있는 최대 줄 수 */
    public static final int MAX_PENDING_ROWS = 10;
    /** 공격으로 들어온 칸의 보드 값 (회색). 블록 색 1~8과 겹치지 않는다 */
    public static final int ATTACK_CELL = 9;
    public static final int BOARD_ROWS = 20;
    public static final int BOARD_COLS = 10;
    /** TIME_LIMIT 기본 제한 시간 (팀 결정 D-05) */
    public static final long DEFAULT_TIME_LIMIT_MILLIS = 120_000L;

    private VersusRules() {
    }
}
