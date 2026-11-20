package tetris.ui.theme;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import tetris.component.block.BlockType;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.item.Cell;
import tetris.feature.item.ItemType;
import tetris.feature.score.ScoreEntry;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.ui.GameOverPanel;
import tetris.ui.GamePanel;
import tetris.ui.MainMenuPanel;
import tetris.ui.MenuItem;
import tetris.ui.NameInputPanel;
import tetris.ui.PausePanel;
import tetris.ui.ScoreBoardPanel;
import tetris.ui.SettingPanel;
import tetris.ui.versus.BattleKeyMap;
import tetris.ui.versus.BattleModes;
import tetris.ui.versus.BattlePanel;
import tetris.ui.versus.FakeBattleClient;

/** 화면 개편 검수용 오프스크린 캡처. 테스트 스위트에는 넣지 않는다 */
public final class OverhaulCapture {
    private static final Path OUT = Path.of("C:/Users/asus/Desktop/SE-tetris/assets-work/overhaul");

    private OverhaulCapture() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        int cell = ScreenSize.MEDIUM.cellPixels();
        var size = GamePanel.preferredSizeFor(cell);
        capture("menu", menu(null, List.of("게임 시작 (솔로)", "아이템 모드", "멀티플레이", "설정", "스코어보드", "게임 종료")), size.width, size.height);
        capture("solo-menu", menu("솔로", List.of("일반 게임", "솔로 레이드", "메인 메뉴")), size.width, size.height);
        capture("multi-menu", menu("멀티", List.of("로컬 일반 대전", "로컬 아이템 대전", "로컬 시간제한 대전", "온라인 대전 (LAN·인터넷)", "협동 레이드 (2인)", "메인 메뉴")), size.width, size.height);
        capture("settings", settings(), size.width, size.height);
        capture("scoreboard", scoreboard(), size.width, size.height);
        capture("game-normal", game(cell, false, false), size.width, size.height);
        capture("game-colorblind", game(cell, true, false), size.width, size.height);
        capture("game-item", game(cell, false, true), size.width, size.height);
        capture("pause", paused(cell), size.width, size.height);
        capture("game-over", gameOver(size.width, size.height), size.width, size.height);
        capture("name-entry", nameEntry(size.width, size.height), size.width, size.height);
        capture("versus", battle(false), 960, 720);
        capture("versus-timed", battle(true), 960, 720);
        int small = ScreenSize.SMALL.cellPixels();
        int large = ScreenSize.LARGE.cellPixels();
        var smallSize = GamePanel.preferredSizeFor(small);
        var largeSize = GamePanel.preferredSizeFor(large);
        capture("game-small", game(small, false, false), smallSize.width, smallSize.height);
        capture("game-large", game(large, false, false), largeSize.width, largeSize.height);
        System.out.println("wrote " + OUT);
    }

    private static MainMenuPanel menu(String title, List<String> labels) {
        List<MenuItem> items = labels.stream().map(label -> new MenuItem(label, () -> {})).toList();
        return title == null
                ? new MainMenuPanel(items)
                : new MainMenuPanel(items, () -> {}, title, () -> false);
    }

    private static SettingPanel settings() {
        return new SettingPanel(new Settings(), () -> true, () -> false, () -> {});
    }

    private static ScoreBoardPanel scoreboard() {
        ScoreBoardPanel panel = new ScoreBoardPanel(() -> {}, () -> {});
        panel.show(List.of(
                new ScoreEntry("홍길동", 128450, 7, 42, java.time.Instant.EPOCH),
                new ScoreEntry("Kim", 80400, 5, 30, java.time.Instant.EPOCH)), 0, false);
        return panel;
    }

    private static GamePanel game(int cell, boolean colorBlind, boolean item) {
        Settings settings = new Settings();
        settings.setColorBlindMode(colorBlind);
        GamePanel panel = new GamePanel(settings, new PausePanel(() -> {}, () -> {}, () -> {}), ignored -> {});
        panel.setCellSize(cell);
        int[][] cells = new int[20][10];
        cells[18][0] = BlockType.S.cellValue();
        cells[18][1] = BlockType.S.cellValue();
        cells[18][2] = BlockType.O.cellValue();
        cells[19][0] = BlockType.I.cellValue();
        cells[19][1] = BlockType.J.cellValue();
        cells[19][2] = BlockType.L.cellValue();
        cells[19][3] = BlockType.O.cellValue();
        cells[19][4] = BlockType.T.cellValue();
        cells[19][5] = BlockType.Z.cellValue();
        TBlock current = new TBlock();
        current.setPosition(2, 3);
        OBlock next = new OBlock();
        Map<Cell, ItemType> locked = Map.of();
        if (item) {
            next.attachItem(0, 0, ItemType.LINE_CLEAR);
            locked = Map.of(new Cell(19, 4), ItemType.DOUBLE_SCORE);
        }
        panel.onChanged(new GameSnapshot(cells, current, next, 128450, 7, 42, GameState.PLAYING,
                item ? GameMode.ITEM : GameMode.CLASSIC, Difficulty.NORMAL, List.of(), item ? 6 : 0, List.of(), locked));
        return panel;
    }

    private static GamePanel paused(int cell) {
        GamePanel panel = game(cell, false, false);
        panel.onChanged(new GameSnapshot(filled(), null, new OBlock(), 128450, 7, 42, GameState.PAUSED));
        return panel;
    }

    private static int[][] filled() {
        int[][] cells = new int[20][10];
        cells[19][4] = BlockType.T.cellValue();
        return cells;
    }

    private static GameOverPanel gameOver(int width, int height) {
        GameOverPanel panel = new GameOverPanel(() -> {});
        panel.show(new GameSnapshot(filled(), null, null, 128450, 7, 42, GameState.GAME_OVER));
        panel.setSize(width, height);
        return panel;
    }

    private static NameInputPanel nameEntry(int width, int height) {
        NameInputPanel panel = new NameInputPanel(name -> {});
        panel.prepare(128450);
        panel.setSize(width, height);
        panel.doLayout();
        return panel;
    }

    private static BattlePanel battle(boolean timed) {
        BattlePanel panel = new BattlePanel(false, new BattleKeyMap().guide(),
                BattleModes.label(timed ? BattleMode.TIMED : BattleMode.CLASSIC),
                timed ? BattleMode.TIMED : BattleMode.CLASSIC, null);
        MatchSnapshot sample = FakeBattleClient.sample();
        panel.setSnapshot(new MatchSnapshot(sample.matchId(), 1, sample.one(), sample.two(),
                timed ? 192_000 : -1, MatchPhase.PLAYING, null));
        return panel;
    }

    private static void capture(String name, JComponent panel, int width, int height) throws Exception {
        panel.setSize(width, height);
        panel.doLayout();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();
        ImageIO.write(image, "png", OUT.resolve(name + ".png").toFile());
    }
}
