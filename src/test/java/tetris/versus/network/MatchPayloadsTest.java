package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tetris.feature.game.Difficulty;
import tetris.feature.network.protocol.ErrorCode;
import tetris.feature.network.protocol.FrameDecoder;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.MatchPayloads;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.MessageType;
import tetris.feature.network.protocol.ProtocolException;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchResult;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.MatchSnapshot.PlayerView;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

/** CONFIG·INPUT·STATE·RESULT payload와 계약 v1(feature.versus.api) 사이 변환 (S7-C1, R3-LIVE, R3-GARBAGE) */
public class MatchPayloadsTest {
    /** 실제 송수신과 같게 프레임으로 인코딩·디코딩한 메시지 */
    private static Message overWire(Message message) throws ProtocolException {
        byte[] frame = new FrameEncoder().encode(message);
        return new FrameDecoder().feed(frame, 0, frame.length).get(0);
    }

    private static int[][] board(int seed) {
        int[][] cells = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        for (int r = 0; r < cells.length; r++) {
            for (int c = 0; c < cells[r].length; c++) cells[r][c] = (r * 3 + c + seed) % (VersusRules.ATTACK_CELL + 1);
        }
        return cells;
    }

    private static int[] attackRow(int hole) {
        int[] row = new int[VersusRules.BOARD_COLS];
        Arrays.fill(row, VersusRules.ATTACK_CELL);
        row[hole] = 0;
        return row;
    }

    private static List<int[]> attackRows(int count) {
        List<int[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) rows.add(attackRow(i % VersusRules.BOARD_COLS));
        return rows;
    }

    private static PlayerView view(int seed, long score, int pending) {
        int[][] next = {{0, seed % 8 + 1, 0}, {seed % 8 + 1, seed % 8 + 1, seed % 8 + 1}};
        return new PlayerView(board(seed), next, score, attackRows(pending));
    }

    private static MatchSnapshot playing() {
        return new MatchSnapshot("m-7", 41, view(1, 1200, 3), view(2, 0, 0), 95_000, MatchPhase.PLAYING, null);
    }

    private static void assertSameView(PlayerView expected, PlayerView actual) {
        assertArrayEquals(expected.cells(), actual.cells());
        assertArrayEquals(expected.next(), actual.next());
        assertEquals(expected.score(), actual.score());
        assertArrayEquals(expected.pendingRows().toArray(int[][]::new), actual.pendingRows().toArray(int[][]::new));
    }

    private static void assertSameSnapshot(MatchSnapshot expected, MatchSnapshot actual) {
        assertEquals(expected.matchId(), actual.matchId());
        assertEquals(expected.revision(), actual.revision());
        assertEquals(expected.remainingMillis(), actual.remainingMillis());
        assertEquals(expected.phase(), actual.phase());
        assertEquals(expected.result(), actual.result());
        assertSameView(expected.one(), actual.one());
        assertSameView(expected.two(), actual.two());
    }

    /** 정상 메시지의 payload 일부를 바꿔 다시 만든 메시지 */
    private static Message tampered(Message valid, Consumer<ObjectNode> edit) {
        ObjectNode payload = valid.payload();
        edit.accept(payload);
        return new Message(valid.type(), valid.matchId(), valid.configRevision(), payload);
    }

    private static ErrorCode rejects(ThrowingRead read) {
        return assertThrows(ProtocolException.class, read::run).code();
    }

    @FunctionalInterface
    private interface ThrowingRead {
        void run() throws ProtocolException;
    }

    //#region 왕복
    @Test
    void CONFIG는_모드_난이도_제한시간과_revision을_그대로_전달한다() throws ProtocolException {
        MatchConfig config = new MatchConfig(BattleMode.TIMED, Difficulty.HARD, 120_000);

        Message received = overWire(MatchPayloads.configMessage(config, 4));

        assertEquals(MessageType.CONFIG, received.type());
        assertEquals(4, received.configRevision());
        assertEquals(config, MatchPayloads.readConfig(received));
    }

    @Test
    void INPUT은_플레이어_동작_누름_순번을_그대로_전달한다() throws ProtocolException {
        PlayerCommand release = new PlayerCommand(PlayerId.TWO, GameAction.HARD_DROP, false, 9_000_000_000L);

        Message received = overWire(MatchPayloads.inputMessage("m-7", release));

        assertEquals("m-7", received.matchId());
        assertEquals(release, MatchPayloads.readInput(received));
    }

    @Test
    void STATE는_두_보드_Next_점수_대기열_남은시간을_그대로_전달한다() throws ProtocolException {
        MatchSnapshot snapshot = playing();

        Message received = overWire(MatchPayloads.stateMessage(snapshot));

        assertEquals("m-7", received.matchId());
        assertSameSnapshot(snapshot, MatchPayloads.readState(received));
    }

    @Test
    void STATE는_공격칸_값_9와_10줄_대기열과_빈_Next를_유지한다() throws ProtocolException {
        PlayerView full = new PlayerView(board(0), new int[0][0], Long.MAX_VALUE, attackRows(VersusRules.MAX_PENDING_ROWS));
        MatchSnapshot snapshot = new MatchSnapshot("m", 0, full, view(3, 5, 0), -1, MatchPhase.PAUSED, null);

        assertSameSnapshot(snapshot, MatchPayloads.readState(overWire(MatchPayloads.stateMessage(snapshot))));
    }

    @Test
    void 끝난_경기의_STATE는_결과를_함께_전달한다() throws ProtocolException {
        MatchSnapshot finished = new MatchSnapshot("m", 99, view(1, 10, 0), view(2, 10, 0), 0,
                MatchPhase.FINISHED, MatchResult.draw(MatchResult.EndReason.TIME_UP));

        assertSameSnapshot(finished, MatchPayloads.readState(overWire(MatchPayloads.stateMessage(finished))));
    }

    @Test
    void RESULT는_승자와_종료_이유를_전달하고_무승부는_승자가_없다() throws ProtocolException {
        MatchResult forfeit = MatchResult.win(PlayerId.ONE, MatchResult.EndReason.FORFEIT);
        MatchResult draw = MatchResult.draw(MatchResult.EndReason.TOPPED_OUT);

        assertEquals(forfeit, MatchPayloads.readResult(overWire(MatchPayloads.resultMessage("m", forfeit))));
        assertEquals(draw, MatchPayloads.readResult(overWire(MatchPayloads.resultMessage("m", draw))));
    }

    @Test
    void 가장_큰_STATE도_한_프레임에_들어간다() throws ProtocolException {
        int[][] next = {{8, 8, 8, 8}, {8, 8, 8, 8}, {8, 8, 8, 8}, {8, 8, 8, 8}};
        int[][] cells = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        for (int[] row : cells) Arrays.fill(row, VersusRules.ATTACK_CELL);
        PlayerView worst = new PlayerView(cells, next, Long.MAX_VALUE, attackRows(VersusRules.MAX_PENDING_ROWS));
        MatchSnapshot snapshot = new MatchSnapshot("m-" + "9".repeat(36), Long.MAX_VALUE, worst, worst,
                Long.MAX_VALUE, MatchPhase.FINISHED, MatchResult.win(PlayerId.TWO, MatchResult.EndReason.FORFEIT));

        assertSameSnapshot(snapshot, MatchPayloads.readState(overWire(MatchPayloads.stateMessage(snapshot))));
    }
    //#endregion

    //#region 받은 payload 검증
    @Test
    void 보드_크기가_20x10이_아니면_거부한다() throws ProtocolException {
        Message valid = MatchPayloads.stateMessage(playing());
        Message shortBoard = tampered(valid, p -> ((ArrayNode) p.get("one").get("cells")).remove(0));

        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readState(shortBoard)));
    }

    @Test
    void 칸_값이_0부터_9_밖이면_거부한다() throws ProtocolException {
        Message valid = MatchPayloads.stateMessage(playing());
        Message badCell = tampered(valid, p -> ((ArrayNode) p.get("two").get("cells").get(0)).set(0, 10));

        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readState(badCell)));
    }

    @Test
    void 대기열이_10줄을_넘거나_공격줄_값이_틀리면_거부한다() throws ProtocolException {
        Message valid = MatchPayloads.stateMessage(playing());
        Message tooMany = tampered(valid, p -> {
            ArrayNode pending = (ArrayNode) p.get("one").get("pendingRows");
            while (pending.size() <= VersusRules.MAX_PENDING_ROWS) pending.add(pending.get(0).deepCopy());
        });
        Message colored = tampered(valid, p -> ((ArrayNode) p.get("one").get("pendingRows").get(0)).set(1, 3));

        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readState(tooMany)));
        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readState(colored)));
    }

    @Test
    void 끝나지_않은_경기에_결과가_있으면_거부한다() throws ProtocolException {
        Message valid = MatchPayloads.stateMessage(playing());
        Message early = tampered(valid, p -> p.set("result",
                p.objectNode().put("winner", "ONE").put("reason", "TOPPED_OUT")));

        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readState(early)));
    }

    @Test
    void 정의되지_않은_열거값은_거부한다() throws ProtocolException {
        Message config = MatchPayloads.configMessage(MatchConfig.of(BattleMode.CLASSIC), 1);
        Message input = MatchPayloads.inputMessage("m", new PlayerCommand(PlayerId.ONE, GameAction.ROTATE, true, 0));
        Message result = MatchPayloads.resultMessage("m", MatchResult.win(PlayerId.ONE, MatchResult.EndReason.TIME_UP));

        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readConfig(tampered(config, p -> p.put("mode", "BLITZ")))));
        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readInput(tampered(input, p -> p.put("player", "THREE")))));
        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readResult(tampered(result, p -> p.put("reason", "LAG")))));
    }

    @Test
    void 필드_타입이_다르거나_빠지면_거부한다() throws ProtocolException {
        Message input = MatchPayloads.inputMessage("m", new PlayerCommand(PlayerId.ONE, GameAction.ROTATE, true, 0));
        Message state = MatchPayloads.stateMessage(playing());

        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readInput(tampered(input, p -> p.put("pressed", "true")))));
        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readInput(tampered(input, p -> p.remove("sequence")))));
        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readState(tampered(state, p -> p.put("revision", 1.5)))));
        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readState(tampered(state, p -> p.remove("two")))));
    }

    @Test
    void 계약_규칙에_어긋난_값은_거부한다() throws ProtocolException {
        Message timed = MatchPayloads.configMessage(MatchConfig.of(BattleMode.TIMED), 1);
        Message forfeit = MatchPayloads.resultMessage("m", MatchResult.win(PlayerId.TWO, MatchResult.EndReason.FORFEIT));
        Message input = MatchPayloads.inputMessage("m", new PlayerCommand(PlayerId.ONE, GameAction.ROTATE, true, 0));

        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readConfig(tampered(timed, p -> p.put("timeLimitMillis", 0)))));
        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readResult(tampered(forfeit, p -> p.putNull("winner")))));
        assertEquals(ErrorCode.INVALID_FIELD, rejects(() -> MatchPayloads.readInput(tampered(input, p -> p.put("sequence", -1)))));
    }

    @Test
    void 다른_종류의_메시지를_읽으려_하면_호출_오류다() {
        Message ping = new Message(MessageType.PING, null, 0, MatchPayloads.configMessage(MatchConfig.of(BattleMode.ITEM), 1).payload());

        assertThrows(IllegalArgumentException.class, () -> MatchPayloads.readConfig(ping));
    }
    //#endregion
}
