package tetris.feature.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;

/** 전체 화면은 settings.json에 남고, 예전 파일은 창 모드로 읽힌다 */
class FullscreenSettingsTest {
    @TempDir Path directory;

    @Test
    void fullscreenRoundTripsAndKeepsWindowedSize() {
        Settings settings = new Settings();
        settings.setScreenSize(ScreenSize.SMALL);
        settings.setFullscreen(true);
        JsonSettingsRepository repository = new JsonSettingsRepository(directory.resolve("settings.json"));
        assertTrue(repository.save(settings));

        Settings loaded = repository.load().value();
        assertTrue(loaded.isFullscreen());
        assertEquals(ScreenSize.SMALL, loaded.getScreenSize());
    }

    @Test
    void oldFileWithoutFullscreenStaysWindowed() throws Exception {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{\"screenSize\":\"LARGE\",\"colorBlindMode\":true}");
        Settings loaded = new JsonSettingsRepository(file).load().value();
        assertFalse(loaded.isFullscreen());
        assertEquals(ScreenSize.LARGE, loaded.getScreenSize());
        assertTrue(loaded.isColorBlindMode());
    }

    @Test
    void legacyFullscreenScreenSizeIsAccepted() throws Exception {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{\"screenSize\":\"FULLSCREEN\",\"colorBlindMode\":true}");
        Settings loaded = new JsonSettingsRepository(file).load().value();
        assertTrue(loaded.isFullscreen());
        assertTrue(loaded.isColorBlindMode());
        assertEquals(ScreenSize.MEDIUM, loaded.getScreenSize());
    }
}
