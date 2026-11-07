package tetris.feature.versus.api;

import java.util.function.Consumer;

/**
 * 경기 화면(B)이 쓰는 포트. 로컬 대전·P2P 호스트·P2P 클라이언트가 같은 인터페이스를 구현한다.
 * 콜백은 EDT에서 호출한다. 구독 즉시 현재 스냅샷을 한 번 받는다.
 */
public interface BattleClient extends AutoCloseable {
    void send(PlayerCommand command);

    Subscription subscribe(Consumer<MatchSnapshot> listener);

    @Override
    void close();

    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
