package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.network.protocol.ControlPayloads;
import tetris.feature.network.protocol.ErrorCode;
import tetris.feature.network.protocol.FrameDecoder;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.PeerRole;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.network.protocol.WireFormat;

/** S7-C1 전송 계약: 길이 접두 JSON 프레임, 버전·순번·matchId·설정 revision 검증 (R3-NETTEST) */
public class FrameCodecTest {
    @Test
    void payloadAccessCannotChangeTheMessageAwaitingSerialization() throws ProtocolException {
        ObjectNode source = payload("tick", 7);
        source.putObject("nested").put("value", 1);
        Message message = new Message(MessageType.STATE, "m", 0, source);
        source.put("tick", 8);
        ObjectNode exposed = message.payload();
        exposed.put("tick", 9);
        ((ObjectNode) exposed.get("nested")).put("value", 2);

        assertEquals(7, message.payload().get("tick").intValue());
        assertEquals(1, message.payload().get("nested").get("value").intValue());
        byte[] frame = new FrameEncoder().encode(message);
        Message received = new FrameDecoder().feed(frame, 0, frame.length).getFirst();
        assertEquals(7, received.payload().get("tick").intValue());
        assertEquals(1, received.payload().get("nested").get("value").intValue());
    }

    private static ObjectNode payload(String key, int value) {
        return JsonNodeFactory.instance.objectNode().put(key, value);
    }

    private static Message state(String matchId, int tick) {
        return new Message(MessageType.STATE, matchId, 0, payload("tick", tick));
    }

    /** 다른 구현이 보냈다고 가정한 프레임: 4바이트 big-endian 길이 + UTF-8 JSON */
    private static byte[] rawFrame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(4 + body.length).putInt(body.length).put(body).array();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.writeBytes(part);
        return out.toByteArray();
    }

    private static ProtocolException decodeFails(byte[] bytes) {
        return assertThrows(ProtocolException.class, () -> new FrameDecoder().feed(bytes, 0, bytes.length));
    }

    //#region 프레이밍
    @Test
    void 인코딩한_메시지를_디코딩하면_같은_메시지가_된다() throws ProtocolException {
        Message sent = new Message(MessageType.START, "match-1", 3, payload("seed", 42));
        byte[] frame = new FrameEncoder().encode(sent);

        List<Message> received = new FrameDecoder().feed(frame, 0, frame.length);

        assertEquals(List.of(sent), received);
    }

    @Test
    void 프레임을_한_바이트씩_나눠_받아도_마지막_바이트에서_한_번만_복원한다() throws ProtocolException {
        Message sent = state("m", 7);
        byte[] frame = new FrameEncoder().encode(sent);
        FrameDecoder decoder = new FrameDecoder();
        List<Message> received = new ArrayList<>();

        for (int i = 0; i < frame.length; i++) {
            List<Message> step = decoder.feed(frame, i, 1);
            if (i < frame.length - 1) assertTrue(step.isEmpty(), "완성 전 " + i + "번째 바이트에서 메시지가 나옴");
            received.addAll(step);
        }

        assertEquals(List.of(sent), received);
    }

    @Test
    void 여러_프레임이_한_번에_와도_보낸_순서대로_모두_복원한다() throws ProtocolException {
        FrameEncoder encoder = new FrameEncoder();
        List<Message> sent = List.of(state("m", 1), state("m", 2), state("m", 3));
        byte[] joined = concat(encoder.encode(sent.get(0)), encoder.encode(sent.get(1)), encoder.encode(sent.get(2)));

        assertEquals(sent, new FrameDecoder().feed(joined, 0, joined.length));
    }

    @Test
    void 프레임_경계가_읽기_경계와_어긋나도_복원한다() throws ProtocolException {
        FrameEncoder encoder = new FrameEncoder();
        byte[] joined = concat(encoder.encode(state("m", 1)), encoder.encode(state("m", 2)));
        FrameDecoder decoder = new FrameDecoder();
        int cut = 6; // 첫 프레임 본문 중간

        List<Message> first = decoder.feed(joined, 0, cut);
        List<Message> rest = decoder.feed(joined, cut, joined.length - cut);

        assertTrue(first.isEmpty());
        assertEquals(List.of(state("m", 1), state("m", 2)), rest);
    }

    @Test
    void 인코딩_결과는_4바이트_길이_접두와_UTF8_JSON_본문이다() throws ProtocolException {
        byte[] frame = new FrameEncoder().encode(new Message(MessageType.PING, null, 0, payload("nonce", 1)));

        int length = ByteBuffer.wrap(frame, 0, 4).getInt();
        String json = new String(frame, 4, frame.length - 4, StandardCharsets.UTF_8);

        assertEquals(frame.length - 4, length);
        assertTrue(json.contains("\"version\":1"), json);
        assertTrue(json.contains("\"sequence\":1"), json);
        assertTrue(json.contains("\"type\":\"PING\""), json);
    }
    //#endregion

    //#region 크기 제한
    @Test
    void 길이_접두가_64KiB를_넘으면_본문을_기다리지_않고_거부한다() {
        byte[] header = ByteBuffer.allocate(4).putInt(WireFormat.MAX_FRAME_BYTES + 1).array();

        assertEquals(ErrorCode.FRAME_TOO_LARGE, decodeFails(header).code());
    }

    @Test
    void 길이_접두가_0이하이면_잘못된_프레임이다() {
        assertEquals(ErrorCode.MALFORMED_FRAME, decodeFails(ByteBuffer.allocate(4).putInt(0).array()).code());
        assertEquals(ErrorCode.MALFORMED_FRAME, decodeFails(ByteBuffer.allocate(4).putInt(-5).array()).code());
    }

    @Test
    void 정확히_64KiB인_프레임은_받는다() throws ProtocolException {
        String prefix = "{\"version\":1,\"sequence\":1,\"type\":\"STATE\",\"matchId\":\"m\",\"configRevision\":0,\"payload\":{\"pad\":\"";
        String suffix = "\"}}";
        int padding = WireFormat.MAX_FRAME_BYTES - prefix.length() - suffix.length();
        byte[] frame = rawFrame(prefix + "x".repeat(padding) + suffix);

        List<Message> received = new FrameDecoder().feed(frame, 0, frame.length);

        assertEquals(WireFormat.MAX_FRAME_BYTES + 4, frame.length);
        assertEquals(padding, received.get(0).payload().get("pad").asText().length());
    }

    @Test
    void 보내는_쪽에서도_64KiB를_넘는_메시지는_인코딩하지_않는다() {
        ObjectNode big = JsonNodeFactory.instance.objectNode().put("pad", "x".repeat(WireFormat.MAX_FRAME_BYTES));
        Message message = new Message(MessageType.STATE, "m", 0, big);

        ProtocolException error = assertThrows(ProtocolException.class, () -> new FrameEncoder().encode(message));

        assertEquals(ErrorCode.FRAME_TOO_LARGE, error.code());
    }
    //#endregion

    //#region 필드 검증
    @Test
    void JSON_구문이_깨지면_잘못된_프레임이다() {
        assertEquals(ErrorCode.MALFORMED_FRAME, decodeFails(rawFrame("{\"version\":1,")).code());
    }

    @Test
    void JSON_객체가_아니면_잘못된_프레임이다() {
        assertEquals(ErrorCode.MALFORMED_FRAME, decodeFails(rawFrame("[1,2]")).code());
    }

    @Test
    void 다른_프로토콜_버전은_거부한다() {
        String json = "{\"version\":2,\"sequence\":1,\"type\":\"PING\",\"configRevision\":0,\"payload\":{}}";

        assertEquals(ErrorCode.UNSUPPORTED_VERSION, decodeFails(rawFrame(json)).code());
    }

    @Test
    void 모르는_메시지_종류는_거부한다() {
        String json = "{\"version\":1,\"sequence\":1,\"type\":\"CHAT\",\"configRevision\":0,\"payload\":{}}";

        assertEquals(ErrorCode.UNKNOWN_TYPE, decodeFails(rawFrame(json)).code());
    }

    @Test
    void 경기_메시지에_matchId가_없으면_거부한다() {
        String json = "{\"version\":1,\"sequence\":1,\"type\":\"INPUT\",\"configRevision\":0,\"payload\":{}}";

        assertEquals(ErrorCode.INVALID_FIELD, decodeFails(rawFrame(json)).code());
    }

    @Test
    void 준비_메시지에_설정_revision이_없으면_거부한다() {
        String json = "{\"version\":1,\"sequence\":1,\"type\":\"READY\",\"configRevision\":0,\"payload\":{}}";

        assertEquals(ErrorCode.INVALID_FIELD, decodeFails(rawFrame(json)).code());
    }

    @Test
    void payload가_객체가_아니면_거부한다() {
        String json = "{\"version\":1,\"sequence\":1,\"type\":\"PING\",\"configRevision\":0,\"payload\":3}";

        assertEquals(ErrorCode.INVALID_FIELD, decodeFails(rawFrame(json)).code());
    }

    @Test
    void 숫자_필드에_문자열이_오면_거부한다() {
        String json = "{\"version\":1,\"sequence\":\"1\",\"type\":\"PING\",\"configRevision\":0,\"payload\":{}}";

        assertEquals(ErrorCode.INVALID_FIELD, decodeFails(rawFrame(json)).code());
    }

    @Test
    void 로컬에서도_규칙에_어긋난_메시지는_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> new Message(MessageType.STATE, " ", 0, payload("a", 1)));
        assertThrows(IllegalArgumentException.class, () -> new Message(MessageType.START, "m", 0, payload("a", 1)));
        assertThrows(IllegalArgumentException.class, () -> new Message(MessageType.PING, null, -1, payload("a", 1)));
    }
    //#endregion

    //#region 순번
    @Test
    void 순번은_연결마다_1부터_하나씩_증가해야_한다() throws ProtocolException {
        String first = "{\"version\":1,\"sequence\":1,\"type\":\"PING\",\"configRevision\":0,\"payload\":{}}";
        String skipped = "{\"version\":1,\"sequence\":3,\"type\":\"PING\",\"configRevision\":0,\"payload\":{}}";
        FrameDecoder decoder = new FrameDecoder();
        byte[] ok = rawFrame(first);
        decoder.feed(ok, 0, ok.length);
        byte[] gap = rawFrame(skipped);

        ProtocolException error = assertThrows(ProtocolException.class, () -> decoder.feed(gap, 0, gap.length));

        assertEquals(ErrorCode.SEQUENCE_GAP, error.code());
    }

    @Test
    void 같은_순번이_다시_오면_재전송으로_보고_거부한다() throws ProtocolException {
        FrameEncoder encoder = new FrameEncoder();
        byte[] frame = encoder.encode(state("m", 1));
        FrameDecoder decoder = new FrameDecoder();
        decoder.feed(frame, 0, frame.length);

        ProtocolException error = assertThrows(ProtocolException.class, () -> decoder.feed(frame, 0, frame.length));

        assertEquals(ErrorCode.SEQUENCE_GAP, error.code());
    }

    @Test
    void 오류가_난_디코더는_이후_입력을_받지_않는다() {
        FrameDecoder decoder = new FrameDecoder();
        byte[] bad = rawFrame("not json");
        assertThrows(ProtocolException.class, () -> decoder.feed(bad, 0, bad.length));

        assertThrows(IllegalStateException.class, () -> decoder.feed(new byte[] {0}, 0, 1));
    }

    @Test
    void 잘못된_프레임_앞의_정상_메시지는_TCP_분할과_무관하게_먼저_전달한다() throws ProtocolException {
        byte[] valid = new FrameEncoder().encode(state("m", 1));
        byte[] joined = concat(valid, rawFrame("not json"));
        List<Message> delivered = new ArrayList<>();

        ProtocolException error = assertThrows(ProtocolException.class,
                () -> new FrameDecoder().feed(joined, 0, joined.length, delivered::add));

        assertEquals(ErrorCode.MALFORMED_FRAME, error.code());
        assertEquals(List.of(state("m", 1)), delivered);
    }

    @Test
    void 인코더는_메시지마다_순번을_붙인다() throws ProtocolException {
        FrameEncoder encoder = new FrameEncoder();
        byte[] a = encoder.encode(state("m", 1));
        byte[] b = encoder.encode(state("m", 1));

        String json = new String(b, 4, b.length - 4, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"sequence\":2"), json);
        assertEquals(2, new FrameDecoder().feed(concat(a, b), 0, a.length + b.length).size());
    }
    //#endregion

    //#region 연결 제어 payload
    @Test
    void 연결_제어_payload는_JSON으로_왕복한다() throws ProtocolException {
        ControlPayloads.Hello hello = new ControlPayloads.Hello(PeerRole.HOST);
        ControlPayloads.Ping ping = new ControlPayloads.Ping(9, 123_456_789L);
        ControlPayloads.Leave leave = new ControlPayloads.Leave("user quit");
        ControlPayloads.Failure failure = new ControlPayloads.Failure(ErrorCode.SEQUENCE_GAP, "expected 4");

        assertEquals(hello, ControlPayloads.Hello.from(hello.toJson()));
        assertEquals(ping, ControlPayloads.Ping.from(ping.toJson()));
        assertEquals(leave, ControlPayloads.Leave.from(leave.toJson()));
        assertEquals(failure, ControlPayloads.Failure.from(failure.toJson()));
    }

    @Test
    void PONG은_받은_PING의_nonce와_보낸_시각을_그대로_돌려준다() throws ProtocolException {
        ControlPayloads.Ping ping = new ControlPayloads.Ping(41, 5_000L);

        Message pong = ping.pong();

        assertEquals(MessageType.PONG, pong.type());
        assertEquals(ping, ControlPayloads.Ping.from(pong.payload()));
    }

    @Test
    void 연결_제어_payload에_필수_필드가_없으면_거부한다() {
        ObjectNode empty = JsonNodeFactory.instance.objectNode();
        ObjectNode badRole = JsonNodeFactory.instance.objectNode().put("role", "OBSERVER");

        assertEquals(ErrorCode.INVALID_FIELD,
                assertThrows(ProtocolException.class, () -> ControlPayloads.Ping.from(empty)).code());
        assertEquals(ErrorCode.INVALID_FIELD,
                assertThrows(ProtocolException.class, () -> ControlPayloads.Hello.from(badRole)).code());
    }

    @Test
    void 연결_제어_메시지는_종류에_맞는_payload를_담는다() throws ProtocolException {
        Message message = ControlPayloads.Hello.message(PeerRole.CLIENT);
        byte[] frame = new FrameEncoder().encode(message);

        Message received = new FrameDecoder().feed(frame, 0, frame.length).get(0);

        assertEquals(MessageType.HELLO, received.type());
        assertEquals(PeerRole.CLIENT, ControlPayloads.Hello.from(received.payload()).role());
        assertArrayEquals(frame, new FrameEncoder().encode(received));
    }
    //#endregion
}
