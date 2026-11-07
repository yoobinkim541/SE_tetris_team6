package tetris.feature.network.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 한 연결의 받는 방향. TCP read 경계는 프레임 경계와 무관하므로 받은 바이트를 모아 완성된 프레임만 꺼낸다.
 * 위반을 한 번 만나면 그 뒤 입력은 받지 않는다. 한 스레드(연결의 수신 스레드)만 쓴다.
 */
public final class FrameDecoder {
    private byte[] buffer = new byte[1024];
    private int size;
    private long lastSequence;
    private boolean failed;

    /** 완성된 메시지를 받은 순서대로 돌려준다. 아직 완성된 프레임이 없으면 빈 리스트 */
    public List<Message> feed(byte[] source, int offset, int length) throws ProtocolException {
        if (failed) throw new IllegalStateException("decoder already rejected this connection");
        Objects.checkFromIndexSize(offset, length, source.length);
        append(source, offset, length);

        List<Message> messages = new ArrayList<>();
        int position = 0;
        try {
            while (size - position >= WireFormat.LENGTH_PREFIX_BYTES) {
                int bodyLength = ByteBuffer.wrap(buffer, position, WireFormat.LENGTH_PREFIX_BYTES).getInt();
                if (bodyLength <= 0) {
                    throw new ProtocolException(ErrorCode.MALFORMED_FRAME, "length prefix " + bodyLength);
                }
                if (bodyLength > WireFormat.MAX_FRAME_BYTES) {
                    throw new ProtocolException(ErrorCode.FRAME_TOO_LARGE,
                            "length prefix " + bodyLength + " > " + WireFormat.MAX_FRAME_BYTES);
                }
                int frameEnd = position + WireFormat.LENGTH_PREFIX_BYTES + bodyLength;
                if (frameEnd > size) break;
                messages.add(parse(position + WireFormat.LENGTH_PREFIX_BYTES, bodyLength));
                position = frameEnd;
            }
        } catch (ProtocolException e) {
            failed = true;
            throw e;
        }
        discard(position);
        return messages;
    }

    private void append(byte[] source, int offset, int length) {
        if (size + length > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, size + length));
        }
        System.arraycopy(source, offset, buffer, size, length);
        size += length;
    }

    private void discard(int consumed) {
        if (consumed == 0) return;
        System.arraycopy(buffer, consumed, buffer, 0, size - consumed);
        size -= consumed;
    }

    private Message parse(int offset, int length) throws ProtocolException {
        JsonNode root;
        try {
            root = WireFormat.MAPPER.readTree(buffer, offset, length);
        } catch (IOException e) {
            throw new ProtocolException(ErrorCode.MALFORMED_FRAME, "invalid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new ProtocolException(ErrorCode.MALFORMED_FRAME, "body is not a JSON object");
        }

        JsonNode version = root.get("version");
        if (version == null || !version.isInt()) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, "version");
        }
        if (version.intValue() != WireFormat.VERSION) {
            throw new ProtocolException(ErrorCode.UNSUPPORTED_VERSION,
                    "version " + version.intValue() + " != " + WireFormat.VERSION);
        }

        JsonNode sequence = root.get("sequence");
        if (sequence == null || !sequence.isIntegralNumber() || !sequence.canConvertToLong()) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, "sequence");
        }

        JsonNode typeNode = root.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, "type");
        }
        MessageType type;
        try {
            type = MessageType.valueOf(typeNode.textValue());
        } catch (IllegalArgumentException e) {
            throw new ProtocolException(ErrorCode.UNKNOWN_TYPE, typeNode.textValue());
        }

        JsonNode matchIdNode = root.get("matchId");
        String matchId = null;
        if (matchIdNode != null && !matchIdNode.isNull()) {
            if (!matchIdNode.isTextual()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "matchId");
            matchId = matchIdNode.textValue();
        }

        JsonNode revision = root.get("configRevision");
        if (revision == null || !revision.isInt()) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, "configRevision");
        }

        JsonNode payload = root.get("payload");
        if (payload == null || !payload.isObject()) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, "payload");
        }

        Message message;
        try {
            message = new Message(type, matchId, revision.intValue(), (ObjectNode) payload);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, e.getMessage());
        }

        long expected = lastSequence + 1;
        if (sequence.longValue() != expected) {
            throw new ProtocolException(ErrorCode.SEQUENCE_GAP,
                    "sequence " + sequence.longValue() + ", expected " + expected);
        }
        lastSequence = expected;
        return message;
    }
}
