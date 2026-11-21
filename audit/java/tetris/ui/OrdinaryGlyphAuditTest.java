package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.BitSet;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tetris.component.block.BlockType;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.setting.Settings;

/** 챌린지 빌드: 일반 칸은 블록마다 고유 글자(I/J/L/O/S/T/Z) */
class OrdinaryGlyphAuditTest {
    private static final int CELL = 40;
    private static final int ROW = 10;
    private static final int COLUMN = 4;

    static Stream<Arguments> glyphs() {
        return Arrays.stream(BlockType.ordinary()).flatMap(type -> Stream.of(
                Arguments.of(type, false), Arguments.of(type, true)));
    }

    @ParameterizedTest
    @MethodSource("glyphs")
    void ordinaryLockedGlyphMatchesItsOwnLetter(BlockType type, boolean colorBlind) throws Exception {
        Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                BitSet expected = referenceMask(type, colorBlind);
                BitSet actual = glyphMask(type, colorBlind);
                assertFalse(expected.isEmpty(), type + " reference glyph must contain foreground pixels");
                assertEquals(expected, actual, type + " must display " + type.letter() + "; colorBlind=" + colorBlind);
            } catch (Throwable thrown) {
                failure[0] = thrown;
            }
        });
        if (failure[0] != null) throw new AssertionError("Ordinary glyph requirement failed", failure[0]);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryLettersDifferFromEachOther(boolean colorBlind) throws Exception {
        Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                BlockType[] types = BlockType.ordinary();
                BitSet[] masks = new BitSet[types.length];
                for (int i = 0; i < types.length; i++) masks[i] = glyphMask(types[i], colorBlind);
                for (int i = 0; i < types.length; i++) {
                    for (int j = i + 1; j < types.length; j++) {
                        assertNotEquals(masks[i], masks[j], types[i] + " and " + types[j] + " share a glyph");
                    }
                }
            } catch (Throwable thrown) {
                failure[0] = thrown;
            }
        });
        if (failure[0] != null) throw new AssertionError("Ordinary glyph requirement failed", failure[0]);
    }

    private static BitSet glyphMask(BlockType type, boolean colorBlind) {
        Settings settings = new Settings();
        settings.setColorBlindMode(colorBlind);
        GamePanel panel = new GamePanel(settings, new PausePanel(() -> {}, () -> {}, () -> {}), ignored -> {});
        panel.setCellSize(CELL);
        panel.setSize(GamePanel.preferredSizeFor(CELL));
        int[][] cells = new int[20][10];
        cells[ROW][COLUMN] = type.cellValue();
        panel.onChanged(new GameSnapshot(cells, null, null, 0, 1, 0, GameState.PLAYING));
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        BlockPalette palette = new BlockPalette();
        Color foreground = palette.textColor(palette.color(type, colorBlind));
        java.awt.Rectangle cell = panel.boardCellBounds(ROW, COLUMN);
        return mask(image, cell.x, cell.y, foreground);
    }

    /** 같은 크기·같은 글꼴로 그 블록 글자만 그린 기준 */
    private static BitSet referenceMask(BlockType type, boolean colorBlind) {
        BlockPalette palette = new BlockPalette();
        Color fill = palette.color(type, colorBlind);
        Color foreground = palette.textColor(fill);
        BufferedImage image = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRect(0, 0, CELL, CELL);
            g.setFont(Theme.blockLetter(CELL * 3 / 5));
            g.setColor(foreground);
            String letter = String.valueOf(type.letter());
            int textX = (CELL - g.getFontMetrics().stringWidth(letter)) / 2;
            int textY = (CELL - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
            g.drawString(letter, textX, textY);
        } finally {
            g.dispose();
        }
        return mask(image, 0, 0, foreground);
    }

    private static BitSet mask(BufferedImage image, int originX, int originY, Color foreground) {
        BitSet mask = new BitSet(CELL * CELL);
        int ink = foreground.getRGB();
        for (int y = 2; y < CELL - 2; y++) {
            for (int x = 2; x < CELL - 2; x++) {
                if (image.getRGB(originX + x, originY + y) == ink) mask.set(y * CELL + x);
            }
        }
        return mask;
    }
}
