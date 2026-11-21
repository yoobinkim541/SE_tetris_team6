package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.rule.TSpinType;
import tetris.feature.save.JsonSettingsRepository;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.ui.fx.GameSounds;
import tetris.ui.fx.SoundId;

/** 효과음 선택과 설정 파일의 sfx·bgm. 재생 장치는 녹음기로 바꿔 끼운다 */
class GameSoundsTest {
    @Test
    void battleTSpinPlaysOncePerLock() {
        Tape tape = new Tape();
        GameSounds sounds = new GameSounds(tape);
        sounds.onBattle(battle(1, 0), battle(2, 1), null);
        assertEquals(1, count(tape, SoundId.TSPIN));
        sounds.onBattle(battle(2, 1), battle(3, 2), null);
        assertEquals(2, count(tape, SoundId.TSPIN));
        sounds.onBattle(battle(3, 2), battle(4, 2), null);
        assertEquals(2, count(tape, SoundId.TSPIN));
    }

    @Test
    void fourLineClearPlaysTetris() {
        Tape tape = new Tape();
        GameSounds sounds = new GameSounds(tape);
        sounds.onSingle(single(0));
        sounds.onSingle(single(4));
        assertTrue(tape.played.contains(SoundId.TETRIS));
    }

    @Test
    void bossDefeatedPlaysBossDefeat() {
        Tape tape = new Tape();
        GameSounds sounds = new GameSounds(tape);
        sounds.onRaid(raid(new RaidEvent(RaidEvent.Type.BOSS_DEFEATED, -1, 0)));
        assertTrue(tape.played.contains(SoundId.BOSS_DEFEAT));
    }

    @Test
    void sfxOffPlaysNothing() {
        Tape tape = new Tape();
        GameSounds sounds = new GameSounds(tape);
        sounds.setSfxEnabled(false);
        sounds.onSingle(single(0));
        sounds.onSingle(single(4));
        sounds.onRaid(raid(new RaidEvent(RaidEvent.Type.BOSS_DEFEATED, -1, 0)));
        sounds.menuSelect();
        assertTrue(tape.played.isEmpty());
    }

    @Test
    void bgmSwitchesFromRaidBackToMenu() {
        Tape tape = new Tape();
        GameSounds sounds = new GameSounds(tape);
        sounds.enterRaid();
        assertEquals(SoundId.BGM_RAID, tape.looping());
        sounds.enterMenu();
        assertEquals(SoundId.BGM_MENU, tape.looping());
        sounds.enterGame();
        assertEquals(null, tape.looping());
    }

    @Test
    void settingsRoundTripAndLegacyAbsentStayOn() throws Exception {
        Path file = Files.createTempFile("sfx-settings", ".json");
        Path legacy = Files.createTempFile("sfx-legacy", ".json");
        try {
            Settings off = new Settings();
            off.setSfxEnabled(false);
            off.setBgmEnabled(false);
            assertTrue(new JsonSettingsRepository(file).save(off));
            Settings loaded = new JsonSettingsRepository(file).load().value();
            assertFalse(loaded.isSfxEnabled());
            assertFalse(loaded.isBgmEnabled());

            loaded.resetToDefaults();
            assertTrue(new JsonSettingsRepository(file).save(loaded));
            Settings reset = new JsonSettingsRepository(file).load().value();
            assertTrue(reset.isSfxEnabled());
            assertTrue(reset.isBgmEnabled());

            Files.writeString(legacy, "{\"screenSize\":\"SMALL\"}");
            var old = new JsonSettingsRepository(legacy).load();
            assertTrue(old.warnings().isEmpty());
            assertTrue(old.value().isSfxEnabled());
            assertTrue(old.value().isBgmEnabled());
            assertEquals(tetris.feature.setting.ScreenSize.SMALL, old.value().getScreenSize());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(legacy);
        }
    }

    @Test
    void invalidSfxKeepsDefaultOnAndWarns() throws Exception {
        Path file = Files.createTempFile("sfx-bad", ".json");
        try {
            Files.writeString(file, "{\"sfx\":\"yes\",\"bgm\":1,\"colorBlindMode\":true}");
            var loaded = new JsonSettingsRepository(file).load();
            assertTrue(loaded.value().isSfxEnabled());
            assertTrue(loaded.value().isBgmEnabled());
            assertTrue(loaded.value().isColorBlindMode());
            assertEquals(2, loaded.warnings().size());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void settingsRowTogglesSfxImmediately() {
        Settings settings = new Settings();
        int[] saves = {0};
        SettingPanel panel = new SettingPanel(settings, () -> { saves[0]++; return true; }, () -> true, () -> { });
        panel.moveSelection(6);
        panel.activateSelected();
        assertFalse(settings.isSfxEnabled());
        assertEquals(1, saves[0]);
        panel.moveSelection(1);
        panel.activateSelected();
        assertFalse(settings.isBgmEnabled());
        assertEquals("효과음", Messages.get("settings.sfx"));
        assertEquals("배경음", Messages.get("settings.bgm"));
        assertEquals("켬", Messages.get("settings.enabled"));
        assertEquals("끔", Messages.get("settings.disabled"));
    }

    private static MatchSnapshot battle(long revision, int tSpinLocks) {
        int[][] cells = new int[20][10];
        var one = new MatchSnapshot.PlayerView(cells, new int[0][], 0, List.of(), new int[20][10],
                new int[0][], new int[20][10], List.of(), tSpinLocks);
        var two = new MatchSnapshot.PlayerView(cells, new int[0][], 0, List.of());
        return new MatchSnapshot("battle", revision, one, two, -1,
                tetris.feature.versus.api.MatchPhase.PLAYING, null);
    }

    private static int count(Tape tape, SoundId id) {
        int n = 0;
        for (SoundId played : tape.played) if (played == id) n++;
        return n;
    }

    private static GameSnapshot single(int lines) {
        return new GameSnapshot(new int[0][], null, null, 0, 1, lines, GameState.PLAYING,
                GameMode.CLASSIC, Difficulty.NORMAL, List.of(), 0, List.of(), Map.of(),
                TSpinType.NONE, null, List.of(), null);
    }
    private static RaidSnapshot raid(RaidEvent event) {
        MatchSnapshot.PlayerView board = new MatchSnapshot.PlayerView(new int[20][10], new int[0][], 0, List.of());
        RaidSnapshot.PlayerView player = new RaidSnapshot.PlayerView(board, true, new int[20][10], List.of(), 0, 8, 0);
        RaidSnapshot.BossView boss = new RaidSnapshot.BossView(
                BossId.JELLY_KING, 1, 10, 100, PatternId.CLOUD, 0, 4, new int[0], -1);
        return new RaidSnapshot("raid", 1, RaidMode.SOLO, RaidPhase.PLAYING, boss,
                List.of(player), 0, 0, 0, null, List.of(event), null);
    }

    private static final class Tape implements GameSounds.Sink {
        private final List<SoundId> played = new ArrayList<>();
        private SoundId loop;

        @Override public void play(SoundId id) { played.add(id); }
        @Override public void loop(SoundId id) { loop = id; }
        @Override public void stopLoop() { loop = null; }
        @Override public SoundId looping() { return loop; }
    }
}
