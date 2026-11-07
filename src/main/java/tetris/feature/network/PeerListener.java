package tetris.feature.network;

import tetris.feature.network.protocol.Message;

/**
 * 연결 이벤트를 받는 쪽. 두 메서드 모두 {@link PeerConnection#start}에 넘긴 dispatcher에서 실행된다.
 * 실제 앱은 dispatcher로 EDT를 넘기므로 여기서 엔진·화면 상태를 바꿔도 된다.
 */
public interface PeerListener {
    /** 검증을 통과한 메시지를 받은 순서대로 전달한다 */
    void onMessage(Message message);

    /** 연결이 닫혔다. 연결마다 한 번만 호출되며 그 뒤 onMessage는 오지 않는다 */
    void onClosed(CloseReason reason, String detail);
}
