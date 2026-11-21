package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.game.Difficulty;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;

/** 메뉴 선택 규칙(순환), CNF-1(확인창 기본 No), SET-2(설정 항목 동작), KEY-2·KEY-3 안내 */
public class MenuPanelsTest {
    private final List<String> calls = new ArrayList<>();

    private MenuItem item(String name) {
        return new MenuItem(name, () -> calls.add(name));
    }

    @Test
    void mainMenuSelectionWrapsAround() {
        MainMenuPanel menu = new MainMenuPanel(List.of(item("start"), item("settings"), item("scores"), item("exit")));
        menu.moveSelection(-1);
        assertEquals(3, menu.getSelectedIndex());
        menu.moveSelection(1);
        assertEquals(0, menu.getSelectedIndex());

        menu.moveSelection(1);
        menu.activateSelected();
        assertEquals(List.of("settings"), calls);
    }

    @Test
    void confirmDialogDefaultsToNo() {
        ConfirmDialog dialog = new ConfirmDialog("?", () -> calls.add("yes"), () -> calls.add("no"));
        assertFalse(dialog.isYesSelected());
        dialog.confirm();
        dialog.toggleSelection();
        dialog.confirm();
        assertEquals(List.of("no", "yes"), calls);
    }

    @Test
    void pausePanelRunsSelectedItem() {
        PausePanel pause = new PausePanel(() -> calls.add("resume"), () -> calls.add("restart"), () -> calls.add("quit"));
        pause.activateSelected();
        pause.moveSelection(-1);
        pause.activateSelected();
        assertEquals(List.of("resume", "quit"), calls);
    }

    @Test
    void scoreboardAfterGameOffersExit() {
        ScoreBoardPanel board = new ScoreBoardPanel(() -> calls.add("menu"), () -> calls.add("exit"));
        board.show(List.of(), -1, true);
        board.moveSelection(1);
        board.activateSelected();

        board.show(List.of(), -1);
        board.moveSelection(1); // 메뉴에서 연 스코어보드는 선택지가 없다
        board.activateSelected();
        assertEquals(List.of("exit", "menu"), calls);
    }

    // ── SettingPanel ─────────────────────────
    private final Settings settings = new Settings();
    private int saves;
    private final SettingPanel settingPanel = // --dev 없이 실행한 일반 화면
            new SettingPanel(settings, () -> { saves++; return true; }, () -> true, () -> calls.add("back"), () -> false);

    @Test
    void screenSizeCyclesAndSaves() {
        settingPanel.activateSelected(); // Medium -> Large
        assertEquals(ScreenSize.LARGE, settings.getScreenSize());
        assertFalse(settings.isFullscreen());
        settingPanel.activateSelected(); // Large -> 전체 화면
        assertTrue(settings.isFullscreen());
        assertEquals(ScreenSize.LARGE, settings.getScreenSize());
        settingPanel.activateSelected(); // 전체 화면 -> Small
        assertEquals(ScreenSize.SMALL, settings.getScreenSize());
        assertFalse(settings.isFullscreen());
        assertEquals(3, saves);
    }

    @Test
    void difficultyCyclesWithEnterAndSaves() {
        settingPanel.moveSelection(1); // Difficulty (Screen Size 바로 아래)
        settingPanel.activateSelected();
        assertEquals(Difficulty.HARD, settings.getDifficulty());
        settingPanel.activateSelected();
        assertEquals(Difficulty.EASY, settings.getDifficulty());
        settingPanel.activateSelected();
        assertEquals(Difficulty.NORMAL, settings.getDifficulty());
        assertEquals(3, saves);
        assertEquals(ScreenSize.MEDIUM, settings.getScreenSize());
    }

    @Test
    void difficultyCyclesWithArrowKeysAndSaves() {
        settingPanel.moveSelection(1);
        settingPanel.changeValue(-1);
        assertEquals(Difficulty.EASY, settings.getDifficulty());
        settingPanel.changeValue(-1);
        assertEquals(Difficulty.HARD, settings.getDifficulty());
        settingPanel.changeValue(1);
        assertEquals(Difficulty.EASY, settings.getDifficulty());
        assertEquals(3, saves);
    }

    @Test
    void arrowKeysChangeOnlyTheSelectedCyclingItem() {
        settingPanel.changeValue(1); // Screen Size
        assertEquals(ScreenSize.LARGE, settings.getScreenSize());
        assertEquals(Difficulty.NORMAL, settings.getDifficulty());

        settingPanel.moveSelection(3); // Color Blind: ←/→로는 바뀌지 않는다
        settingPanel.changeValue(1);
        assertFalse(settings.isColorBlindMode());
        assertEquals(1, saves);

        settingPanel.openControlKeys(); // 하위 화면에서는 무시
        settingPanel.changeValue(1);
        assertEquals(1, saves);
    }

    @Test
    void difficultySaveFailureShowsWarningButKeepsValue() {
        SettingPanel failing = new SettingPanel(settings, () -> false, () -> true, () -> { });
        failing.moveSelection(1);
        failing.activateSelected();
        assertEquals(Difficulty.HARD, settings.getDifficulty());
        assertEquals(Messages.get("settings.saveFailed"), failing.getNotice());
    }

    @Test
    void resetSettingsRestoresNormalDifficulty() {
        settings.setDifficulty(Difficulty.HARD);
        settings.resetToDefaults();
        assertEquals(Difficulty.NORMAL, settings.getDifficulty());
    }

    @Test
    void difficultyLabelsComeFromMessages() {
        assertEquals("난이도", Messages.get("settings.difficulty"));
        assertEquals("Easy", Messages.difficultyName(Difficulty.EASY));
        assertEquals("Normal", Messages.difficultyName(Difficulty.NORMAL));
        assertEquals("Hard", Messages.difficultyName(Difficulty.HARD));
    }

    @Test
    void colorBlindToggles() {
        settingPanel.moveSelection(3);
        settingPanel.activateSelected();
        assertTrue(settings.isColorBlindMode());
    }

    @Test
    void devNetworkOverlayIsHiddenWithoutDevFlag() { // 일반 실행의 설정 화면은 2차와 같다
        settingPanel.moveSelection(8); // 효과음·배경음 다음이 Reset Scoreboard
        settingPanel.activateSelected();
        assertFalse(settings.isDevNetworkOverlay());
        assertEquals(0, saves, "Reset Scoreboard 확인창만 열리고 설정은 바뀌지 않는다");
    }

    @Test
    void devNetworkOverlayTogglesRightAfterColorBlindUnderDevFlag() { // dev-net-overlay
        SettingPanel dev = new SettingPanel(settings, () -> { saves++; return true; }, () -> true, () -> { }, () -> true);
        dev.moveSelection(8); // 효과음·배경음 다음이 개발자 항목
        dev.activateSelected();
        assertTrue(settings.isDevNetworkOverlay());
        assertEquals(1, saves, "바뀐 설정은 바로 저장한다");

        dev.activateSelected();
        assertFalse(settings.isDevNetworkOverlay());
    }

    @Test
    void savedDevNetworkOverlayStaysVisibleUntilTurnedOff() { // --dev 없이도 켜진 값은 끌 수 있어야 한다
        settings.setDevNetworkOverlay(true);
        settingPanel.moveSelection(8);
        settingPanel.activateSelected();
        assertFalse(settings.isDevNetworkOverlay());

        settingPanel.moveSelection(1); // 항목이 사라져 선택은 Reset Scoreboard에 있다 → 다음은 Reset Settings
        settingPanel.moveSelection(1); // 맨 아래에서 맨 위로 순환
        settingPanel.activateSelected();
        assertEquals(ScreenSize.LARGE, settings.getScreenSize(), "연출이 있는 목록으로 돌아왔다");
    }

    @Test
    void devNetworkOverlayLabelComesFromMessages() {
        assertEquals("개발자: 연결 상태 표시", Messages.get("settings.devNetOverlay"));
        assertEquals("ON", Messages.get("settings.on"));
        assertEquals("OFF", Messages.get("settings.off"));
    }

    @Test
    void effectsRowCyclesHighAndLowAndPersists() throws Exception {
        settingPanel.moveSelection(5);
        settingPanel.activateSelected();
        assertFalse(settings.isEffectsHigh());
        settingPanel.changeValue(-1);
        assertTrue(settings.isEffectsHigh());
        var file = java.nio.file.Files.createTempFile("effects", ".json");
        try {
            settings.setEffectsHigh(false);
            assertTrue(new tetris.feature.save.JsonSettingsRepository(file).save(settings));
            var loaded = new tetris.feature.save.JsonSettingsRepository(file).load();
            assertTrue(loaded.warnings().isEmpty());
            assertFalse(loaded.value().isEffectsHigh());
            var absent = java.nio.file.Files.createTempFile("effects-absent", ".json");
            java.nio.file.Files.writeString(absent, "{\"screenSize\":\"SMALL\"}");
            assertTrue(new tetris.feature.save.JsonSettingsRepository(absent).load().value().isEffectsHigh());
            java.nio.file.Files.deleteIfExists(absent);
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            tetris.ui.fx.StarfieldBackground.setEffectsHigh(true);
        }
    }

    @Test
    void keyCaptureAppliesValidKey() {
        settingPanel.openControlKeys();
        settingPanel.moveSelection(5); // HARD_DROP (반시계가 하나 늘었다)
        settingPanel.activateSelected();
        assertTrue(settingPanel.isCapturing());

        settingPanel.captureKey(KeyEvent.VK_X);
        assertFalse(settingPanel.isCapturing());
        assertEquals(KeyEvent.VK_X, settings.getKeyMap().keyOf(GameAction.HARD_DROP));
        assertEquals(1, saves);
    }

    @Test
    void keyCaptureRejectsReservedAndDuplicateKeys() {
        settingPanel.openControlKeys();
        settingPanel.beginKeyCapture(GameAction.HARD_DROP);
        settingPanel.captureKey(KeyEvent.VK_ENTER);
        assertEquals(Messages.get("settings.reservedKey"), settingPanel.getNotice());

        settingPanel.beginKeyCapture(GameAction.HARD_DROP);
        settingPanel.captureKey(KeyEvent.VK_P);
        assertEquals(Messages.format("settings.duplicateKey", Messages.actionName(GameAction.PAUSE)),
                settingPanel.getNotice());

        assertEquals(KeyEvent.VK_SPACE, settings.getKeyMap().keyOf(GameAction.HARD_DROP));
        assertEquals(0, saves);
    }

    @Test
    void cancelKeyCaptureKeepsKey() {
        settingPanel.beginKeyCapture(GameAction.ROTATE);
        settingPanel.cancelKeyCapture();
        settingPanel.captureKey(KeyEvent.VK_X);
        assertEquals(KeyEvent.VK_UP, settings.getKeyMap().keyOf(GameAction.ROTATE));
    }

    @Test
    void closeControlKeysReturnsToList() {
        settingPanel.moveSelection(2);
        settingPanel.activateSelected(); // Control Keys
        assertTrue(settingPanel.isInControlKeys());
        settingPanel.closeControlKeys();
        assertFalse(settingPanel.isInControlKeys());
    }
}
