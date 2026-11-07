package tetris.feature.versus.api;

/** 대전 참가자. 로컬 대전은 화면 왼쪽이 ONE, P2P는 호스트가 ONE이다 (R3-MODES) */
public enum PlayerId {
    ONE,
    TWO;

    /** 공격 줄을 받을 상대 (R3-ATTACK) */
    public PlayerId opponent() {
        return this == ONE ? TWO : ONE;
    }
}
