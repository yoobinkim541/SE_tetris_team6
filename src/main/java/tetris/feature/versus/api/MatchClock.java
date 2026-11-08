package tetris.feature.versus.api;

/**
 * 제한 시간 판정용 단조 시계 (R3-TIME). 낙하 타이머({@code GameClock})와 분리한다:
 * GameClock은 "다음 tick까지의 간격"이고, MatchClock은 "경기 마감 시각(due time)"을 판정한다.
 * 운영은 {@link #system()}, 테스트는 값을 직접 올리는 가짜를 쓴다.
 */
@FunctionalInterface
public interface MatchClock {
    long nowMillis();

    static MatchClock system() {
        return () -> System.nanoTime() / 1_000_000L;
    }
}
