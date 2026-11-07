package tetris.feature.network.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** 엔진과 무관한 연결 제어 메시지 payload. C 트랙이 소유한다 */
public final class ControlPayloads {
    private ControlPayloads() {
    }

    /** 연결 직후 서로 보내는 첫 메시지. 버전은 봉투에서 이미 검사한다 */
    public record Hello(PeerRole role) {
        public Hello {
            Objects.requireNonNull(role, "role");
        }

        public static Message message(PeerRole role) {
            return new Message(MessageType.HELLO, null, 0, new Hello(role).toJson());
        }

        public ObjectNode toJson() {
            return WireFormat.MAPPER.createObjectNode().put("role", role.name());
        }

        public static Hello from(JsonNode payload) throws ProtocolException {
            String role = requiredText(payload, "role");
            try {
                return new Hello(PeerRole.valueOf(role));
            } catch (IllegalArgumentException e) {
                throw new ProtocolException(ErrorCode.INVALID_FIELD, "role " + role);
            }
        }
    }

    /**
     * PING과 PONG이 같은 모양을 쓴다. 받은 쪽은 그대로 PONG으로 돌려주고, 보낸 쪽은 자기 시계로 RTT를 잰다.
     *
     * @param sentAtNanos 보낸 쪽의 {@link System#nanoTime()}. 다른 PC의 시계와 비교하지 않는다
     */
    public record Ping(long nonce, long sentAtNanos) {
        public Message message() {
            return new Message(MessageType.PING, null, 0, toJson());
        }

        public Message pong() {
            return new Message(MessageType.PONG, null, 0, toJson());
        }

        public ObjectNode toJson() {
            return WireFormat.MAPPER.createObjectNode().put("nonce", nonce).put("sentAtNanos", sentAtNanos);
        }

        public static Ping from(JsonNode payload) throws ProtocolException {
            return new Ping(requiredLong(payload, "nonce"), requiredLong(payload, "sentAtNanos"));
        }
    }

    /** 정상 종료 통지. 받은 쪽은 EOF를 기다리지 않고 연결을 닫는다 */
    public record Leave(String reason) {
        public Leave {
            Objects.requireNonNull(reason, "reason");
        }

        public Message message() {
            return new Message(MessageType.LEAVE, null, 0, toJson());
        }

        public ObjectNode toJson() {
            return WireFormat.MAPPER.createObjectNode().put("reason", reason);
        }

        public static Leave from(JsonNode payload) throws ProtocolException {
            return new Leave(requiredText(payload, "reason"));
        }
    }

    /** 상대의 프로토콜 위반을 알리고 연결을 끊기 직전에 보낸다 */
    public record Failure(ErrorCode code, String detail) {
        public Failure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
        }

        public Message message() {
            return new Message(MessageType.ERROR, null, 0, toJson());
        }

        public ObjectNode toJson() {
            return WireFormat.MAPPER.createObjectNode().put("code", code.name()).put("detail", detail);
        }

        public static Failure from(JsonNode payload) throws ProtocolException {
            String code = requiredText(payload, "code");
            try {
                return new Failure(ErrorCode.valueOf(code), requiredText(payload, "detail"));
            } catch (IllegalArgumentException e) {
                throw new ProtocolException(ErrorCode.INVALID_FIELD, "code " + code);
            }
        }
    }

    private static String requiredText(JsonNode payload, String field) throws ProtocolException {
        JsonNode node = payload.get(field);
        if (node == null || !node.isTextual()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        return node.textValue();
    }

    private static long requiredLong(JsonNode payload, String field) throws ProtocolException {
        JsonNode node = payload.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        }
        return node.longValue();
    }
}
