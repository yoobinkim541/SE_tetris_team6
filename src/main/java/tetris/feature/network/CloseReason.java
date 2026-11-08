package tetris.feature.network;

/** 연결이 닫힌 이유. {@link PeerListener#onClosed}로 한 번만 전달한다 */
public enum CloseReason {
    /** 이쪽에서 {@link PeerConnection#close()}를 불렀다 */
    LOCAL,
    /** 상대가 연결을 정상적으로 닫았다 (EOF) */
    PEER_CLOSED,
    /** 소켓 읽기·쓰기 실패 (상대 프로세스 강제 종료, 네트워크 단절 등) */
    IO_ERROR,
    /** 상대가 프로토콜을 어겼거나 보낼 메시지가 프레임 규칙을 넘었다 */
    PROTOCOL_ERROR,
    /** 상대가 읽지 않아 송신 대기열이 가득 찼다 */
    SEND_BACKLOG
}
