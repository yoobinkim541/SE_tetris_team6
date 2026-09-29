package tetris.feature.setting;

/** 조작키를 지정할 수 있는 게임 기능 7개 (KEY-3) */
public enum GameAction {
    MOVE_LEFT, MOVE_RIGHT, SOFT_DROP, ROTATE, HARD_DROP, PAUSE, QUIT;

    /** 키를 누르고 있을 때 반복 처리되는지 (좌/우/Soft Drop만 true. 명세 7.3) */
    public boolean isRepeatable() {
        throw new UnsupportedOperationException("TODO");
    }
}
