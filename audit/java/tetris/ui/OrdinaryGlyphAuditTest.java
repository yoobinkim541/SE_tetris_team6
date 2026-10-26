package tetris.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.BitSet;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tetris.component.block.BlockType;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.setting.Settings;

/** Req2 ordinary cells use the same O glyph, while retaining their palette colors. */
class OrdinaryGlyphAuditTest {
    private static final int CELL = 40;
    private static final int PADDING = CELL / 2;
    private static final int ROW = 10;
    private static final int COLUMN = 4;

    static Stream<Arguments> glyphs() {
        return Arrays.stream(BlockType.ordinary()).flatMap(type -> Stream.of(
                Arguments.of(type, false), Arguments.of(type, true)));
    }

    @ParameterizedTest
    @MethodSource("glyphs")
    void ordinaryLockedGlyphMatchesOInBothPalettes(BlockType type, boolean colorBlind) throws Exception {
        Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                    BitSet expected = glyphMask(BlockType.O, colorBlind);
                    BitSet actual = glyphMask(type, colorBlind);
                    assertFalse(expected.isEmpty(), "O reference glyph must contain foreground pixels");
                    assertFalse(actual.isEmpty(), "Actual ordinary glyph must contain foreground pixels");
                    assertEquals(expected, actual,
                            type + " must display O; colorBlind=" + colorBlind);
            } catch (Throwable thrown) { failure[0] = thrown; }
        });
        if (failure[0] != null) throw new AssertionError("Ordinary glyph requirement failed", failure[0]);
    }

    private static BitSet glyphMask(BlockType type, boolean colorBlind) {
        Settings settings = new Settings();
        settings.setColorBlindMode(colorBlind);
        GamePanel panel = new GamePanel(settings, new PausePanel(() -> {}, () -> {}, () -> {}),
                ignored -> {});
        panel.setCellSize(CELL);
        panel.setSize(GamePanel.preferredSizeFor(CELL));
        int[][] cells = new int[20][10];
        cells[ROW][COLUMN] = type.cellValue();
        panel.onChanged(new GameSnapshot(cells, null, null, 0, 1, 0, GameState.PLAYING));
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { panel.paint(graphics); }
        finally { graphics.dispose(); }

        BlockPalette palette = new BlockPalette();
        Color foreground = palette.textColor(palette.color(type, colorBlind));
        BitSet mask = new BitSet(CELL * CELL);
        // Exclude the cell outline and grid; only opaque glyph foreground belongs in the mask.
        for (int y = 2; y < CELL - 2; y++) {
            for (int x = 2; x < CELL - 2; x++) {
                if (image.getRGB(PADDING + COLUMN * CELL + x, PADDING + ROW * CELL + y)
                        == foreground.getRGB()) mask.set(y * CELL + x);
            }
        }
        return mask;
    }
}
