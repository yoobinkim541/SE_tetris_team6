package tetris.feature.versus.api;

/**
 * 두 GameSession·시계·공격·승패의 단일 소유자 (호스트 권위). 로컬은 이 PC가, P2P는 호스트가 소유한다.
 * 모든 메서드는 EDT에서 호출한다. C는 tick을 전송하지 않고 명령과 스냅샷만 옮긴다.
 *
 * <p>{@link #advance(long)} 한 번(=pulse)은 다음 순서를 지킨다 (D-07):
 * due인 낙하·삭제와 입력 처리 → 삭제 확정·공격 수집 → 대기열 반영 → Spawn 대기 세션에 공격 삽입
 * → 양측 Spawn → 승패 확정. 같은 pulse의 양측 패배는 무승부다 (D-02).
 */
public interface MatchEngine extends AutoCloseable {
    /** 새 matchId로 경기를 시작한다. 이전 경기의 held 키는 비운다 */
    void start(MatchConfig config);

    /** 입력을 받은 순서대로 큐에 넣는다. 다음 advance에서 처리 */
    void submit(PlayerCommand command);

    /** 단조 시각 nowMillis까지 due인 작업을 실행한다. 마감과 같거나 늦은 입력은 무시한다 */
    void advance(long nowMillis);

    MatchSnapshot snapshot();

    @Override
    void close();
}
