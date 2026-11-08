package tetris.feature.network.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** 로비 준비(READY)·시작(START) 메시지 payload 변환 (S8-C2) */
public final class LobbyPayloads {
    private LobbyPayloads() {
    }

    /** 현재 설정 revision에 대한 준비 여부를 담는 READY 메시지를 만든다 */
    public static Message readyMessage(int configRevision, boolean ready) {
        ObjectNode payload = WireFormat.MAPPER.createObjectNode().put("ready", ready);
        return new Message(MessageType.READY, null, configRevision, payload);
    }

    /** READY 메시지에서 준비 불리언 값을 꺼낸다 */
    public static boolean readReady(Message m) throws ProtocolException {
        Objects.requireNonNull(m, "message");
        if (m.type() != MessageType.READY) {
            throw new IllegalArgumentException("expected READY but was " + m.type());
        }
        JsonNode readyNode = m.payload().get("ready");
        if (readyNode == null || !readyNode.isBoolean()) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, "ready");
        }
        return readyNode.booleanValue();
    }

    /** 호스트가 양쪽 준비를 확인하고 한 번 보내는 START 메시지를 만든다 */
    public static Message startMessage(String matchId, int configRevision) {
        return new Message(MessageType.START, matchId, configRevision, WireFormat.MAPPER.createObjectNode());
    }
}
