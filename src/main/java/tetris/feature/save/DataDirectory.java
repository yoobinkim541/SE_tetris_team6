package tetris.feature.save;

import java.nio.file.Path;

/** 사용자 데이터 위치 (PER-2): %APPDATA%\SETetrisTeam6\ */
public class DataDirectory {
    /** 디렉터리를 반환(없으면 생성). APPDATA가 없으면 {user.home}\AppData\Roaming 대체 */
    public static Path resolve() {
        throw new UnsupportedOperationException("TODO");
    }

    public static Path settingsFile() {
        throw new UnsupportedOperationException("TODO");
    }

    public static Path scoreboardFile() {
        throw new UnsupportedOperationException("TODO");
    }
}
