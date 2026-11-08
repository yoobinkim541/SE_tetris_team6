package tetris.versus.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tetris.feature.network.protocol.*;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.*;
import tetris.ui.versus.FakeBattleClient;

/** S7 DTO-consumer acceptance, not a production match transport adapter. */
class CommonWireContractTest {
    @Test void commonConfigCommandAndSnapshotFitTheActualFrameContract() throws Exception {
        var encoder=new FrameEncoder(); var decoder=new FrameDecoder();
        var config=MatchConfig.of(BattleMode.TIMED);
        byte[] configFrame=encoder.encode(MatchPayloads.configMessage(config,1));
        var receivedConfig=decoder.feed(configFrame,0,configFrame.length).getFirst();
        assertEquals("TIMED",receivedConfig.payload().get("mode").textValue());
        assertEquals(config.timeLimitMillis(),receivedConfig.payload().get("timeLimitMillis").longValue());
        assertEquals(config,MatchPayloads.readConfig(receivedConfig));

        var command=new PlayerCommand(PlayerId.TWO,GameAction.ROTATE,true,7);
        byte[] inputFrame=encoder.encode(MatchPayloads.inputMessage("fake-s7",command));
        var receivedInput=decoder.feed(inputFrame,0,inputFrame.length).getFirst();
        assertEquals(command,MatchPayloads.readInput(receivedInput));
        assertEquals(7,receivedInput.payload().get("sequence").longValue());

        var snapshot=FakeBattleClient.sample();
        byte[] stateFrame=encoder.encode(MatchPayloads.stateMessage(snapshot));
        assertTrue(stateFrame.length<=WireFormat.MAX_FRAME_BYTES+Integer.BYTES);
        var receivedState=decoder.feed(stateFrame,0,stateFrame.length).getFirst();
        assertEquals(snapshot.matchId(),MatchPayloads.readState(receivedState).matchId());
        assertEquals(20,receivedState.payload().get("one").get("cells").size());
        assertEquals(10,receivedState.payload().get("two").get("pendingRows").size());
    }
}
