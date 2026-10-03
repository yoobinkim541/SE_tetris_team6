package tetris.feature.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import tetris.feature.score.ScoreEntry;
import tetris.feature.score.Scoreboard;

/** scoreboard.json 입출력 (PER-1~5). 무효 항목은 버리고, 손상 파일은 *.corrupt로 옮긴다. */
public class ScoreboardRepository {
    static final String WARN_NO_DATA_DIR = "데이터 폴더를 만들 수 없어 이번 실행의 기록은 저장되지 않습니다.";
    static final String WARN_CORRUPT = "스코어보드 파일이 손상되어 빈 기록으로 시작합니다.";
    static final String WARN_INVALID_ENTRIES = "스코어보드의 잘못된 기록 %d개를 제외했습니다.";

    private static final int DEFAULT_LEVEL = 1;
    private static final int DEFAULT_LINES = 0;
    // recordedAt은 표시·디버깅용이라 없는 기록도 살린다(PER-5). ScoreEntry가 null을 받지 않으므로 고정값을 쓴다
    private static final Instant DEFAULT_RECORDED_AT = Instant.EPOCH;

    private final Supplier<Path> fileLocator;

    public ScoreboardRepository() {
        this(DataDirectory::scoreboardFile);
    }

    /** 파일 위치를 주입한다(테스트용 임시 폴더 등). 위치를 구하다 UncheckedIOException이 나면 데이터 폴더가 없는 것으로 본다 */
    public ScoreboardRepository(Supplier<Path> fileLocator) {
        this.fileLocator = fileLocator;
    }

    /** 명세 12.2: 없음 -> 빈 보드, 손상 -> 빈 보드 + *.corrupt 보관 + 경고, 무효 항목 -> 그 항목만 버림 + 경고 */
    public LoadResult<Scoreboard> load() {
        Path file;
        try {
            file = fileLocator.get();
        } catch (UncheckedIOException e) {
            return new LoadResult<>(empty(), List.of(WARN_NO_DATA_DIR));
        }
        if (!Files.exists(file)) return new LoadResult<>(empty(), List.of());

        JsonNode root;
        try {
            root = JsonFiles.read(file);
        } catch (IOException e) {
            root = null;
        }
        JsonNode entriesNode = root == null ? null : root.get("entries");
        if (root == null || !root.isObject() || (entriesNode != null && !entriesNode.isArray())) {
            JsonFiles.moveToCorrupt(file);
            return new LoadResult<>(empty(), List.of(WARN_CORRUPT));
        }
        if (entriesNode == null) return new LoadResult<>(empty(), List.of());

        List<ScoreEntry> valid = new ArrayList<>();
        int invalid = 0;
        for (JsonNode node : entriesNode) {
            ScoreEntry entry = toEntry(node);
            if (entry == null) invalid++;
            else valid.add(entry);
        }
        List<String> warnings = invalid == 0 ? List.of() : List.of(String.format(WARN_INVALID_ENTRIES, invalid));
        return new LoadResult<>(new Scoreboard(valid), warnings);
    }

    /** 무효면 null. name·score는 필수, level·lines·recordedAt은 없으면 기본값이지만 있는데 잘못되면 무효 */
    private static ScoreEntry toEntry(JsonNode node) {
        if (node == null || !node.isObject()) return null;

        JsonNode name = node.get("name");
        JsonNode score = node.get("score");
        if (name == null || !name.isTextual() || !ScoreEntry.isValidName(name.textValue())) return null;
        if (score == null || !score.canConvertToLong() || !score.isIntegralNumber() || score.longValue() < 0) return null;

        Integer level = intOrDefault(node.get("level"), DEFAULT_LEVEL, 1);
        Integer lines = intOrDefault(node.get("lines"), DEFAULT_LINES, 0);
        if (level == null || lines == null) return null;

        Instant recordedAt = DEFAULT_RECORDED_AT;
        JsonNode at = node.get("recordedAt");
        if (at != null) {
            if (!at.isTextual()) return null;
            try {
                recordedAt = Instant.parse(at.textValue());
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return new ScoreEntry(name.textValue(), score.longValue(), level, lines, recordedAt);
    }

    // 없으면 기본값, 정수가 아니거나 min보다 작으면 null(무효)
    private static Integer intOrDefault(JsonNode node, int defaultValue, int min) {
        if (node == null) return defaultValue;
        if (!node.isInt() || node.intValue() < min) return null;
        return node.intValue();
    }

    private static Scoreboard empty() {
        return new Scoreboard(List.of());
    }

    /** 원자적 저장. 실패하면 false. 파일 안 배열 순서가 순위다 (SBD-4) */
    public boolean save(Scoreboard scoreboard) {
        ObjectNode root = JsonFiles.newObject();
        ArrayNode entries = root.putArray("entries");
        for (ScoreEntry entry : scoreboard.entries()) {
            ObjectNode node = entries.addObject();
            node.put("name", entry.name());
            node.put("score", entry.score());
            node.put("level", entry.level());
            node.put("lines", entry.lines());
            node.put("recordedAt", entry.recordedAt().toString());
        }
        try {
            JsonFiles.writeAtomically(fileLocator.get(), root);
            return true;
        } catch (IOException | UncheckedIOException e) {
            return false;
        }
    }
}
