package tetris.feature.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tetris.feature.game.Difficulty;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;
import tetris.feature.setting.ControlStyle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** settings.json 입출력 (PER-1~5, PER-7~9). 조작키도 여기에 포함. 필드가 없으면 기본값, 무효이면 그 필드만 기본값 */
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
        root.put("devNetworkOverlay", settings.isDevNetworkOverlay());
        root.put("difficulty", settings.getDifficulty().name());
        root.put("controlStyle", settings.getControlStyle().name());
        root.put("effects", settings.isEffectsHigh() ? "high" : "low");
        ObjectNode keys = root.putObject("keys");
        settings.getKeyMap().codes().forEach((action, keyCode) -> keys.put(action.name(), keyCode));
        return file.write(root);
    }

    private LoadResult<Settings> loadFields(JsonNode root) {
        Settings settings = new Settings();
        List<String> warnings = new ArrayList<>();

        applyScreenSize(root.get("screenSize"), settings, warnings);
        applyColorBlindMode(root.get("colorBlindMode"), settings, warnings);
        applyDevNetworkOverlay(root.get("devNetworkOverlay"), settings, warnings);
        applyDifficulty(root.get("difficulty"), settings, warnings);
        applyControlStyle(root.get("controlStyle"), settings, warnings);
        applyEffects(root.get("effects"), settings, warnings);
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

    /** 이전 버전 파일에는 없으므로 없으면 경고 없이 꺼짐 (dev-net-overlay, colorBlindMode와 같은 방식) */
    private static void applyDevNetworkOverlay(JsonNode node, Settings settings, List<String> warnings) {
        if (isAbsent(node)) return;

        if (node.isBoolean()) settings.setDevNetworkOverlay(node.asBoolean());
        else warnings.add("개발자 연결 상태 표시 설정이 올바르지 않아 기본값(꺼짐)을 사용합니다.");
    }

    /** 이전 버전 파일에는 difficulty가 없으므로 없으면 경고 없이 NORMAL (PER-7~9) */
    private static void applyDifficulty(JsonNode node, Settings settings, List<String> warnings) {
        if (isAbsent(node)) return;

        if (node.isTextual()) {
            try {
                settings.setDifficulty(Difficulty.valueOf(node.asText()));
                return;
            } catch (IllegalArgumentException unknownDifficulty) {
                // 아래에서 경고한다
            }
        }
        warnings.add("난이도 설정이 올바르지 않아 기본값(Normal)을 사용합니다.");
    }

    /** 없으면 경고 없이 모던. 값이 이상하면 모던으로 두고 경고 한 번 (colorBlindMode와 같은 방식) */
    private static void applyControlStyle(JsonNode node, Settings settings, List<String> warnings) {
        if (isAbsent(node)) return;
        if (node.isTextual()) {
            try {
                settings.setControlStyle(ControlStyle.valueOf(node.asText()));
                return;
            } catch (IllegalArgumentException unknown) {
                // 아래에서 경고한다
            }
        }
        warnings.add("조작 방식 설정이 올바르지 않아 기본값(모던)을 사용합니다.");
    }

    /** 없으면 경고 없이 높음. high/low 가 아니면 높음으로 두고 경고 한 번 */
    private static void applyEffects(JsonNode node, Settings settings, List<String> warnings) {
        if (isAbsent(node)) return;
        if (node.isTextual()) {
            String value = node.asText();
            if ("high".equalsIgnoreCase(value)) {
                settings.setEffectsHigh(true);
                return;
            }
            if ("low".equalsIgnoreCase(value)) {
                settings.setEffectsHigh(false);
                return;
            }
        }
        warnings.add("연출 설정이 올바르지 않아 기본값(높음)을 사용합니다.");
    }

    /** 중복·누락·예약키가 하나라도 있으면 keys 전체를 기본값으로 둔다. 예전 파일에 없는 반시계·홀드는 기본키로 채운다 */
    private static void applyKeys(JsonNode node, Settings settings, List<String> warnings) {
        if (isAbsent(node)) return;
        Map<GameAction, Integer> codes = readKeyCodes(node);
        fillMissingModernKeys(codes);
        if (!settings.getKeyMap().tryReplaceAll(codes)) {
            warnings.add("조작키 설정이 올바르지 않아 기본 조작키를 사용합니다.");
        }
    }

    /** 예전 설정 파일에는 없는 기능만 기본키로 채운다. 나머지 누락은 그대로 둬서 전체 검증이 실패하게 한다 */
    private static void fillMissingModernKeys(Map<GameAction, Integer> codes) {
        KeyMap defaults = new KeyMap();
        if (!codes.containsKey(GameAction.ROTATE_CCW)) codes.put(GameAction.ROTATE_CCW, defaults.keyOf(GameAction.ROTATE_CCW));
        if (!codes.containsKey(GameAction.HOLD)) codes.put(GameAction.HOLD, defaults.keyOf(GameAction.HOLD));
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
