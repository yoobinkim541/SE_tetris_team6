package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.protocol.ErrorCode;
import tetris.feature.network.protocol.FrameDecoder;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.LobbyPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.ProtocolException;

/** S8-C2: READY·START 로비 메시지 payload 변환 검증 (R3-READY) */
@Timeout(30)
class LobbyPayloadsTest {

    private static Message roundTrip(Message outbound) throws ProtocolException {
        byte[] encoded = new FrameEncoder().encode(outbound);
        return new FrameDecoder().feed(encoded, 0, encoded.length).getFirst();
    }

    @Test
    void READY는_프레임_왕복_후_불리언_값과_봉투_revision을_유지한다() throws Exception {
        Message readyTrue = roundTrip(LobbyPayloads.readyMessage(3, true));
        assertEquals(MessageType.READY, readyTrue.type());
        assertNull(readyTrue.matchId());
        assertEquals(3, readyTrue.configRevision());
        assertTrue(LobbyPayloads.readReady(readyTrue));

        Message readyFalse = roundTrip(LobbyPayloads.readyMessage(7, false));
        assertEquals(MessageType.READY, readyFalse.type());
        assertEquals(7, readyFalse.configRevision());
        assertFalse(LobbyPayloads.readReady(readyFalse));
    }

    @Test
    void START는_프레임_왕복_후_matchId와_봉투_revision을_유지한다() throws Exception {
        Message start = roundTrip(LobbyPayloads.startMessage("match-42", 5));
        assertEquals(MessageType.START, start.type());
        assertEquals("match-42", start.matchId());
        assertEquals(5, start.configRevision());
        assertEquals(0, start.payload().size());
    }

    @Test
    void 다른_종류_메시지를_readReady에_넘기면_IllegalArgumentException() {
        Message start = LobbyPayloads.startMessage("match-1", 1);
        assertThrows(IllegalArgumentException.class, () -> LobbyPayloads.readReady(start));
    }

    @Test
    void ready_필드가_없거나_불리언이_아니면_INVALID_FIELD를_던진다() {
        ObjectNode missing = JsonNodeFactory.instance.objectNode();
        ObjectNode textReady = JsonNodeFactory.instance.objectNode().put("ready", "true");
        ObjectNode intReady = JsonNodeFactory.instance.objectNode().put("ready", 1);
        ObjectNode nullReady = JsonNodeFactory.instance.objectNode().putNull("ready");

        for (ObjectNode bad : new ObjectNode[] {missing, textReady, intReady, nullReady}) {
            Message malformed = new Message(MessageType.READY, null, 1, bad);
            ProtocolException ex = assertThrows(
                    ProtocolException.class, () -> LobbyPayloads.readReady(malformed));
            assertEquals(ErrorCode.INVALID_FIELD, ex.code());
        }
    }

    @Test
    void 봉투_규칙을_어긴_revision이나_matchId는_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> LobbyPayloads.readyMessage(0, true));
        assertThrows(IllegalArgumentException.class, () -> LobbyPayloads.startMessage(null, 1));
        assertThrows(IllegalArgumentException.class, () -> LobbyPayloads.startMessage("  ", 1));
        assertThrows(IllegalArgumentException.class, () -> LobbyPayloads.startMessage("match-1", 0));
    }
}
