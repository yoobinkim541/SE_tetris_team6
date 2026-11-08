package tetris.feature.network;

/** 연결 수립(호스트 대기·클라이언트 접속·HELLO 교환)이 실패한 이유 */
public enum EstablishFailure {
    /** IPv4 점 4개 형식이 아니거나 포트 범위를 벗어났다 */
    INVALID_ADDRESS,
    /** 호스트 포트를 다른 프로세스나 소켓이 이미 쓰고 있다 */
    PORT_IN_USE,
    /** 상대 포트가 닫혀 있어 접속이 거부되었다 */
    REFUSED,
    /** 제한 시간 안에 소켓 접속이나 HELLO 응답이 끝나지 않았다 */
    TIMEOUT,
    /** 핸드셰이크 규칙 위반 (같은 역할, HELLO 전 다른 메시지, 잘못된 프레임 등) */
    PROTOCOL_ERROR,
    /** 그 외 소켓 입출력 실패 또는 핸드셰이크 중 연결 종료 */
    IO_ERROR
}
