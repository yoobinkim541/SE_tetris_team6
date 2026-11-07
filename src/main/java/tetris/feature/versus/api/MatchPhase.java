package tetris.feature.versus.api;

/** 경기 진행 단계. P2P는 FINISHED 뒤 로비 대기 상태로 돌아간다 (R3-P2P-END) */
public enum MatchPhase {
    COUNTDOWN,
    PLAYING,
    /** 로컬 대전 일시정지. 두 보드가 함께 멈춘다 (D-03) */
    PAUSED,
    FINISHED
}
