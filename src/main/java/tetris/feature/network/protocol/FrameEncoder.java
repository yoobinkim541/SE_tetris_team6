package tetris.feature.network.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;

/** 한 연결의 보내는 방향. 메시지마다 1부터 순번을 붙인다. 한 스레드(연결의 송신 스레드)만 쓴다 */
public final class FrameEncoder {
    private long nextSequence = 1;

    /** 크기 초과면 순번을 소비하지 않고 FRAME_TOO_LARGE를 던진다 */
    public byte[] encode(Message message) throws ProtocolException {
        ObjectNode root = WireFormat.MAPPER.createObjectNode();
        root.put("version", WireFormat.VERSION);
        root.put("sequence", nextSequence);
        root.put("type", message.type().name());
        if (message.matchId() != null) root.put("matchId", message.matchId());
        root.put("configRevision", message.configRevision());
        root.set("payload", message.payload());

        byte[] body;
        try {
            body = WireFormat.MAPPER.writeValueAsBytes(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON tree serialization failed", e);
        }
        if (body.length > WireFormat.MAX_FRAME_BYTES) {
            throw new ProtocolException(ErrorCode.FRAME_TOO_LARGE,
                    message.type() + " body " + body.length + " > " + WireFormat.MAX_FRAME_BYTES);
        }
        nextSequence++;
        return ByteBuffer.allocate(WireFormat.LENGTH_PREFIX_BYTES + body.length)
                .putInt(body.length)
                .put(body)
                .array();
    }
}
