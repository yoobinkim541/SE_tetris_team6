package tetris.feature.versus.api;

import java.util.function.Consumer;

/**
 * P2P 로비 포트 (R3-P2P-LOBBY). 로비 화면(B)이 호출하고 네트워크(C)가 구현한다. 계약 v1 초안.
 * 모든 메서드는 EDT에서 호출해도 막히지 않아야 한다(소켓 작업은 구현이 별도 스레드에서 처리).
 * 콜백은 EDT에서 호출하며, 구독 즉시 현재 스냅샷을 한 번 받는다.
 */
public interface LobbyClient extends AutoCloseable {
    /** 서버로 대기 시작. IDLE·FAILED에서만 유효 */
    void host();

    /** 클라이언트로 접속 시도. IDLE·FAILED에서만 유효 */
    void connect(String address);

    /** HOSTING·CONNECTING을 취소하거나 연결을 끊고 IDLE로 돌아간다 */
    void cancel();

    /** 호스트만 호출. CONNECTED에서 설정을 바꾸면 configRevision이 오르고 양쪽 준비가 풀린다 */
    void configure(MatchConfig config);

    /** 게임 시작 버튼. 현재 configRevision에 대한 준비. 둘 다 준비되면 IN_MATCH */
    void ready();

    /** IN_MATCH일 때 경기 포트. 그 외 상태에서는 IllegalStateException */
    BattleClient battle();

    BattleClient.Subscription subscribe(Consumer<LobbySnapshot> listener);

    @Override
    void close();
}
