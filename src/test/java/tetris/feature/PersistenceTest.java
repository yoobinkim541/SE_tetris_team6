package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.save.DataDirectory;
import tetris.feature.save.LoadResult;
import tetris.feature.save.JsonScoreboardRepository;
import tetris.feature.save.JsonSettingsRepository;
import tetris.feature.save.ScoreboardRepository;
import tetris.feature.save.SettingsRepository;
import tetris.feature.score.ScoreEntry;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;

/** PER-1~5, 명세 12.2 오류 처리표의 모든 행 (임시 디렉터리로 재현, 원자적 교체) */
public class PersistenceTest {
    @TempDir
    Path dir;

    private Path write(String fileName, String content) throws IOException {
        Path file = dir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static ScoreEntry entry(String name, long score) {
        return new ScoreEntry(name, score, 3, 12, Instant.parse("2026-09-29T12:34:56Z"));
    }

    private static List<String> names(Scoreboard board) {
        return board.entries().stream().map(ScoreEntry::name).toList();
    }

    //#region DataDirectory (PER-2)
    @Test
    void APPDATA가_있으면_그_아래_SETetrisTeam6_경로를_돌려준다() {
        assertEquals(dir.resolve("SETetrisTeam6"), DataDirectory.resolve(dir.toString(), "ignored"));
    }

    @Test
    void APPDATA가_없으면_user_home의_AppData_Roaming으로_대체한다() {
        Path expected = dir.resolve("AppData").resolve("Roaming").resolve("SETetrisTeam6");

        assertEquals(expected, DataDirectory.resolve(null, dir.toString()));
    }

    @Test
    void APPDATA가_비어_있어도_대체한다() {
        Path expected = dir.resolve("AppData").resolve("Roaming").resolve("SETetrisTeam6");

        assertEquals(expected, DataDirectory.resolve("  ", dir.toString()));
    }

    @Test
    void 경로를_구할_뿐_디렉터리를_만들지_않는다() {
        Path resolved = DataDirectory.resolve(dir.toString(), "ignored");

        assertFalse(Files.exists(resolved));
    }

    @Test
    void 디렉터리가_없어도_저장하면_만들어서_저장한다() {
        Path file = DataDirectory.resolve(dir.toString(), "ignored").resolve("settings.json");

        assertTrue(new JsonSettingsRepository(file).save(new Settings()));

        assertTrue(Files.exists(file));
    }

    @Test
    void 디렉터리를_만들_수_없어도_예외_없이_경로를_돌려주고_저장은_실패한다() throws IOException {
        Path blocker = write("blocker", "파일이라 하위에 디렉터리를 만들 수 없다");

        Path resolved = DataDirectory.resolve(blocker.toString(), "ignored");

        assertFalse(new JsonSettingsRepository(resolved.resolve("settings.json")).save(new Settings()));
        assertFalse(new JsonScoreboardRepository(resolved.resolve("scoreboard.json")).save(new Scoreboard(List.of())));
    }

    @Test
    void 기본_파일_이름은_settings_json과_scoreboard_json이다() {
        assertEquals("settings.json", DataDirectory.settingsFile().getFileName().toString());
        assertEquals("scoreboard.json", DataDirectory.scoreboardFile().getFileName().toString());
    }
    //#endregion

    //#region settings.json 오류 처리표
    @Test
    void settings_파일이_없으면_기본_설정이고_경고도_없다() {
        LoadResult<Settings> result = new JsonSettingsRepository(dir.resolve("settings.json")).load();

        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());
        assertFalse(result.value().isColorBlindMode());
        assertEquals(KeyEvent.VK_LEFT, result.value().getKeyMap().keyOf(GameAction.MOVE_LEFT));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void settings를_저장하고_다시_읽으면_같다() {
        Path file = dir.resolve("settings.json");
        Settings settings = new Settings();
        settings.setScreenSize(ScreenSize.LARGE);
        settings.setColorBlindMode(true);
        settings.getKeyMap().assign(GameAction.PAUSE, KeyEvent.VK_O);

        assertTrue(new JsonSettingsRepository(file).save(settings));
        LoadResult<Settings> result = new JsonSettingsRepository(file).load();

        assertEquals(ScreenSize.LARGE, result.value().getScreenSize());
        assertTrue(result.value().isColorBlindMode());
        assertEquals(KeyEvent.VK_O, result.value().getKeyMap().keyOf(GameAction.PAUSE));
        assertEquals(settings.getKeyMap().codes(), result.value().getKeyMap().codes());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void settings_저장_파일에_schemaVersion이_들어간다() throws IOException {
        Path file = dir.resolve("settings.json");

        new JsonSettingsRepository(file).save(new Settings());

        assertTrue(Files.readString(file).contains("\"schemaVersion\" : 1"));
    }

    @Test
    void settings_구문이_손상되면_기본_설정과_경고() throws IOException {
        Path file = write("settings.json", "{ \"screenSize\": \"LARGE\", ");

        LoadResult<Settings> result = new JsonSettingsRepository(file).load();

        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void settings가_빈_파일이거나_객체가_아니면_손상으로_본다() throws IOException {
        for (String content : new String[] {"", "[1, 2]", "42", "{} 뒤에 더 붙은 내용"}) {
            LoadResult<Settings> result = new JsonSettingsRepository(write("settings.json", content)).load();

            assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize(), content);
            assertEquals(1, result.warnings().size(), content);
        }
    }

    @Test
    void settings_알_수_없는_screenSize는_그_필드만_기본값이고_나머지는_살린다() throws IOException {
        Path file = write("settings.json", "{\"screenSize\": \"HUGE\", \"colorBlindMode\": true}");

        LoadResult<Settings> result = new JsonSettingsRepository(file).load();

        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());
        assertTrue(result.value().isColorBlindMode());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void settings_screenSize가_문자열이_아니어도_그_필드만_기본값() throws IOException {
        Path file = write("settings.json", "{\"screenSize\": 3, \"colorBlindMode\": true}");

        LoadResult<Settings> result = new JsonSettingsRepository(file).load();

        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());
        assertTrue(result.value().isColorBlindMode());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void settings_colorBlindMode가_불린이_아니면_그_필드만_기본값() throws IOException {
        Path file = write("settings.json", "{\"screenSize\": \"SMALL\", \"colorBlindMode\": \"yes\"}");

        LoadResult<Settings> result = new JsonSettingsRepository(file).load();

        assertEquals(ScreenSize.SMALL, result.value().getScreenSize());
        assertFalse(result.value().isColorBlindMode());
        assertEquals(1, result.warnings().size());
    }

    private static String keysJson(String override) {
        // 기본 키에서 override 항목만 바꾼 keys 객체. override는 "\"PAUSE\": 79" 같은 한 항목
        String name = override.substring(1, override.indexOf('"', 1));
        StringBuilder builder = new StringBuilder("{");
        for (GameAction action : GameAction.values()) {
            if (builder.length() > 1) builder.append(',');
            if (action.name().equals(name)) builder.append(override);
            else builder.append('"').append(action.name()).append("\": ").append(new tetris.feature.setting.KeyMap().keyOf(action));
        }
        return builder.append('}').toString();
    }

    @Test
    void settings_keys가_모두_유효하면_그대로_적용한다() throws IOException {
        Path file = write("settings.json", "{\"keys\": " + keysJson("\"PAUSE\": " + KeyEvent.VK_O) + "}");

        LoadResult<Settings> result = new JsonSettingsRepository(file).load();

        assertEquals(KeyEvent.VK_O, result.value().getKeyMap().keyOf(GameAction.PAUSE));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void settings_keys에_중복이_있으면_keys_전체를_기본값으로_하고_다른_필드는_살린다() throws IOException {
        // PAUSE를 QUIT 기본키(Q)와 같게 만든다
        Path file = write("settings.json",
                "{\"screenSize\": \"LARGE\", \"keys\": " + keysJson("\"PAUSE\": " + KeyEvent.VK_Q) + "}");

        LoadResult<Settings> result = new JsonSettingsRepository(file).load();

        assertEquals(ScreenSize.LARGE, result.value().getScreenSize());
        assertEquals(new Settings().getKeyMap().codes(), result.value().getKeyMap().codes());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void settings_keys에_예약키가_있으면_keys_전체를_기본값으로_한다() throws IOException {
        Path file = write("settings.json", "{\"keys\": " + keysJson("\"PAUSE\": " + KeyEvent.VK_ENTER) + "}");

        LoadResult<Settings> result = new JsonSettingsRepository(file).load();

        assertEquals(new Settings().getKeyMap().codes(), result.value().getKeyMap().codes());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void settings_keys에_누락이_있으면_keys_전체를_기본값으로_한다() throws IOException {
        Path file = write("settings.json", "{\"keys\": {\"MOVE_LEFT\": " + KeyEvent.VK_A + "}}");

        LoadResult<Settings> result = new JsonSettingsRepository(file).load();

        assertEquals(KeyEvent.VK_LEFT, result.value().getKeyMap().keyOf(GameAction.MOVE_LEFT));
        assertEquals(1, result.warnings().size());
    }

    @Test
    void settings_keys_값이_정수가_아니거나_keys가_객체가_아니면_기본값() throws IOException {
        for (String keys : new String[] {"\"abc\"", "[1,2,3]", keysJson("\"PAUSE\": \"P\""), keysJson("\"PAUSE\": 80.5")}) {
            LoadResult<Settings> result = new JsonSettingsRepository(write("settings.json", "{\"keys\": " + keys + "}")).load();

            assertEquals(new Settings().getKeyMap().codes(), result.value().getKeyMap().codes(), keys);
            assertEquals(1, result.warnings().size(), keys);
        }
    }

    @Test
    void settings_필드가_없으면_기본값이고_경고도_없다_PER5() throws IOException {
        LoadResult<Settings> result = new JsonSettingsRepository(write("settings.json", "{\"schemaVersion\": 1}")).load();

        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void settings_모르는_필드는_무시한다_PER5() throws IOException {
        Path file = write("settings.json", "{\"schemaVersion\": 9, \"screenSize\": \"SMALL\", \"future\": {\"a\": 1}}");

        LoadResult<Settings> result = new JsonSettingsRepository(file).load();

        assertEquals(ScreenSize.SMALL, result.value().getScreenSize());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void settings_읽을_수_없으면_기본값과_경고() throws IOException {
        Path directoryInPlaceOfFile = Files.createDirectory(dir.resolve("settings.json"));

        LoadResult<Settings> result = new JsonSettingsRepository(directoryInPlaceOfFile).load();

        assertEquals(ScreenSize.MEDIUM, result.value().getScreenSize());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void 손상된_settings는_다음_정상_저장이_덮어쓴다() throws IOException {
        Path file = write("settings.json", "깨진 파일");
        Settings settings = new JsonSettingsRepository(file).load().value();
        settings.setScreenSize(ScreenSize.SMALL);

        assertTrue(new JsonSettingsRepository(file).save(settings));

        assertEquals(ScreenSize.SMALL, new JsonSettingsRepository(file).load().value().getScreenSize());
    }
    //#endregion

    //#region scoreboard.json 오류 처리표
    @Test
    void scoreboard_파일이_없으면_빈_스코어보드이고_경고도_없다() {
        LoadResult<Scoreboard> result = new JsonScoreboardRepository(dir.resolve("scoreboard.json")).load();

        assertTrue(result.value().entries().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void scoreboard를_저장하고_다시_읽으면_순서와_필드가_같다() {
        Path file = dir.resolve("scoreboard.json");
        Scoreboard board = new Scoreboard(List.of(entry("홍길동", 300), entry("A B", 200), entry("c", 200)));

        assertTrue(new JsonScoreboardRepository(file).save(board));
        LoadResult<Scoreboard> result = new JsonScoreboardRepository(file).load();

        assertEquals(board.entries(), result.value().entries());
        assertEquals(List.of("홍길동", "A B", "c"), names(result.value()));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void scoreboard_저장_파일은_UTF8_한글과_schemaVersion을_담는다() throws IOException {
        Path file = dir.resolve("scoreboard.json");

        new JsonScoreboardRepository(file).save(new Scoreboard(List.of(entry("홍길동", 1))));
        String text = Files.readString(file, StandardCharsets.UTF_8);

        assertTrue(text.contains("홍길동"));
        assertTrue(text.contains("\"schemaVersion\" : 1"));
        assertTrue(text.contains("2026-09-29T12:34:56Z"));
    }

    @Test
    void scoreboard_구문이_손상되면_빈_스코어보드와_경고를_주고_파일을_corrupt로_옮긴다() throws IOException {
        Path file = write("scoreboard.json", "{ \"entries\": [ {\"name\": ");

        LoadResult<Scoreboard> result = new JsonScoreboardRepository(file).load();

        assertTrue(result.value().entries().isEmpty());
        assertEquals(1, result.warnings().size());
        assertFalse(Files.exists(file));
        assertEquals("{ \"entries\": [ {\"name\": ", Files.readString(dir.resolve("scoreboard.json.corrupt")));
    }

    @Test
    void scoreboard_entries가_없거나_배열이_아니면_손상으로_본다() throws IOException {
        for (String content : new String[] {"", "[]", "{\"schemaVersion\": 1}", "{\"entries\": {}}"}) {
            LoadResult<Scoreboard> result = new JsonScoreboardRepository(write("scoreboard.json", content)).load();

            assertTrue(result.value().entries().isEmpty(), content);
            assertEquals(1, result.warnings().size(), content);
        }
    }

    @Test
    void 손상_파일을_옮긴_뒤_다시_저장하면_새_파일이_생긴다() throws IOException {
        Path file = write("scoreboard.json", "깨짐");
        ScoreboardRepository repository = new JsonScoreboardRepository(file);
        Scoreboard board = repository.load().value();
        board.add(entry("a", 10));

        assertTrue(repository.save(board));

        assertEquals(List.of("a"), names(repository.load().value()));
        assertTrue(Files.exists(dir.resolve("scoreboard.json.corrupt")));
    }

    @Test
    void scoreboard_일부_항목이_무효면_그_항목만_버리고_경고한다() throws IOException {
        Path file = write("scoreboard.json", """
                {"schemaVersion": 1, "entries": [
                  {"name": "ok1", "score": 500, "level": 2, "lines": 4, "recordedAt": "2026-09-29T12:34:56Z"},
                  {"name": "A!", "score": 400},
                  {"name": "   ", "score": 400},
                  {"name": "ABCDEFGHIJK", "score": 400},
                  {"name": "neg", "score": -1},
                  {"name": "float", "score": 1.5},
                  {"name": "nolevel", "score": 10, "level": -3},
                  {"name": "nolines", "score": 10, "lines": "x"},
                  {"name": 7, "score": 10},
                  {"score": 10},
                  {"name": "noscore"},
                  "문자열 항목",
                  {"name": "ok2", "score": 300}
                ]}""");

        LoadResult<Scoreboard> result = new JsonScoreboardRepository(file).load();

        assertEquals(List.of("ok1", "ok2"), names(result.value()));
        assertEquals(1, result.warnings().size());
        assertTrue(Files.exists(file));
    }

    @Test
    void scoreboard_정렬이_안_되어_있으면_정렬하고_동점은_파일_순서를_유지하며_경고는_없다() throws IOException {
        Path file = write("scoreboard.json", """
                {"entries": [
                  {"name": "low", "score": 10},
                  {"name": "firstTie", "score": 50},
                  {"name": "top", "score": 90},
                  {"name": "secondTie", "score": 50}
                ]}""");

        LoadResult<Scoreboard> result = new JsonScoreboardRepository(file).load();

        assertEquals(List.of("top", "firstTie", "secondTie", "low"), names(result.value()));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void scoreboard_10개를_넘으면_상위_10개만_남기고_경고는_없다() throws IOException {
        StringBuilder entries = new StringBuilder();
        for (int i = 1; i <= 13; i++) {
            if (i > 1) entries.append(',');
            entries.append("{\"name\": \"p").append(i).append("\", \"score\": ").append(i * 10).append('}');
        }
        Path file = write("scoreboard.json", "{\"entries\": [" + entries + "]}");

        LoadResult<Scoreboard> result = new JsonScoreboardRepository(file).load();

        assertEquals(10, result.value().entries().size());
        assertEquals("p13", result.value().entries().get(0).name());
        assertEquals("p4", result.value().entries().get(9).name());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void scoreboard_level_lines_recordedAt이_없거나_잘못돼도_기록은_살린다_PER5() throws IOException {
        Path file = write("scoreboard.json", """
                {"entries": [
                  {"name": "a", "score": 20},
                  {"name": "b", "score": 10, "recordedAt": "어제"},
                  {"name": "c", "score": 5, "recordedAt": 12345}
                ]}""");

        LoadResult<Scoreboard> result = new JsonScoreboardRepository(file).load();

        assertEquals(List.of("a", "b", "c"), names(result.value()));
        ScoreEntry first = result.value().entries().get(0);
        assertEquals(1, first.level());
        assertEquals(0, first.lines());
        assertEquals(Instant.EPOCH, first.recordedAt());
        assertEquals(Instant.EPOCH, result.value().entries().get(1).recordedAt());
        assertEquals(Instant.EPOCH, result.value().entries().get(2).recordedAt());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void scoreboard_점수_0과_앞뒤_공백_이름도_처리한다() throws IOException {
        Path file = write("scoreboard.json", "{\"entries\": [{\"name\": \"  A B  \", \"score\": 0}]}");

        LoadResult<Scoreboard> result = new JsonScoreboardRepository(file).load();

        assertEquals(1, result.value().entries().size());
        assertEquals("A B", result.value().entries().get(0).name());
        assertEquals(0, result.value().entries().get(0).score());
    }

    @Test
    void scoreboard_큰_점수는_long으로_보존하고_int를_넘는_level은_버린다() throws IOException {
        Path file = write("scoreboard.json", """
                {"entries": [
                  {"name": "big", "score": 9000000000},
                  {"name": "lv", "score": 5, "level": 3000000000}
                ]}""");

        LoadResult<Scoreboard> result = new JsonScoreboardRepository(file).load();

        assertEquals(List.of("big"), names(result.value()));
        assertEquals(9_000_000_000L, result.value().entries().get(0).score());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void scoreboard_읽을_수_없으면_빈_스코어보드와_경고() throws IOException {
        Path directoryInPlaceOfFile = Files.createDirectory(dir.resolve("scoreboard.json"));

        LoadResult<Scoreboard> result = new JsonScoreboardRepository(directoryInPlaceOfFile).load();

        assertTrue(result.value().entries().isEmpty());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void Reset한_빈_스코어보드를_저장하면_다음_실행에서도_비어_있다_SBD7() {
        Path file = dir.resolve("scoreboard.json");
        Scoreboard board = new Scoreboard(List.of(entry("a", 10)));
        board.clear();

        assertTrue(new JsonScoreboardRepository(file).save(board));

        assertTrue(new JsonScoreboardRepository(file).load().value().entries().isEmpty());
    }
    //#endregion

    //#region 원자적 저장 (PER-4)
    @Test
    void 저장하면_기존_내용을_교체하고_임시_파일이_남지_않는다() throws IOException {
        Path file = dir.resolve("scoreboard.json");
        ScoreboardRepository repository = new JsonScoreboardRepository(file);
        repository.save(new Scoreboard(List.of(entry("old", 10))));

        assertTrue(repository.save(new Scoreboard(List.of(entry("new", 20)))));

        assertEquals(List.of("new"), names(repository.load().value()));
        try (var files = Files.list(dir)) {
            List<String> fileNames = new ArrayList<>(files.map(p -> p.getFileName().toString()).toList());
            assertEquals(List.of("scoreboard.json"), fileNames);
        }
    }

    @Test
    void 저장이_실패하면_false이고_기존_파일은_그대로이며_임시_파일이_남지_않는다() throws IOException {
        // 대상 경로가 비어 있지 않은 디렉터리라 교체가 불가능하다
        Path file = Files.createDirectory(dir.resolve("settings.json"));
        Files.writeString(file.resolve("child"), "x");

        assertFalse(new JsonSettingsRepository(file).save(new Settings()));

        assertTrue(Files.exists(file.resolve("child")));
        assertFalse(Files.exists(dir.resolve("settings.json.tmp")));
    }

    @Test
    void 저장_대상_폴더가_없으면_만들어서_저장한다() {
        Path file = dir.resolve("sub").resolve("settings.json");

        assertTrue(new JsonSettingsRepository(file).save(new Settings()));

        assertTrue(Files.exists(file));
    }
    //#endregion
}
