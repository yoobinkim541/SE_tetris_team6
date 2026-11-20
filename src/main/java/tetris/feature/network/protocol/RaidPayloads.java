package tetris.feature.network.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.CardId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

/**
 * 레이드 메시지(RAID_INPUT·RAID_STATE·RAID_RESULT) payload.
 * 보드·보스·효과·사건만 보내고, 빈 고스트·럭키·미리보기는 생략해 64KiB 안에 둔다.
 */
public final class RaidPayloads {
    private RaidPayloads() {
    }

    public static Message commandMessage(String matchId, PlayerCommand command) {
        ObjectNode payload = WireFormat.MAPPER.createObjectNode()
                .put("kind", "command")
                .put("player", command.playerId().name())
                .put("action", command.action().name())
                .put("pressed", command.pressed())
                .put("sequence", command.sequence());
        return new Message(MessageType.RAID_INPUT, matchId, 0, payload);
    }

    public static Message ultimateMessage(String matchId, PlayerId player, long sequence) {
        ObjectNode payload = WireFormat.MAPPER.createObjectNode()
                .put("kind", "ultimate")
                .put("player", player.name())
                .put("sequence", sequence);
        return new Message(MessageType.RAID_INPUT, matchId, 0, payload);
    }

    public static Message pickMessage(String matchId, PlayerId player, int index, long sequence) {
        ObjectNode payload = WireFormat.MAPPER.createObjectNode()
                .put("kind", "pick")
                .put("player", player.name())
                .put("index", index)
                .put("sequence", sequence);
        return new Message(MessageType.RAID_INPUT, matchId, 0, payload);
    }

    public static RaidInput readInput(Message message) throws ProtocolException {
        ObjectNode payload = payloadOf(message, MessageType.RAID_INPUT);
        String kind = text(payload, "kind");
        PlayerId player = enumField(payload, "player", PlayerId.class);
        long sequence = longField(payload, "sequence");
        return switch (kind) {
            case "command" -> new RaidInput(Kind.COMMAND, player,
                    new PlayerCommand(player, enumField(payload, "action", GameAction.class),
                            booleanField(payload, "pressed"), sequence),
                    -1, sequence);
            case "ultimate" -> new RaidInput(Kind.ULTIMATE, player, null, -1, sequence);
            case "pick" -> new RaidInput(Kind.PICK, player, null, intField(payload, "index"), sequence);
            default -> throw new ProtocolException(ErrorCode.INVALID_FIELD, "kind");
        };
    }

    public static Message stateMessage(RaidSnapshot snapshot) {
        ObjectNode payload = WireFormat.MAPPER.createObjectNode()
                .put("revision", snapshot.revision())
                .put("mode", snapshot.mode().name())
                .put("phase", snapshot.phase().name())
                .put("kills", snapshot.kills())
                .put("elapsedMillis", snapshot.elapsedMillis())
                .put("gauge", snapshot.gauge());
        payload.set("boss", bossJson(snapshot.boss()));
        ArrayNode players = payload.putArray("players");
        for (RaidSnapshot.PlayerView player : snapshot.players()) players.add(playerJson(player));
        if (snapshot.offer() == null) payload.putNull("offer");
        else payload.set("offer", offerJson(snapshot.offer()));
        payload.set("events", eventsJson(snapshot.events()));
        if (snapshot.result() == null) payload.putNull("result");
        else payload.set("result", resultJson(snapshot.result()));
        if (!snapshot.raceBosses().isEmpty()) {
            ArrayNode bosses = payload.putArray("raceBosses");
            for (RaidSnapshot.BossView boss : snapshot.raceBosses()) bosses.add(bossJson(boss));
            payload.set("raceKills", intsJson(snapshot.raceKills()));
            payload.set("raceGauges", intsJson(snapshot.raceGauges()));
        }
        return new Message(MessageType.RAID_STATE, snapshot.raidId(), 0, payload);
    }

    public static RaidSnapshot readState(Message message) throws ProtocolException {
        ObjectNode payload = payloadOf(message, MessageType.RAID_STATE);
        return readSnapshot(message.matchId(), payload);
    }

    public static Message resultMessage(String matchId, RaidSnapshot.Result result) {
        return new Message(MessageType.RAID_RESULT, matchId, 0, resultJson(result));
    }

    public static RaidSnapshot.Result readResult(Message message) throws ProtocolException {
        return readResultJson(payloadOf(message, MessageType.RAID_RESULT));
    }

    public enum Kind { COMMAND, ULTIMATE, PICK }

    public record RaidInput(Kind kind, PlayerId player, PlayerCommand command, int index, long sequence) {
        public RaidInput {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(player, "player");
        }
    }

    private static RaidSnapshot readSnapshot(String raidId, ObjectNode payload) throws ProtocolException {
        long revision = longField(payload, "revision");
        RaidMode mode = enumField(payload, "mode", RaidMode.class);
        RaidPhase phase = enumField(payload, "phase", RaidPhase.class);
        int kills = intField(payload, "kills");
        long elapsed = longField(payload, "elapsedMillis");
        int gauge = intField(payload, "gauge");
        RaidSnapshot.BossView boss = readBoss(objectField(payload, "boss"));
        JsonNode playerNode = payload.get("players");
        if (playerNode == null || !playerNode.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "players");
        List<RaidSnapshot.PlayerView> players = new ArrayList<>();
        for (JsonNode node : playerNode) {
            if (!node.isObject()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "players");
            players.add(readPlayer((ObjectNode) node));
        }
        JsonNode offerNode = payload.get("offer");
        if (offerNode == null) throw new ProtocolException(ErrorCode.INVALID_FIELD, "offer");
        RaidSnapshot.CardOffer offer = offerNode.isNull() ? null : readOffer(offerNode);
        List<RaidEvent> events = readEvents(payload.get("events"));
        JsonNode resultNode = payload.get("result");
        if (resultNode == null) throw new ProtocolException(ErrorCode.INVALID_FIELD, "result");
        RaidSnapshot.Result result = resultNode.isNull() ? null : readResultJson(resultNode);
        List<RaidSnapshot.BossView> raceBosses = new ArrayList<>();
        if (payload.has("raceBosses")) {
            JsonNode bosses = payload.get("raceBosses");
            if (!bosses.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "raceBosses");
            for (JsonNode node : bosses) {
                if (!node.isObject()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "raceBosses");
                raceBosses.add(readBoss((ObjectNode) node));
            }
        }
        int[] raceKills = payload.has("raceKills") ? intsField(payload, "raceKills") : new int[0];
        int[] raceGauges = payload.has("raceGauges") ? intsField(payload, "raceGauges") : new int[0];
        String id = raidId == null ? "raid" : raidId;
        return construct(() -> new RaidSnapshot(id, revision, mode, phase, boss, players, kills, elapsed, gauge,
                offer, events, result, raceBosses, raceKills, raceGauges));
    }

    private static ObjectNode bossJson(RaidSnapshot.BossView boss) {
        ObjectNode node = WireFormat.MAPPER.createObjectNode()
                .put("id", boss.id().name())
                .put("level", boss.level())
                .put("hp", boss.hp())
                .put("maxHp", boss.maxHp())
                .put("target", boss.target())
                .put("piecesLeft", boss.piecesLeft())
                .put("fleeMillis", boss.fleeMillis());
        if (boss.nextPattern() == null) node.putNull("nextPattern");
        else node.put("nextPattern", boss.nextPattern().name());
        node.set("splitHp", intsJson(boss.splitHp()));
        return node;
    }

    private static RaidSnapshot.BossView readBoss(ObjectNode node) throws ProtocolException {
        BossId id = enumField(node, "id", BossId.class);
        int level = intField(node, "level");
        int hp = intField(node, "hp");
        int maxHp = intField(node, "maxHp");
        JsonNode patternNode = node.get("nextPattern");
        if (patternNode == null) throw new ProtocolException(ErrorCode.INVALID_FIELD, "nextPattern");
        PatternId pattern = patternNode.isNull() ? null : enumField(node, "nextPattern", PatternId.class);
        int target = intField(node, "target");
        int piecesLeft = intField(node, "piecesLeft");
        int[] splitHp = node.has("splitHp") ? intsField(node, "splitHp") : new int[0];
        long flee = longField(node, "fleeMillis");
        return construct(() -> new RaidSnapshot.BossView(id, level, hp, maxHp, pattern, target, piecesLeft, splitHp, flee));
    }

    private static ObjectNode playerJson(RaidSnapshot.PlayerView player) {
        MatchSnapshot.PlayerView board = player.board();
        ObjectNode node = WireFormat.MAPPER.createObjectNode();
        node.set("cells", gridJson(board.cells()));
        node.set("next", gridJson(board.next()));
        node.put("score", board.score());
        node.set("pendingRows", gridJson(board.pendingRows().toArray(int[][]::new)));
        if (board.hold().length > 0) node.set("hold", gridJson(board.hold()));
        if (Arrays.stream(board.ghost()).flatMapToInt(Arrays::stream).anyMatch(value -> value != 0)) {
            node.set("ghost", gridJson(board.ghost()));
        }
        if (!board.previews().isEmpty()) node.set("previews", shapesJson(board.previews()));
        node.put("alive", player.alive());
        if (Arrays.stream(player.lucky()).flatMapToInt(Arrays::stream).anyMatch(value -> value != 0)) {
            node.set("lucky", gridJson(player.lucky()));
        }
        if (!player.effects().isEmpty()) {
            ArrayNode effects = node.putArray("effects");
            for (RaidSnapshot.Effect effect : player.effects()) {
                ObjectNode item = effects.addObject()
                        .put("pattern", effect.pattern().name())
                        .put("remaining", effect.remainingPieces());
                item.set("area", intsJson(effect.area()));
            }
        }
        node.put("reviveProgress", player.reviveProgress());
        node.put("reviveRequired", player.reviveRequired());
        node.put("damageDealt", player.damageDealt());
        if (!player.extendedPreviews().isEmpty()) node.set("extendedPreviews", shapesJson(player.extendedPreviews()));
        return node;
    }

    private static RaidSnapshot.PlayerView readPlayer(ObjectNode node) throws ProtocolException {
        int[][] cells = gridField(node, "cells");
        int[][] next = gridField(node, "next");
        long score = longField(node, "score");
        List<int[]> pending = List.of(gridField(node, "pendingRows"));
        int[][] items = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        int[][] hold = node.has("hold") ? gridField(node, "hold") : new int[0][];
        int[][] ghost = node.has("ghost") ? gridField(node, "ghost")
                : new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        List<int[][]> previews = node.has("previews") ? shapesField(node, "previews") : List.of();
        MatchSnapshot.PlayerView board = construct(() -> new MatchSnapshot.PlayerView(
                cells, next, score, pending, items, hold, ghost, previews));
        boolean alive = booleanField(node, "alive");
        int[][] lucky = node.has("lucky") ? gridField(node, "lucky")
                : new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        List<RaidSnapshot.Effect> effects = new ArrayList<>();
        if (node.has("effects")) {
            JsonNode list = node.get("effects");
            if (!list.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "effects");
            for (JsonNode item : list) {
                if (!item.isObject()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "effects");
                ObjectNode effect = (ObjectNode) item;
                effects.add(construct(() -> new RaidSnapshot.Effect(
                        enumField(effect, "pattern", PatternId.class),
                        intField(effect, "remaining"),
                        effect.has("area") ? intsField(effect, "area") : new int[0])));
            }
        }
        int progress = intField(node, "reviveProgress");
        int required = intField(node, "reviveRequired");
        long damage = longField(node, "damageDealt");
        List<int[][]> extra = node.has("extendedPreviews") ? shapesField(node, "extendedPreviews") : List.of();
        return construct(() -> new RaidSnapshot.PlayerView(board, alive, lucky, effects, progress, required, damage, extra));
    }

    private static ObjectNode offerJson(RaidSnapshot.CardOffer offer) {
        ObjectNode node = WireFormat.MAPPER.createObjectNode()
                .put("chooser", offer.chooser())
                .put("remainingMillis", offer.remainingMillis());
        ArrayNode cards = node.putArray("cards");
        for (CardId card : offer.cards()) cards.add(card.name());
        return node;
    }

    private static RaidSnapshot.CardOffer readOffer(JsonNode node) throws ProtocolException {
        if (!node.isObject()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "offer");
        ObjectNode object = (ObjectNode) node;
        JsonNode cardsNode = object.get("cards");
        if (cardsNode == null || !cardsNode.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "cards");
        List<CardId> cards = new ArrayList<>();
        for (JsonNode card : cardsNode) {
            if (!card.isTextual()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "cards");
            try {
                cards.add(CardId.valueOf(card.textValue()));
            } catch (IllegalArgumentException e) {
                throw new ProtocolException(ErrorCode.INVALID_FIELD, "cards");
            }
        }
        int chooser = intField(object, "chooser");
        long remaining = longField(object, "remainingMillis");
        return construct(() -> new RaidSnapshot.CardOffer(cards, chooser, remaining));
    }

    private static ArrayNode eventsJson(List<RaidEvent> events) {
        ArrayNode node = WireFormat.MAPPER.createArrayNode();
        for (RaidEvent event : events) {
            node.addObject().put("type", event.type().name()).put("player", event.player()).put("value", event.value());
        }
        return node;
    }

    private static List<RaidEvent> readEvents(JsonNode node) throws ProtocolException {
        if (node == null || !node.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "events");
        List<RaidEvent> events = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "events");
            ObjectNode event = (ObjectNode) item;
            events.add(construct(() -> new RaidEvent(enumField(event, "type", RaidEvent.Type.class),
                    intField(event, "player"), intField(event, "value"))));
        }
        return events;
    }

    private static ObjectNode resultJson(RaidSnapshot.Result result) {
        ObjectNode node = WireFormat.MAPPER.createObjectNode()
                .put("kills", result.kills())
                .put("totalDamage", result.totalDamage())
                .put("survivedMillis", result.survivedMillis());
        if (result.winner() == null) node.putNull("winner");
        else node.put("winner", result.winner());
        return node;
    }

    private static RaidSnapshot.Result readResultJson(JsonNode node) throws ProtocolException {
        if (!node.isObject()) throw new ProtocolException(ErrorCode.INVALID_FIELD, "result");
        ObjectNode object = (ObjectNode) node;
        int kills = intField(object, "kills");
        long damage = longField(object, "totalDamage");
        long survived = longField(object, "survivedMillis");
        JsonNode winnerNode = object.get("winner");
        if (winnerNode == null) throw new ProtocolException(ErrorCode.INVALID_FIELD, "winner");
        Integer winner = winnerNode.isNull() ? null : winnerNode.intValue();
        return construct(() -> new RaidSnapshot.Result(kills, damage, survived, winner));
    }

    private static ArrayNode intsJson(int[] values) {
        ArrayNode node = WireFormat.MAPPER.createArrayNode();
        for (int value : values) node.add(value);
        return node;
    }

    private static int[] intsField(JsonNode parent, String field) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        int[] values = new int[node.size()];
        for (int i = 0; i < values.length; i++) {
            if (!node.get(i).isIntegralNumber()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
            values[i] = node.get(i).intValue();
        }
        return values;
    }

    private static ArrayNode gridJson(int[][] grid) {
        ArrayNode rows = WireFormat.MAPPER.createArrayNode();
        for (int[] row : grid) {
            ArrayNode values = rows.addArray();
            for (int value : row) values.add(value);
        }
        return rows;
    }

    private static int[][] gridField(JsonNode parent, String field) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        int[][] grid = new int[node.size()][];
        for (int r = 0; r < grid.length; r++) {
            JsonNode row = node.get(r);
            if (!row.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
            grid[r] = new int[row.size()];
            for (int c = 0; c < grid[r].length; c++) {
                if (!row.get(c).isInt()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
                grid[r][c] = row.get(c).intValue();
            }
        }
        return grid;
    }

    private static ArrayNode shapesJson(List<int[][]> shapes) {
        ArrayNode node = WireFormat.MAPPER.createArrayNode();
        for (int[][] shape : shapes) node.add(gridJson(shape));
        return node;
    }

    private static List<int[][]> shapesField(JsonNode parent, String field) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        List<int[][]> shapes = new ArrayList<>();
        for (JsonNode shape : node) {
            if (!shape.isArray()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
            ObjectNode wrapper = WireFormat.MAPPER.createObjectNode();
            wrapper.set("shape", shape);
            shapes.add(gridField(wrapper, "shape"));
        }
        return shapes;
    }

    private static ObjectNode payloadOf(Message message, MessageType expected) {
        Objects.requireNonNull(message, "message");
        if (message.type() != expected) throw new IllegalArgumentException("expected " + expected + " but was " + message.type());
        return message.payload();
    }

    private static ObjectNode objectField(JsonNode parent, String field) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isObject()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        return (ObjectNode) node;
    }

    private static String text(JsonNode parent, String field) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual()) throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        return node.textValue();
    }

    private static int intField(JsonNode parent, String field) throws ProtocolException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        }
        return node.intValue();
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
            throw new ProtocolException(ErrorCode.INVALID_FIELD, field);
        }
    }

    @FunctionalInterface
    private interface Constructor<T> {
        T create() throws ProtocolException;
    }

    private static <T> T construct(Constructor<T> constructor) throws ProtocolException {
        try {
            return constructor.create();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ProtocolException(ErrorCode.INVALID_FIELD, String.valueOf(e.getMessage()));
        }
    }
}
