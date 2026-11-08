package tetris.versus.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tetris.feature.network.protocol.*;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.*;
import tetris.ui.versus.FakeBattleClient;

/** S7 DTO-consumer acceptance, not a production match transport adapter. */
class CommonWireContractTest {
    @Test void commonConfigCommandAndSnapshotFitTheActualFrameContract() throws Exception {
        var mapper=new ObjectMapper(); var encoder=new FrameEncoder(); var decoder=new FrameDecoder();
        var config=MatchConfig.of(BattleMode.TIMED);
        ObjectNode configPayload=mapper.valueToTree(config);
        byte[] configFrame=encoder.encode(new Message(MessageType.CONFIG,null,1,configPayload));
        var receivedConfig=decoder.feed(configFrame,0,configFrame.length).getFirst();
        assertEquals("TIMED",receivedConfig.payload().get("mode").textValue());
        assertEquals(config.timeLimitMillis(),receivedConfig.payload().get("timeLimitMillis").longValue());

        var command=new PlayerCommand(PlayerId.TWO,GameAction.ROTATE,true,7);
        byte[] inputFrame=encoder.encode(new Message(MessageType.INPUT,"fake-s7",0,mapper.valueToTree(command)));
        var receivedInput=decoder.feed(inputFrame,0,inputFrame.length).getFirst();
        assertEquals("TWO",receivedInput.payload().get("playerId").textValue());
        assertEquals(7,receivedInput.payload().get("sequence").longValue());

        var snapshot=FakeBattleClient.sample();
        byte[] stateFrame=encoder.encode(new Message(MessageType.STATE,snapshot.matchId(),0,mapper.valueToTree(snapshot)));
        assertTrue(stateFrame.length<=WireFormat.MAX_FRAME_BYTES+Integer.BYTES);
        var receivedState=decoder.feed(stateFrame,0,stateFrame.length).getFirst();
        assertEquals(snapshot.matchId(),receivedState.payload().get("matchId").textValue());
        assertEquals(20,receivedState.payload().get("one").get("cells").size());
        assertEquals(10,receivedState.payload().get("two").get("pendingRows").size());
    }
}
