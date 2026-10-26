package tetris.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tetris.component.block.*;
import tetris.feature.data.Board;
import tetris.feature.game.*;
import tetris.feature.item.*;
import tetris.feature.setting.Settings;

class RequirementsVisualAuditTest {
    private static final int CELL = 30, PADDING = 15;

    private static GameSnapshot snapshot(int[][] cells, Block current, Block next, GameState state,
                                         GameMode mode, Difficulty difficulty, int countdown,
                                         List<Cell> clearing, List<ActiveEffect> effects) {
        return new GameSnapshot(cells, current, next, 1234, 3, 12, state, mode, difficulty, clearing, countdown, effects);
    }

    private static BufferedImage render(GameSnapshot snapshot, boolean colorBlind) {
        if (!SwingUtilities.isEventDispatchThread()) {
            BufferedImage[] result = new BufferedImage[1];
            try { SwingUtilities.invokeAndWait(() -> result[0] = render(snapshot, colorBlind)); }
            catch (Exception error) { throw new AssertionError(error); }
            return result[0];
        }
        Settings settings = new Settings();
        settings.setColorBlindMode(colorBlind);
        GamePanel panel = new GamePanel(settings, new PausePanel(() -> {}, () -> {}, () -> {}), value -> {});
        panel.setCellSize(CELL);
        panel.onChanged(snapshot);
        Dimension size = GamePanel.preferredSizeFor(CELL);
        panel.setSize(size);
        panel.doLayout();
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static int[][] empty() { return new int[Board.HEIGHT][Board.WIDTH]; }
    private static int pixel(BufferedImage image, int row, int col) {
        return image.getRGB(PADDING + col * CELL + 4, PADDING + row * CELL + 4);
    }

    static Stream<Arguments> items() {
        return Arrays.stream(ItemType.values()).flatMap(item -> Stream.of(
                Arguments.of(item, false), Arguments.of(item, true)));
    }

    @Test
    void clearingCellsTurnWhiteEvenWhenThereIsNoCurrentBlock() {
        int[][] cells = empty();
        cells[19][0] = BlockType.I.cellValue();
        cells[19][1] = BlockType.Z.cellValue();
        var state = snapshot(cells, null, null, GameState.CLEARING, GameMode.CLASSIC, Difficulty.NORMAL,
                0, List.of(new Cell(19, 0)), List.of());
        BufferedImage image = render(state, false);
        assertEquals(new BlockPalette().color(BlockType.Z, false).getRGB(), pixel(image, 19, 1));
        assertEquals(Color.WHITE.getRGB(), pixel(image, 19, 0), "삭제 대상만 흰색이어야 한다");
    }

    @ParameterizedTest @MethodSource("items")
    void currentItemCellUsesWhiteBackgroundIndependentOfPalette(ItemType item, boolean colorBlind) {
        OBlock block = new OBlock();
        block.setPosition(5, 4);
        block.attachItem(0, 0, item);
        BufferedImage image = render(snapshot(empty(), block, null, GameState.PLAYING, GameMode.ITEM,
                Difficulty.NORMAL, 10, List.of(), List.of()), colorBlind);
        assertEquals(Color.WHITE.getRGB(), pixel(image, 5, 4));
        assertEquals(new BlockPalette().color(BlockType.O, colorBlind).getRGB(), pixel(image, 5, 5));
    }

    @ParameterizedTest @MethodSource("items")
    void nextItemCellUsesWhiteBackground(ItemType item, boolean colorBlind) {
        OBlock next = new OBlock();
        next.attachItem(0, 0, item);
        BufferedImage image = render(snapshot(empty(), null, next, GameState.PLAYING, GameMode.ITEM,
                Difficulty.NORMAL, 10, List.of(), List.of()), colorBlind);
        int label = CELL * 9 / 20;
        int boxX = PADDING * 2 + Board.WIDTH * CELL;
        int boxY = PADDING + label + label / 2;
        int blockX = boxX + (CELL * 6 - CELL * 2) / 2;
        int blockY = boxY + (CELL * 4 - CELL * 2) / 2;
        assertEquals(Color.WHITE.getRGB(), image.getRGB(blockX + 4, blockY + 4));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void weightCellsAlreadyRenderGrayInBothPalettes(boolean colorBlind) {
        int[][] cells = empty();
        cells[19][0] = BlockType.W.cellValue();
        WeightBlock current = new WeightBlock();
        current.setPosition(5, 3);
        BufferedImage image = render(snapshot(cells, current, new WeightBlock(), GameState.PLAYING,
                GameMode.ITEM, Difficulty.NORMAL, 10, List.of(), List.of()), colorBlind);
        assertEquals(new Color(0xBDBDBD).getRGB(), pixel(image, 19, 0));
        assertEquals(new Color(0xBDBDBD).getRGB(), pixel(image, 5, 4));
    }

    @ParameterizedTest @ValueSource(strings = {"mode", "difficulty", "nextItem", "doubleScore", "slow"})
    void changingStatusInformationChangesTheRenderedInformation(String field) {
        GameSnapshot first;
        GameSnapshot second;
        if (field.equals("mode")) {
            first = snapshot(empty(), null, null, GameState.PLAYING, GameMode.CLASSIC, Difficulty.NORMAL, 0, List.of(), List.of());
            second = snapshot(empty(), null, null, GameState.PLAYING, GameMode.ITEM, Difficulty.NORMAL, 10, List.of(), List.of());
        } else if (field.equals("difficulty")) {
            first = snapshot(empty(), null, null, GameState.PLAYING, GameMode.CLASSIC, Difficulty.EASY, 0, List.of(), List.of());
            second = snapshot(empty(), null, null, GameState.PLAYING, GameMode.CLASSIC, Difficulty.HARD, 0, List.of(), List.of());
        } else {
            ItemType effect = field.equals("doubleScore") ? ItemType.DOUBLE_SCORE : ItemType.SLOW;
            List<ActiveEffect> a = field.equals("nextItem") ? List.of() : List.of(new ActiveEffect(effect, 10));
            List<ActiveEffect> b = field.equals("nextItem") ? List.of() : List.of(new ActiveEffect(effect, 9));
            first = snapshot(empty(), null, null, GameState.PLAYING, GameMode.ITEM, Difficulty.NORMAL, 10, List.of(), a);
            second = snapshot(empty(), null, null, GameState.PLAYING, GameMode.ITEM, Difficulty.NORMAL,
                    field.equals("nextItem") ? 9 : 10, List.of(), b);
        }
        BufferedImage before = render(first, false), after = render(second, false);
        assertFalse(Arrays.equals(before.getRGB(0, 0, before.getWidth(), before.getHeight(), null, 0, before.getWidth()),
                after.getRGB(0, 0, after.getWidth(), after.getHeight(), null, 0, after.getWidth())), field + " 표시 누락");
    }

    @ParameterizedTest @ValueSource(ints = {KeyEvent.VK_ENTER, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT})
    void settingsDifficultyRowCyclesAndSavesImmediately(int key) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        Settings settings = new Settings();
        int[] saves = {0};
        SettingPanel panel = new SettingPanel(settings, () -> { saves[0]++; return true; }, () -> true, () -> {});
        panel.moveSelection(1); // Screen Size 다음 Difficulty
        Object name = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(key, 0));
        panel.getActionMap().get(name).actionPerformed(null);
        assertEquals(key == KeyEvent.VK_LEFT ? Difficulty.EASY : Difficulty.HARD, settings.getDifficulty());
        assertEquals(1, saves[0]);
        });
    }
}
