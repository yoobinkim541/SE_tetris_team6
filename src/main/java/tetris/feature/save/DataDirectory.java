package tetris.feature.save;

import java.nio.file.Path;

/** 사용자 데이터 위치 (PER-2): %APPDATA%\SETetrisTeam6\. 경로만 계산하고 디렉터리는 저장할 때 만들어진다 */
public class DataDirectory {
    private static final String DIRECTORY_NAME = "SETetrisTeam6";
    private static final String SETTINGS_FILE_NAME = "settings.json";
    private static final String SCOREBOARD_FILE_NAME = "scoreboard.json";

    private DataDirectory() {
    }

    public static Path resolve() {
        return resolve(System.getenv("APPDATA"), System.getProperty("user.home"));
    }

    /** APPDATA가 없거나 비어 있으면 {user.home}\AppData\Roaming을 쓴다 */
    public static Path resolve(String appData, String userHome) {
        Path base = (appData == null || appData.isBlank())
                ? Path.of(userHome, "AppData", "Roaming")
                : Path.of(appData);
        return base.resolve(DIRECTORY_NAME);
    }

    public static Path settingsFile() {
        return resolve().resolve(SETTINGS_FILE_NAME);
    }

    public static Path scoreboardFile() {
        return resolve().resolve(SCOREBOARD_FILE_NAME);
    }
}
