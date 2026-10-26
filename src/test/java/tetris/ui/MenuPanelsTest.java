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
    private final SettingPanel settingPanel =
            new SettingPanel(settings, () -> { saves++; return true; }, () -> true, () -> calls.add("back"));

    @Test
    void screenSizeCyclesAndSaves() {
        settingPanel.activateSelected(); // Screen Size
        assertEquals(ScreenSize.LARGE, settings.getScreenSize());
        settingPanel.activateSelected();
        assertEquals(ScreenSize.SMALL, settings.getScreenSize());
        assertEquals(2, saves);
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
    void keyCaptureAppliesValidKey() {
        settingPanel.openControlKeys();
        settingPanel.moveSelection(4); // HARD_DROP
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
