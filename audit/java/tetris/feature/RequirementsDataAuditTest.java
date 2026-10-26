package tetris.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tetris.feature.game.*;
import tetris.feature.save.*;
import tetris.feature.score.*;
import tetris.feature.setting.*;

/** 인수 조건을 직접 검증한다. 실패를 skip이나 기대 실패로 바꾸지 않는다. */
class RequirementsDataAuditTest {
    @TempDir Path directory;

    @ParameterizedTest @EnumSource(Difficulty.class)
    void difficultyIsExplicitlyPersistedAndSurvivesReload(Difficulty difficulty) throws Exception {
        Path path = directory.resolve("settings.json");
        Settings settings = new Settings();
        settings.setDifficulty(difficulty);
        assertTrue(new JsonSettingsRepository(path).save(settings));
        var loaded = new JsonSettingsRepository(path).load();
        assertAll(() -> assertTrue(Files.readString(path).contains("\"difficulty\""), "difficulty 필드 누락"),
                () -> assertEquals(difficulty, loaded.value().getDifficulty()),
                () -> assertTrue(loaded.warnings().isEmpty()));
    }

    @Test
    void legacySettingsRemainNormalWithoutWarnings() throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{\"screenSize\":\"LARGE\",\"colorBlindMode\":true}");
        var loaded = new JsonSettingsRepository(path).load();
        assertEquals(Difficulty.NORMAL, loaded.value().getDifficulty());
        assertEquals(ScreenSize.LARGE, loaded.value().getScreenSize());
        assertTrue(loaded.value().isColorBlindMode());
        assertTrue(loaded.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"\"IMPOSSIBLE\"", "3", "true", "{}", "[]"})
    void invalidDifficultyWarnsAndPreservesOtherFields(String value) throws Exception {
        Path path = directory.resolve("settings.json");
        Files.writeString(path, "{\"screenSize\":\"LARGE\",\"colorBlindMode\":true,\"difficulty\":" + value + "}");
        var loaded = new JsonSettingsRepository(path).load();
        assertAll(() -> assertEquals(Difficulty.NORMAL, loaded.value().getDifficulty()),
                () -> assertFalse(loaded.warnings().isEmpty(), "무효 difficulty 경고 누락"),
                () -> assertEquals(ScreenSize.LARGE, loaded.value().getScreenSize()),
                () -> assertTrue(loaded.value().isColorBlindMode()));
    }

    static Stream<Arguments> categories() {
        return Stream.of(Arguments.of(GameMode.CLASSIC, Difficulty.EASY),
                Arguments.of(GameMode.CLASSIC, Difficulty.NORMAL),
                Arguments.of(GameMode.CLASSIC, Difficulty.HARD), Arguments.of(GameMode.ITEM, Difficulty.NORMAL));
    }

    @ParameterizedTest @MethodSource("categories")
    void scoreCategorySurvivesSaveAndReload(GameMode mode, Difficulty difficulty) throws Exception {
        Path path = directory.resolve("scoreboard.json");
        ScoreEntry entry = new ScoreEntry("Player", 3_000_000_000L, 7, 15, mode, difficulty, Instant.EPOCH);
        assertTrue(new JsonScoreboardRepository(path).save(new Scoreboard(List.of(entry))));
        var loaded = new JsonScoreboardRepository(path).load();
        assertEquals(1, loaded.value().entries().size());
        ScoreEntry result = loaded.value().entries().getFirst();
        assertAll(() -> assertEquals(mode, result.mode()), () -> assertEquals(difficulty, result.difficulty()),
                () -> assertEquals(entry.score(), result.score()),
                () -> assertTrue(Files.readString(path).contains("\"mode\"")),
                () -> assertTrue(Files.readString(path).contains("\"difficulty\"")));
    }

    static Stream<String> invalidCategories() {
        return Stream.of("\"mode\":\"UNKNOWN\"", "\"mode\":12", "\"mode\":true", "\"mode\":{}",
                "\"difficulty\":\"UNKNOWN\"", "\"difficulty\":12", "\"difficulty\":true", "\"difficulty\":[]");
    }

    @ParameterizedTest @MethodSource("invalidCategories")
    void invalidCategoryDropsOnlyThatRecordAndWarns(String field) throws Exception {
        Path path = directory.resolve("scoreboard.json");
        Files.writeString(path, "{\"entries\":[{\"name\":\"legacy\",\"score\":5},"
                + "{\"name\":\"invalid\",\"score\":9999," + field + "}]}");
        var loaded = new JsonScoreboardRepository(path).load();
        assertAll(() -> assertEquals(List.of("legacy"), loaded.value().entries().stream().map(ScoreEntry::name).toList()),
                () -> assertFalse(loaded.warnings().isEmpty()));
    }

    @Test
    void eachOfFourCategoriesRetainsItsOwnTopTen() {
        List<ScoreEntry> entries = new ArrayList<>();
        int category = 0;
        for (var value : categories().toList()) {
            Object[] arguments = value.get();
            for (int rank = 0; rank < 10; rank++)
                entries.add(new ScoreEntry("c" + category + "p" + rank, category * 1000L + rank, 1, 0,
                        (GameMode) arguments[0], (Difficulty) arguments[1], Instant.EPOCH));
            category++;
        }
        assertEquals(40, new Scoreboard(entries).entries().size(), "다른 분류의 기록이 전체 Top 10에서 탈락하면 안 된다");
    }

    @Test
    void legacyScoreRecordKeepsNumericAndOptionalFieldCompatibility() throws Exception {
        Path path = directory.resolve("scoreboard.json");
        Files.writeString(path, "{\"entries\":[{\"name\":\"legacy\",\"score\":3000000000,\"recordedAt\":\"bad\"}]}");
        ScoreEntry entry = new JsonScoreboardRepository(path).load().value().entries().getFirst();
        assertEquals(3_000_000_000L, entry.score());
        assertEquals(1, entry.level());
        assertEquals(0, entry.lines());
        assertEquals(Instant.EPOCH, entry.recordedAt());
        assertEquals(GameMode.CLASSIC, entry.mode());
        assertEquals(Difficulty.NORMAL, entry.difficulty());
    }
}
