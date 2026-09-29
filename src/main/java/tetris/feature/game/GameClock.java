package tetris.feature.game;

/** 낙하 타이머 추상화 (TMR-1~3). 운영: SwingGameClock, 테스트: 가짜 구현. */
public interface GameClock {
    /** 전체 간격으로 (재)시작. 이미 돌고 있으면 처음부터 다시 센다 */
    void start(int intervalMillis);

    void stop();
}
