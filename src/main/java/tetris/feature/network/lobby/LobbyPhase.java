package tetris.feature.network.lobby;

/**
 * P2P 로비 단계 (docs/req3/decisions.md "C 소유 결정" 로비 상태).
 * 경기 안의 카운트다운·진행·결과는 {@code MatchSnapshot.phase()}가 나타내므로 여기서는 {@link #IN_MATCH} 하나로 둔다.
 */
public enum LobbyPhase {
    /** P2P 첫 화면. 호스트/접속 선택 전 */
    INITIAL,
    /** 호스트: 내 LAN 주소를 표시하고 접속을 기다림 */
    HOSTING,
    /** 클라이언트: 입력한 주소로 접속 중 */
    CONNECTING,
    /** 연결됨. 호스트가 모드를 고르고 양쪽이 게임 시작을 누르길 기다림. 경기가 끝나면 여기로 돌아온다 (F-07) */
    LOBBY,
    /** 양쪽 준비 후 START. 경기 화면은 {@link LobbyClient#battle()}을 쓴다 */
    IN_MATCH,
    /** 접속 실패·취소 불가 오류·경기 중 단절. message를 보여 준 뒤 {@link LobbyClient#cancel()}로 INITIAL */
    FAILED
}
