package tetris.feature.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;

/** settings.json 입출력 (PER-1~5). Jackson은 이 계층에서만 사용. 조작키도 여기에 포함. */
public class SettingsRepository {
    static final String WARN_NO_DATA_DIR = "데이터 폴더를 만들 수 없어 이번 실행의 설정은 저장되지 않습니다.";
    static final String WARN_CORRUPT = "설정 파일이 손상되어 기본 설정으로 시작합니다.";
    static final String WARN_SCREEN_SIZE = "설정의 화면 크기 값이 잘못되어 기본값을 사용합니다.";
    static final String WARN_COLOR_BLIND = "설정의 색맹 모드 값이 잘못되어 기본값을 사용합니다.";
    static final String WARN_KEYS = "조작키 설정이 잘못되어 모든 조작키를 기본값으로 되돌렸습니다.";

    // 파일 형식의 키 이름 (명세 12.1). enum 이름을 바꿔도 기존 파일이 깨지지 않도록 따로 둔다
    private static final Map<GameAction, String> KEY_NAMES = keyNames();

    private final Supplier<Path> fileLocator;

    public SettingsRepository() {
        this(DataDirectory::settingsFile);
    }

    /** 파일 위치를 주입한다(테스트용 임시 폴더 등). 위치를 구하다 UncheckedIOException이 나면 데이터 폴더가 없는 것으로 본다 */
    public SettingsRepository(Supplier<Path> fileLocator) {
        this.fileLocator = fileLocator;
    }

    private static Map<GameAction, String> keyNames() {
        EnumMap<GameAction, String> names = new EnumMap<>(GameAction.class);
        names.put(GameAction.MOVE_LEFT, "LEFT");
        names.put(GameAction.MOVE_RIGHT, "RIGHT");
        names.put(GameAction.SOFT_DROP, "SOFT_DROP");
        names.put(GameAction.ROTATE, "ROTATE");
        names.put(GameAction.HARD_DROP, "HARD_DROP");
        names.put(GameAction.PAUSE, "PAUSE");
        names.put(GameAction.QUIT, "QUIT");
        return Collections.unmodifiableMap(names);
    }

    /** 없음/손상/일부 무효 시 기본값으로 대체하고 warnings에 사유를 담는다 (명세 12.2). 파일이 없으면 기본값으로 새로 만든다 */
    public LoadResult<Settings> load() {
        Settings settings = new Settings();
        Path file;
        try {
            file = fileLocator.get();
        } catch (UncheckedIOException e) {
            return new LoadResult<>(settings, List.of(WARN_NO_DATA_DIR));
        }

        if (!Files.exists(file)) {
            save(settings);
            return new LoadResult<>(settings, List.of());
        }

        JsonNode root;
        try {
            root = JsonFiles.read(file);
        } catch (IOException e) {
            return new LoadResult<>(settings, List.of(WARN_CORRUPT));
        }
        if (root == null || !root.isObject()) return new LoadResult<>(settings, List.of(WARN_CORRUPT));

        List<String> warnings = new ArrayList<>();
        readScreenSize(root.get("screenSize"), settings, warnings);
        readColorBlindMode(root.get("colorBlindMode"), settings, warnings);
        readKeys(root.get("keys"), settings, warnings);
        return new LoadResult<>(settings, List.copyOf(warnings));
    }

    // 필드가 없으면 기본값을 조용히 쓰고(PER-5), 있는데 잘못됐으면 해당 필드만 기본값 + 경고
    private static void readScreenSize(JsonNode node, Settings settings, List<String> warnings) {
        if (node == null) return;
        if (node.isTextual()) {
            try {
                settings.setScreenSize(ScreenSize.valueOf(node.textValue()));
                return;
            } catch (IllegalArgumentException e) {
                // 아래에서 경고
            }
        }
        warnings.add(WARN_SCREEN_SIZE);
    }

    private static void readColorBlindMode(JsonNode node, Settings settings, List<String> warnings) {
        if (node == null) return;
        if (node.isBoolean()) settings.setColorBlindMode(node.booleanValue());
        else warnings.add(WARN_COLOR_BLIND);
    }

    // keys는 중복 금지 불변식 때문에 하나라도 잘못되면 전체를 기본값으로 둔다
    private static void readKeys(JsonNode node, Settings settings, List<String> warnings) {
        if (node == null) return;
        if (!node.isObject()) {
            warnings.add(WARN_KEYS);
            return;
        }
        Map<GameAction, Integer> codes = new EnumMap<>(GameAction.class);
        for (Map.Entry<GameAction, String> entry : KEY_NAMES.entrySet()) {
            JsonNode code = node.get(entry.getValue());
            if (code != null && code.isInt()) codes.put(entry.getKey(), code.intValue());
        }
        if (!settings.getKeyMap().tryReplaceAll(codes)) warnings.add(WARN_KEYS);
    }

    /** 원자적 저장. 실패하면 false */
    public boolean save(Settings settings) {
        ObjectNode root = JsonFiles.newObject();
        root.put("screenSize", settings.getScreenSize().name());
        root.put("colorBlindMode", settings.isColorBlindMode());
        ObjectNode keys = root.putObject("keys");
        for (Map.Entry<GameAction, Integer> entry : settings.getKeyMap().codes().entrySet()) {
            keys.put(KEY_NAMES.get(entry.getKey()), entry.getValue());
        }
        try {
            JsonFiles.writeAtomically(fileLocator.get(), root);
            return true;
        } catch (IOException | UncheckedIOException e) {
            return false;
        }
    }
}
