package tetris.feature.network.lobby;

import java.util.function.Consumer;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.MatchConfig;

/**
 * P2P 로비 포트. 로비 화면(B)이 호출하고 네트워크(C)가 구현한다.
 *
 * <p>모든 메서드는 EDT에서 불러도 막히지 않는다(소켓 작업은 구현이 별도 스레드에서 한다). 결과는 즉시 반환값이 아니라
 * 구독한 {@link LobbySnapshot}으로 알린다. 콜백은 EDT에서 실행하며, 구독하면 현재 스냅샷을 한 번 바로 받는다.
 * 현재 단계에서 허용되지 않는 호출은 {@link IllegalStateException}이다.
 */
public interface LobbyClient extends AutoCloseable {
    /** 호스트로 대기 시작. INITIAL·FAILED에서만 */
    void host();

    /** 클라이언트로 접속 시도. INITIAL·FAILED에서만. 주소 형식 오류는 FAILED와 안내 문구로 알린다 (S8-C1) */
    void connect(String address);

    /** 대기·접속을 취소하거나, 로비·경기 중이면 연결을 끊고 INITIAL로 돌아간다(로비 떠나기, D-03). FAILED 확인도 이것으로 한다 */
    void cancel();

    /** 호스트만, LOBBY에서. configRevision을 올리고 양쪽 준비를 푼다 (R3-MODES, R3-READY) */
    void configure(MatchConfig config);

    /** 게임 시작 버튼. 현재 configRevision에 대한 준비. 양쪽 모두 준비되면 호스트가 START를 한 번 보내고 IN_MATCH */
    void ready();

    /** IN_MATCH일 때 경기 포트. 그 외 단계에서는 {@link IllegalStateException} */
    BattleClient battle();

    BattleClient.Subscription subscribe(Consumer<LobbySnapshot> listener);

    /** 연결과 스레드를 정리한다. 여러 번 불러도 된다 */
    @Override
    void close();
}
