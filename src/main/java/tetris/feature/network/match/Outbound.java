package tetris.feature.network.match;

import tetris.feature.network.protocol.Message;

/** 경기 메시지를 상대에게 보내는 통로. 앱에서는 PeerLink::send. 대기열에 넣기만 하고 막지 않는다 */
@FunctionalInterface
public interface Outbound {
    boolean send(Message message);
}
