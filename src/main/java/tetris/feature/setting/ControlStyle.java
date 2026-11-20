package tetris.feature.setting;

/**
 * 조작 방식 (CTL-1). 한 판이 시작할 때 정해지고 판 도중에는 바뀌지 않는다.
 * CLASSIC은 1차 명세(즉시 Lock, Wall Kick 없음, Next 1, OS 키 반복)를 그대로 재현한다.
 * MODERN은 Lock Delay·SRS와 고스트·홀드·반시계·DAS·Next 3·입력 버퍼를 쓴다.
 */
public enum ControlStyle {
    CLASSIC, MODERN;

    public boolean modern() {
        return this == MODERN;
    }
}
