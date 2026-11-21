package tetris.feature.raid.api;

/** 레이드 진행 단계 */
public enum RaidPhase {
    /** 시작 전 3초 */
    COUNTDOWN,
    PLAYING,
    /** 보스 처치 연출 뒤 보상 카드 선택 (보드 정지) */
    CARD_PICK,
    PAUSED,
    FINISHED
}
