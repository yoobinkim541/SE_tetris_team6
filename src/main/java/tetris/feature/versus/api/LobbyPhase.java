package tetris.feature.versus.api;

/** P2P 로비 단계 (R3-P2P-LOBBY). 경기가 끝나면 CONNECTED로 돌아간다 */
public enum LobbyPhase {
    /** P2P 메뉴 첫 화면. 서버/클라이언트 선택 전 */
    IDLE,
    /** 서버: IP를 표시하고 접속을 기다림 */
    HOSTING,
    /** 클라이언트: 입력한 IP로 접속 중 */
    CONNECTING,
    /** 연결 완료. 호스트가 모드를 고르고 둘 다 게임 시작을 누르길 기다림 */
    CONNECTED,
    /** 둘 다 준비됨. 경기 화면으로 넘어감 */
    IN_MATCH,
    /** 연결 실패·끊김. message를 보여 준 뒤 IDLE로 돌아간다 */
    FAILED
}
