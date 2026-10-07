package tetris.feature.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tetris.feature.score.ScoreEntry;
import tetris.feature.score.Scoreboard;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** scoreboard.json 입출력 (PER-1~5). 무효 항목은 버리고, 손상 파일은 *.corrupt로 옮긴다 */
public class JsonScoreboardRepository implements ScoreboardRepository {
    private static final int DEFAULT_LEVEL = 1;
    private static final int DEFAULT_LINES = 0;

    private final JsonFile file;

    public JsonScoreboardRepository() {
        this(DataDirectory.scoreboardFile());
    }

    public JsonScoreboardRepository(Path path) {
        this.file = new JsonFile(path);
    }

    @Override
    public LoadResult<Scoreboard> load() {
        JsonFile.ReadResult read = file.read();
        return switch (read.status()) {
            case MISSING -> emptyScoreboard();
            case UNREADABLE -> emptyScoreboard("스코어보드 파일을 읽을 수 없어 빈 스코어보드로 시작합니다.");
            case MALFORMED -> emptyScoreboardAfterMovingAsideCorruptFile();
            case OK -> loadEntries(read.root().path("entries"));
        };
    }

    @Override
    public boolean save(Scoreboard scoreboard) {
        ObjectNode root = JsonFile.newRoot();
        ArrayNode entries = root.putArray("entries");
        for (ScoreEntry entry : scoreboard.entries()) {
            entries.addObject()
                    .put("name", entry.name())
                    .put("score", entry.score())
                    .put("level", entry.level())
                    .put("lines", entry.lines())
                    .put("recordedAt", entry.recordedAt().toString());
        }
        return file.write(root);
    }

    private LoadResult<Scoreboard> loadEntries(JsonNode entriesNode) {
        if (!entriesNode.isArray()) return emptyScoreboardAfterMovingAsideCorruptFile();

        List<ScoreEntry> entries = new ArrayList<>();
        int discardedCount = 0;
        for (JsonNode node : entriesNode) {
            Optional<ScoreEntry> entry = toEntry(node);
            if (entry.isPresent()) entries.add(entry.get());
            else discardedCount++;
        }

        List<String> warnings = discardedCount == 0
                ? List.of()
                : List.of("스코어보드에서 올바르지 않은 기록 " + discardedCount + "개를 제외했습니다.");
        return new LoadResult<>(new Scoreboard(entries), warnings);
    }

    private Optional<ScoreEntry> toEntry(JsonNode node) {
        if (!node.isObject()) return Optional.empty();

        try {
            return Optional.of(new ScoreEntry(
                    textOrNull(node, "name"),
                    requiredInteger(node, "score"),
                    integerOrDefault(node, "level", DEFAULT_LEVEL),
                    integerOrDefault(node, "lines", DEFAULT_LINES),
                    recordedAtOrEpoch(node)));
        } catch (IllegalArgumentException invalidEntry) {
            return Optional.empty();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static long requiredInteger(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException(field + " should be an integer");
        }
        return value.asLong();
    }

    private static int integerOrDefault(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return defaultValue;

        long integer = requiredInteger(node, field);
        if (integer > Integer.MAX_VALUE || integer < Integer.MIN_VALUE) {
            throw new IllegalArgumentException(field + " is out of range");
        }
        return (int) integer;
    }

    /** 표시·디버깅용 필드라 없거나 잘못돼도 기록은 살린다 */
    private static Instant recordedAtOrEpoch(JsonNode node) {
        String text = textOrNull(node, "recordedAt");
        if (text == null) return Instant.EPOCH;

        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    private static LoadResult<Scoreboard> emptyScoreboard(String... warnings) {
        return new LoadResult<>(new Scoreboard(List.of()), List.of(warnings));
    }

    private LoadResult<Scoreboard> emptyScoreboardAfterMovingAsideCorruptFile() {
        file.moveAsideAsCorrupt();
        return emptyScoreboard("스코어보드 파일이 손상되어 빈 스코어보드로 시작합니다.");
    }
}
