package tetris.feature.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** settings.json 입출력 (PER-1~5). 조작키도 여기에 포함. 필드가 없으면 기본값, 무효이면 그 필드만 기본값 */
public class JsonSettingsRepository implements SettingsRepository {
    private final JsonFile file;

    public JsonSettingsRepository() {
        this(DataDirectory.settingsFile());
    }

    public JsonSettingsRepository(Path path) {
        this.file = new JsonFile(path);
    }

    @Override
    public LoadResult<Settings> load() {
        JsonFile.ReadResult read = file.read();
        return switch (read.status()) {
            case MISSING -> defaultSettings();
            case UNREADABLE -> defaultSettings("설정 파일을 읽을 수 없어 기본 설정으로 시작합니다.");
            case MALFORMED -> defaultSettings("설정 파일이 손상되어 기본 설정으로 시작합니다.");
            case OK -> loadFields(read.root());
        };
    }

    @Override
    public boolean save(Settings settings) {
        ObjectNode root = JsonFile.newRoot();
        root.put("screenSize", settings.getScreenSize().name());
        root.put("colorBlindMode", settings.isColorBlindMode());
        ObjectNode keys = root.putObject("keys");
        settings.getKeyMap().codes().forEach((action, keyCode) -> keys.put(action.name(), keyCode));
        return file.write(root);
    }

    private LoadResult<Settings> loadFields(JsonNode root) {
        Settings settings = new Settings();
        List<String> warnings = new ArrayList<>();

        applyScreenSize(root.get("screenSize"), settings, warnings);
        applyColorBlindMode(root.get("colorBlindMode"), settings, warnings);
        applyKeys(root.get("keys"), settings, warnings);
        return new LoadResult<>(settings, warnings);
    }

    private static void applyScreenSize(JsonNode node, Settings settings, List<String> warnings) {
        if (isAbsent(node)) return;

        if (node.isTextual()) {
            try {
                settings.setScreenSize(ScreenSize.valueOf(node.asText()));
                return;
            } catch (IllegalArgumentException unknownSize) {
                // 아래에서 경고한다
            }
        }
        warnings.add("화면 크기 설정이 올바르지 않아 기본값(Medium)을 사용합니다.");
    }

    private static void applyColorBlindMode(JsonNode node, Settings settings, List<String> warnings) {
        if (isAbsent(node)) return;

        if (node.isBoolean()) settings.setColorBlindMode(node.asBoolean());
        else warnings.add("색맹 모드 설정이 올바르지 않아 기본값(꺼짐)을 사용합니다.");
    }

    /** 중복·누락·예약키가 하나라도 있으면 keys 전체를 기본값으로 둔다. 검증은 KeyMap이 한다 */
    private static void applyKeys(JsonNode node, Settings settings, List<String> warnings) {
        if (isAbsent(node)) return;

        if (!settings.getKeyMap().tryReplaceAll(readKeyCodes(node))) {
            warnings.add("조작키 설정이 올바르지 않아 기본 조작키를 사용합니다.");
        }
    }

    private static Map<GameAction, Integer> readKeyCodes(JsonNode keysNode) {
        Map<GameAction, Integer> keyCodes = new EnumMap<>(GameAction.class);
        if (!keysNode.isObject()) return keyCodes;

        for (GameAction action : GameAction.values()) {
            JsonNode keyCode = keysNode.get(action.name());
            if (keyCode != null && keyCode.isInt()) keyCodes.put(action, keyCode.asInt());
        }
        return keyCodes;
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull();
    }

    private static LoadResult<Settings> defaultSettings(String... warnings) {
        return new LoadResult<>(new Settings(), List.of(warnings));
    }
}
