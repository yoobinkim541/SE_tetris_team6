package tetris.ui;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;

/**
 * UI 문자열 단일 출처 (명세 9.2). Key Guide 문구는 현재 KeyMap에서 생성하고 하드코딩하지 않는다.
 * 언어는 한글로 통일하고 게임 용어(SCORE, LEVEL, NEXT 등)와 게임명은 영문을 유지한다. 바꿀 때는 이 파일만 고친다.
 */
public class Messages {
    private static final Map<String, String> TEXT = new HashMap<>();

    static {
        TEXT.put("app.title", "SE Tetris");

        TEXT.put("menu.start", "게임 시작");
        TEXT.put("menu.settings", "설정");
        TEXT.put("menu.scoreboard", "스코어보드");
        TEXT.put("menu.exit", "게임 종료");

        TEXT.put("game.next", "NEXT");
        TEXT.put("game.score", "SCORE");
        TEXT.put("game.level", "LEVEL");
        TEXT.put("game.lines", "LINES");
        TEXT.put("game.state", "STATE");
        TEXT.put("game.keys", "KEYS");

        TEXT.put("pause.title", "PAUSED");
        TEXT.put("pause.resume", "계속하기");
        TEXT.put("pause.restart", "다시 시작");
        TEXT.put("pause.quit", "메인 메뉴로");

        TEXT.put("confirm.yes", "예");
        TEXT.put("confirm.no", "아니오");
        TEXT.put("confirm.quit", "게임을 끝내고 메인 메뉴로 갈까요?\n점수는 저장되지 않습니다");
        TEXT.put("confirm.restart", "처음부터 다시 시작할까요?\n점수는 저장되지 않습니다");
        TEXT.put("confirm.resetScoreboard", "스코어보드 기록을 모두 지울까요?");
        TEXT.put("confirm.resetSettings", "모든 설정을 기본값으로 되돌릴까요?");

        TEXT.put("gameOver.title", "GAME OVER");

        TEXT.put("name.title", "TOP 10 진입!");
        TEXT.put("name.prompt", "이름을 입력하세요 (한글·영문·숫자, 최대 10자)");
        TEXT.put("name.invalid", "이름은 공백을 뺀 1~10자로 입력하세요");

        TEXT.put("scoreboard.title", "SCOREBOARD");
        TEXT.put("scoreboard.empty", "기록 없음");
        TEXT.put("scoreboard.rank", "순위");
        TEXT.put("scoreboard.name", "이름");
        TEXT.put("scoreboard.score", "점수");
        TEXT.put("scoreboard.level", "레벨");
        TEXT.put("scoreboard.mainMenu", "메인 메뉴");
        TEXT.put("scoreboard.exit", "게임 종료");
        TEXT.put("scoreboard.saveFailed", "기록을 저장하지 못했습니다 (이번 실행에서만 유지됩니다)");

        TEXT.put("settings.title", "설정");
        TEXT.put("settings.screenSize", "화면 크기");
        TEXT.put("settings.controlKeys", "조작키");
        TEXT.put("settings.colorBlind", "색맹 모드");
        TEXT.put("settings.resetScoreboard", "스코어보드 초기화");
        TEXT.put("settings.resetSettings", "설정 초기화");
        TEXT.put("settings.on", "ON");
        TEXT.put("settings.off", "OFF");
        TEXT.put("settings.pressKey", "새 키를 누르세요 (Esc 취소)");
        TEXT.put("settings.keyChanged", "변경했습니다");
        TEXT.put("settings.reservedKey", "사용할 수 없는 키입니다");
        TEXT.put("settings.duplicateKey", "이미 [%s]에 사용 중입니다");
        TEXT.put("settings.saveFailed", "설정을 저장하지 못했습니다 (이번 실행에만 적용됩니다)");
        TEXT.put("settings.scoreboardCleared", "스코어보드를 초기화했습니다");
        TEXT.put("settings.settingsReset", "설정을 기본값으로 되돌렸습니다");

        TEXT.put("size.SMALL", "Small");
        TEXT.put("size.MEDIUM", "Medium");
        TEXT.put("size.LARGE", "Large");

        TEXT.put("action.MOVE_LEFT", "왼쪽 이동");
        TEXT.put("action.MOVE_RIGHT", "오른쪽 이동");
        TEXT.put("action.SOFT_DROP", "소프트 드롭");
        TEXT.put("action.ROTATE", "회전");
        TEXT.put("action.HARD_DROP", "하드 드롭");
        TEXT.put("action.PAUSE", "일시정지");
        TEXT.put("action.QUIT", "게임 끝내기");

        TEXT.put("guide.move", "이동");
        TEXT.put("guide.mainMenu", "↑/↓ 이동   Enter 선택");
        TEXT.put("guide.settings", "↑/↓ 이동   Enter 선택   Esc 뒤로");
        TEXT.put("guide.controlKeys", "↑/↓ 이동   Enter 키 변경   Esc 뒤로");
        TEXT.put("guide.scoreboard", "Enter/Esc 메인 메뉴");
        TEXT.put("guide.scoreboardAfterGame", "↑/↓ 이동   Enter 선택");
        TEXT.put("guide.nameInput", "Enter 저장");
        TEXT.put("guide.gameOver", "Enter 계속");
        TEXT.put("guide.pause", "↑/↓ 선택   Enter 확인   %s/Esc 계속");
        TEXT.put("guide.confirm", "←/→ 선택   Enter 확인   Esc 아니오");

        TEXT.put("warn.settingsLoad", "설정 파일을 읽지 못해 기본 설정으로 시작합니다");
        TEXT.put("warn.scoreboardLoad", "스코어보드 파일을 읽지 못해 빈 기록으로 시작합니다");
    }

    public static String get(String key) {
        String text = TEXT.get(key);
        if (text == null) throw new IllegalArgumentException("unknown message key: " + key);
        return text;
    }

    public static String format(String key, Object... args) {
        return String.format(get(key), args);
    }

    public static String actionName(GameAction action) {
        return get("action." + action.name());
    }

    /** 키 이름. 방향키는 화살표 기호, 나머지는 KeyMap.displayName (KEY-6) */
    public static String keyName(KeyMap keyMap, GameAction action) {
        return switch (keyMap.keyOf(action)) {
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            default -> keyMap.displayName(action);
        };
    }

    /**
     * 화면별 Key Guide 문구. 플레이 키는 keyMap에서 만들어 키 변경이 자동 반영된다 (명세 9.1).
     * GAME은 정보 패널에 한 줄씩 그리도록 줄바꿈(\n)으로 구분한다.
     */
    public static String keyGuide(MainFrame.Screen screen, KeyMap keyMap) {
        return switch (screen) {
            case MAIN_MENU -> get("guide.mainMenu");
            case SETTINGS -> get("guide.settings");
            case SCOREBOARD -> get("guide.scoreboard");
            case NAME_INPUT -> get("guide.nameInput");
            case GAME_OVER -> get("guide.gameOver");
            case GAME -> String.join("\n",
                    keyName(keyMap, GameAction.MOVE_LEFT) + "/" + keyName(keyMap, GameAction.MOVE_RIGHT)
                            + "  " + get("guide.move"),
                    gameKeyLine(keyMap, GameAction.SOFT_DROP),
                    gameKeyLine(keyMap, GameAction.ROTATE),
                    gameKeyLine(keyMap, GameAction.HARD_DROP),
                    gameKeyLine(keyMap, GameAction.PAUSE),
                    gameKeyLine(keyMap, GameAction.QUIT));
        };
    }

    /** Pause 화면 전용 Key Guide */
    public static String pauseKeyGuide(KeyMap keyMap) {
        return format("guide.pause", keyName(keyMap, GameAction.PAUSE));
    }

    private static String gameKeyLine(KeyMap keyMap, GameAction action) {
        return keyName(keyMap, action) + "  " + actionName(action);
    }
}
