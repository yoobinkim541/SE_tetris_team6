package tetris.feature.network.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import tetris.feature.game.Difficulty;
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

/**
 * 경기 메시지(CONFIG·INPUT·STATE·RESULT) payload와 계약 v1({@code tetris.feature.versus.api}) 사이 변환.
 *
 * <p>네트워크로는 계약 객체를 그대로 직렬화하지 않고 아래 필드만 보낸다. 받은 쪽은 JSON 타입을 엄격히 검사한 뒤
 * 계약 생성자로 다시 만들어 값 범위(보드 20×10, 칸 0~9, 대기열 0~10줄, FINISHED일 때만 결과 등)를 같은 규칙으로 검증한다.
 * 어긋나면 {@link ErrorCode#INVALID_FIELD}. 형식은 {@code docs/req3/network-protocol.md} 3절.
 */
public final class MatchPayloads {
    private MatchPayloads() {
    }

    //#region CONFIG {"mode","difficulty","timeLimitMillis"}
    /** 호스트가 고른 설정. configRevision은 로비가 설정을 바꿀 때마다 올린 값(1 이상) */
    public static Message configMessage(MatchConfig config, int configRevision) {
        ObjectNode payload = WireFormat.MAPPER.createObjectNode()
                .put("mode", config.mode().name())
                .put("difficulty", config.difficulty().name())
                .put("timeLimitMillis", config.timeLimitMillis());
        return new Message(MessageType.CONFIG, null, configRevision, payload);
    }

    public static MatchConfig readConfig(Message message) throws ProtocolException {
        ObjectNode payload = payloadOf(message, MessageType.CONFIG);
        BattleMode mode = enumField(payload, "mode", BattleMode.class);
        Difficulty difficulty = enumField(payload, "difficulty", Difficulty.class);
        long timeLimit = longField(payload, "timeLimitMillis");
        return construct(() -> new MatchConfig(mode, difficulty, timeLimit));
    }
    //#endregion

    //#region INPUT {"player","action","pressed","sequence"}
    public static Message inputMessage(String matchId, PlayerCommand command) {
        ObjectNode payload = WireFormat.MAPPER.createObjectNode()
                .put("player", command.playerId().name())
                .put("action", command.action().name())
                .put("pressed", command.pressed())
                .put("sequence", command.sequence());
        return new Message(MessageType.INPUT, matchId, 0, payload);
    }

    public static PlayerCommand readInput(Message message) throws ProtocolException {
        ObjectNode payload = payloadOf(message, MessageType.INPUT);
        PlayerId player = enumField(payload, "player", PlayerId.class);
        GameAction action = enumField(payload, "action", GameAction.class);
        boolean pressed = booleanField(payload, "pressed");
        long sequence = longField(payload, "sequence");
        return construct(() -> new PlayerCommand(player, action, pressed, sequence));
    }
    //#endregion

    //#region STATE {"revision","remainingMillis","phase","result","one","two"}
    /** matchId는 봉투에만 싣는다. 받은 쪽은 봉투의 matchId로 스냅샷을 만든다 */
    public static Message stateMessage(MatchSnapshot snapshot) {
        ObjectNode payload = WireFormat.MAPPER.createObjectNode()
                .put("revision", snapshot.revision())
                .put("remainingMillis", snapshot.remainingMillis())
                .put("phase", snapshot.phase().name());
        if (snapshot.result() == null) payload.putNull("result");
        else payload.set("result", resultJson(snapshot.result()));
        payload.set("one", viewJson(snapshot.one()));
        payload.set("two", viewJson(snapshot.two()));
        return new Message(MessageType.STATE, snapshot.matchId(), 0, payload);
    }

    public static MatchSnapshot readState(Message message) throws ProtocolException {
        ObjectNode payload = payloadOf(message, MessageType.STATE);
        long revision = longField(payload, "revision");
        long remaining = longField(payload, "remainingMillis");
        MatchPhase phase = enumField(payload, "phase", MatchPhase.class);
        JsonNode resultNode = payload.get("result");
        if (resultNode == null) throw new ProtocolException(ErrorCode.INVALID_FIELD, "result");
        MatchResult result = resultNode.isNull() ? null : readResultJson(resultNode);
        PlayerView one = readView(objectField(payload, "one"));
        PlayerView two = readView(objectField(payload, "two"));
        return construct(() -> new MatchSnapshot(message.matchId(), revision, one, two, remaining, phase, result));
    }

    private static ObjectNode viewJson(PlayerView view) {
        ObjectNode node = WireFormat.MAPPER.createObjectNode();
        node.set("cells", gridJson(view.cells()));
        node.set("next", gridJson(view.next()));
        node.put("score", view.score());
        node.set("pendingRows", gridJson(view.pendingRows().toArray(int[][]::new)));
        int[][] items = view.items();
        // 아이템 대전에서만 싣는다. 없으면 받는 쪽이 빈 표시로 만든다 (일반·시간제한 STATE 크기 유지)
        if (Arrays.stream(items).flatMapToInt(Arrays::stream).anyMatch(v -> v != 0)) node.set("items", gridJson(items));
        return node;
    }

    private static PlayerView readView(ObjectNode node) throws ProtocolException {
        int[][] cells = gridField(node, "cells");
        int[][] next = gridField(node, "next");
        long score = longField(node, "score");
        List<int[]> pending = List.of(gridField(node, "pendingRows"));
        int[][] items = node.has("items") ? gridField(node, "items")
                : new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        return construct(() -> new PlayerView(cells, next, score, pending, items));
    }
    //#endregion

    //#region RESULT {"winner"(무승부면 null),"reason"}
    public static Message resultMessage(String matchId, MatchResult result) {
        return new Message(MessageType.RESULT, matchId, 0, resultJson(result));
    }

    public static MatchResult readResult(Message message) throws ProtocolException {
        return readResultJson(payloadOf(message, MessageType.RESULT));
    }

    private static ObjectNode resultJson(MatchResult result) {
        ObjectNode node = WireFormat.MAPPER.createObjectNode();
        if (result.winner() == null) node.putNull("winner");
        else node.put("winner", result.winner().name());
        node.put("reason", result.reason().name());
        return node;
    }

    private static MatchResult readResultJson(JsonNode node) throws ProtocolException {
        if (!node.isObject()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "result");
        JsonNode winnerNode = node.get("winner");
        if (winnerNode == null) throw new ProtocolException(ErrorCode.INVALID_FIELD, "winner");
        PlayerId winner = winnerNode.isNull() ? null : enumField(node, "winner", PlayerId.class);
        MatchResult.EndReason reason = enumField(node, "reason", MatchResult.EndReason.class);
        return construct(() -> new MatchResult(winner, reason));
    }
    //#endregion

    //#region JSON 필드 읽기
    private static ObjectNode payloadOf(Message message, MessageType expected) {
        Objects.requireNonNull(message, "message");
        if (message.type() != expected) {
            throw new IllegalArgumentException("expected " + expected + " but was " + message.type());
        }
        return message.payload();
    }

    private static ArrayNode gridJson(int[][] grid) {
        ArrayNode rows = WireFormat.MAPPER.createArrayNode();
        for (int[] row : grid) {
            ArrayNode values = rows.addArray();
            for (int value : row) values.add(value);
        }
        return rows;
    }

    /** 정수 배열의 배열. 행 길이·값 범위는 계약 생성자가 검사한다 */
    private static int[][] gridField(JsonNode parent, String field) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        int[][] grid = new int[node.size()][];
        for (int r = 0; r < grid.length; r++) {
            JsonNode row = node.get(r);
            if (!row.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field + "[" + r + "]");
            grid[r] = new int[row.size()];
            for (int c = 0; c < grid[r].length; c++) {
                JsonNode value = row.get(c);
                if (!value.isInt()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field + "[" + r + "][" + c + "]");
                grid[r][c] = value.intValue();
            }
        }
        return grid;
    }

    private static ObjectNode objectField(JsonNode parent, String field) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isObject()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        return (ObjectNode) node;
    }

    private static long longField(JsonNode parent, String field) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        }
        return node.longValue();
    }

    private static boolean booleanField(JsonNode parent, String field) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isBoolean()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        return node.booleanValue();
    }

    private static <E extends Enum<E>> E enumField(JsonNode parent, String field, Class<E> type) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        try {
            return Enum.valueOf(type, node.textValue());
        } catch (IllegalArgumentException e) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, field + " " + node.textValue());
        }
    }

    @FunctionalInterface
    private interface Constructor<T> {
        T create();
    }

    /** 계약 생성자의 검증 실패를 프로토콜 위반으로 바꾼다 */
    private static <T> T construct(Constructor<T> constructor) throws ProtocolException {
        try {
            return constructor.create();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, String.valueOf(e.getMessage()));
        }
    }
    //#endregion
}
