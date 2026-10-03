package tetris.feature.save;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 사용자 데이터 위치 (PER-2): %APPDATA%\SETetrisTeam6\ */
public class DataDirectory {
    private static final String APP_FOLDER = "SETetrisTeam6";

    private DataDirectory() {
    }

    /** 디렉터리를 반환(없으면 생성). APPDATA가 없으면 {user.home}\AppData\Roaming 대체. 만들 수 없으면 UncheckedIOException */
    public static Path resolve() {
        String appData = System.getenv("APPDATA");
        Path base = (appData == null || appData.isBlank())
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                : Path.of(appData);
        Path dir = base.resolve(APP_FOLDER);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    public static Path settingsFile() {
        return resolve().resolve("settings.json");
    }

    public static Path scoreboardFile() {
        return resolve().resolve("scoreboard.json");
    }
}
