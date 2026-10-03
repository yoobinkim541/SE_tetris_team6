package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tetris.feature.save.LoadResult;
import tetris.feature.save.ScoreboardRepository;
import tetris.feature.save.SettingsRepository;
import tetris.feature.score.ScoreEntry;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;

/** PER-1~5, 명세 12.2 오류 처리표의 모든 행 (임시 디렉터리로 재현, 원자적 교체) */
public class PersistenceTest {
    private static Path tempDir() throws IOException {
        return Files.createTempDirectory("tetris-persistence");
    }

    private static void write(Path file, String json) throws IOException {
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    private static List<String> fileNames(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    //#region settings.json
    @Test
    void 설정_파일이_없으면_기본값으로_실행하고_새_파일을_만든다() throws IOException {
        Path file = tempDir().resolve("settings.json");
        LoadResult<Settings> result = new SettingsRepository(() -> file).load();

        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());
        assertTrue(result.warnings().isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    void 설정_파일_구문이_깨지면_기본값과_경고() throws IOException {
        Path file = tempDir().resolve("settings.json");
        write(file, "{ \"screenSize\": \"LARGE\", ");
        LoadResult<Settings> result = new SettingsRepository(() -> file).load();

        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void 값_일부가_무효면_그_필드만_기본값() throws IOException {
        Path file = tempDir().resolve("settings.json");
        write(file, """
                { "schemaVersion": 1, "screenSize": "HUGE", "colorBlindMode": true,
                  "keys": { "LEFT": 65, "RIGHT": 68, "SOFT_DROP": 83, "ROTATE": 87, "HARD_DROP": 32, "PAUSE": 80, "QUIT": 81 } }
                """);
        LoadResult<Settings> result = new SettingsRepository(() -> file).load();

        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());          // 무효 -> 기본값
        assertTrue(result.value().isColorBlindMode());                           // 나머지는 유지
        assertEquals(KeyEvent.VK_A, result.value().getKeyMap().keyOf(GameAction.MOVE_LEFT));
        assertEquals(1, result.warnings().size());
    }

    @Test
    void 조작키가_중복_누락_예약키면_조작키_전체를_기본값으로() throws IOException {
        String[] badKeys = {
            "{ \"LEFT\": 65, \"RIGHT\": 65, \"SOFT_DROP\": 83, \"ROTATE\": 87, \"HARD_DROP\": 32, \"PAUSE\": 80, \"QUIT\": 81 }",
            "{ \"LEFT\": 65, \"RIGHT\": 68, \"SOFT_DROP\": 83, \"ROTATE\": 87, \"HARD_DROP\": 32, \"PAUSE\": 80 }",
            "{ \"LEFT\": 65, \"RIGHT\": 68, \"SOFT_DROP\": 83, \"ROTATE\": 87, \"HARD_DROP\": 32, \"PAUSE\": 80, \"QUIT\": 10 }"
        };
        for (String keys : badKeys) {
            Path file = tempDir().resolve("settings.json");
            write(file, "{ \"screenSize\": \"LARGE\", \"keys\": " + keys + " }");
            LoadResult<Settings> result = new SettingsRepository(() -> file).load();

            assertEquals(new KeyMap().codes(), result.value().getKeyMap().codes());
            assertEquals(ScreenSize.LARGE, result.value().getScreenSize());
            assertEquals(1, result.warnings().size());
        }
    }

    @Test
    void 모르는_필드는_무시하고_없는_필드는_기본값() throws IOException { // PER-5
        Path file = tempDir().resolve("settings.json");
        write(file, "{ \"schemaVersion\": 99, \"futureOption\": [1, 2], \"colorBlindMode\": true }");
        LoadResult<Settings> result = new SettingsRepository(() -> file).load();

        assertTrue(result.value().isColorBlindMode());
        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void 설정을_저장했다가_불러오면_같다() throws IOException {
        Path file = tempDir().resolve("settings.json");
        Settings settings = new Settings();
        settings.setScreenSize(ScreenSize.SMALL);
        settings.setColorBlindMode(true);
        settings.getKeyMap().assign(GameAction.ROTATE, KeyEvent.VK_X);

        assertTrue(new SettingsRepository(() -> file).save(settings));
        LoadResult<Settings> loaded = new SettingsRepository(() -> file).load();

        assertEquals(ScreenSize.SMALL, loaded.value().getScreenSize());
        assertTrue(loaded.value().isColorBlindMode());
        assertEquals(settings.getKeyMap().codes(), loaded.value().getKeyMap().codes());
        assertTrue(loaded.warnings().isEmpty());
    }

    @Test
    void 저장은_기존_파일을_교체하고_임시_파일을_남기지_않는다() throws IOException { // PER-4
        Path dir = tempDir();
        Path file = dir.resolve("settings.json");
        SettingsRepository repository = new SettingsRepository(() -> file);
        repository.save(new Settings());
        Settings changed = new Settings();
        changed.setScreenSize(ScreenSize.LARGE);
        repository.save(changed);

        assertEquals(List.of("settings.json"), fileNames(dir));
        assertEquals(ScreenSize.LARGE, repository.load().value().getScreenSize());
    }

    @Test
    void 저장에_실패하면_false() throws IOException { // SET-4
        Path dir = tempDir();
        Path file = dir.resolve("settings.json");
        Files.createDirectories(file.resolve("blocker")); // 같은 이름의 비어 있지 않은 폴더라 교체 불가
        assertFalse(new SettingsRepository(() -> file).save(new Settings()));
    }

    @Test
    void 데이터_폴더를_만들_수_없으면_기본값과_경고_저장은_false() { // PER-2
        SettingsRepository repository = new SettingsRepository(() -> {
            throw new UncheckedIOException(new IOException("no permission"));
        });
        LoadResult<Settings> result = repository.load();

        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());
        assertEquals(1, result.warnings().size());
        assertFalse(repository.save(new Settings()));
    }
    //#endregion

    //#region scoreboard.json
    @Test
    void 스코어보드_파일이_없으면_빈_보드_경고_없음() throws IOException {
        Path file = tempDir().resolve("scoreboard.json");
        LoadResult<Scoreboard> result = new ScoreboardRepository(() -> file).load();

        assertTrue(result.value().entries().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void 스코어보드_구문이_깨지면_빈_보드와_경고_손상_파일은_corrupt로_보관() throws IOException {
        Path dir = tempDir();
        Path file = dir.resolve("scoreboard.json");
        String broken = "{ \"entries\": [ { \"name\": \"홍길동\", ";
        write(file, broken);
        LoadResult<Scoreboard> result = new ScoreboardRepository(() -> file).load();

        assertTrue(result.value().entries().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals(broken, Files.readString(dir.resolve("scoreboard.json.corrupt"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(file));
    }

    @Test
    void 무효_항목만_버리고_경고() throws IOException {
        Path file = tempDir().resolve("scoreboard.json");
        write(file, """
                { "schemaVersion": 1, "entries": [
                  { "name": "홍길동", "score": 300 },
                  { "name": "A!", "score": 200 },
                  { "name": "   ", "score": 200 },
                  { "score": 200 },
                  { "name": "B", "score": -5 },
                  { "name": "C", "score": "abc" },
                  { "name": "D", "score": 100, "level": 0 },
                  { "name": "E", "score": 100, "recordedAt": "어제" }
                ] }
                """);
        LoadResult<Scoreboard> result = new ScoreboardRepository(() -> file).load();

        assertEquals(1, result.value().entries().size());
        assertEquals("홍길동", result.value().entries().get(0).name());
        assertEquals(List.of(String.format("스코어보드의 잘못된 기록 %d개를 제외했습니다.", 7)), result.warnings());
    }

    @Test
    void 정렬_안_됐거나_10개를_넘으면_정렬하고_자르고_경고_없음() throws IOException {
        StringBuilder json = new StringBuilder("{ \"entries\": [");
        for (int i = 0; i < 12; i++) json.append(i == 0 ? "" : ",").append("{ \"name\": \"P").append(i).append("\", \"score\": ").append(i * 10).append(" }");
        Path file = tempDir().resolve("scoreboard.json");
        write(file, json.append("] }").toString());

        LoadResult<Scoreboard> result = new ScoreboardRepository(() -> file).load();
        assertEquals(Scoreboard.MAX_ENTRIES, result.value().entries().size());
        assertEquals("P11", result.value().entries().get(0).name());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void 스코어보드를_저장했다가_불러오면_순서와_값이_같다() throws IOException {
        Path file = tempDir().resolve("scoreboard.json");
        Instant at = Instant.parse("2026-09-29T12:34:56Z");
        Scoreboard board = new Scoreboard(List.of());
        board.add(new ScoreEntry("홍길동", 12345, 7, 31, at));
        board.add(new ScoreEntry("A B", 12345, 7, 30, at));
        board.add(new ScoreEntry("Kim", 900, 2, 4, at.plusSeconds(60)));

        assertTrue(new ScoreboardRepository(() -> file).save(board));
        LoadResult<Scoreboard> loaded = new ScoreboardRepository(() -> file).load();

        assertEquals(board.entries(), loaded.value().entries());
        assertTrue(loaded.warnings().isEmpty());
    }

    @Test
    void level_lines_recordedAt이_없으면_기본값() throws IOException {
        Path file = tempDir().resolve("scoreboard.json");
        write(file, "{ \"entries\": [ { \"name\": \"Lee\", \"score\": 10 } ] }");
        ScoreEntry entry = new ScoreboardRepository(() -> file).load().value().entries().get(0);

        assertEquals(1, entry.level());
        assertEquals(0, entry.lines());
        assertEquals(Instant.EPOCH, entry.recordedAt());
    }
    //#endregion
}
