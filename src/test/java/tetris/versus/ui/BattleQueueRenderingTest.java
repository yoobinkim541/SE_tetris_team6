package tetris.versus.ui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import tetris.feature.versus.api.*;
import tetris.ui.versus.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleQueueRenderingTest {
    @Test void queuesPreserveEveryHoleAndRowOrderForBothPlayersAtEveryCapacity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (boolean colorBlind : new boolean[]{false, true}) {
                for (int count = 0; count <= VersusRules.MAX_PENDING_ROWS; count++) {
                    var panel = new BattlePanel(colorBlind);
                    var one = player(count, 0); var two = player(count, 3);
                    panel.setSnapshot(new MatchSnapshot("queue", 0, one, two, -1, MatchPhase.PLAYING, null));
                    panel.setSize(960, 720);
                    var image = new BufferedImage(960, 720, BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics(); panel.paint(graphics); graphics.dispose();
                    for (int player = 0; player < 2; player++) {
                        int x = (player == 0 ? 28 : 500) + 288;
                        for (int row = 0; row < VersusRules.MAX_PENDING_ROWS; row++) {
                            int hole = (row + (player == 0 ? 0 : 3)) % VersusRules.BOARD_COLS;
                            for (int col = 0; col < VersusRules.BOARD_COLS; col++) {
                                boolean empty = row >= count || col == hole;
                                assertEquals(empty ? 0x262A33 : 0x929AA5,
                                    image.getRGB(x + col * 14 + 2, 280 + row * 16 + 1) & 0xffffff,
                                    "player=" + player + " count=" + count + " row=" + row + " col=" + col);
                            }
                        }
                    }
                }
            }
        });
    }

    @Test void weightAndAttackHaveDistinctPatternsEvenAtSevenPixelCellSize() {
        for (boolean colorBlind : new boolean[]{false, true}) {
            for (int size : new int[]{7, 14, 27}) {
                var image = new BufferedImage(size * 2, size, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics(); var renderer = new BoardRenderer(colorBlind);
                assertDoesNotThrow(() -> renderer.paint(g, new int[][]{{8, VersusRules.ATTACK_CELL}}, 0, 0, size));
                g.dispose();
                boolean patternDiffers = false;
                for (int y = 1; y < size - 1; y++) for (int x = 1; x < size - 1; x++) {
                    // Pattern assertion remains useful even if the two gray fill colors converge.
                    boolean firstBlack = (image.getRGB(x, y) & 0xffffff) == 0;
                    boolean attackBlack = (image.getRGB(x + size, y) & 0xffffff) == 0;
                    patternDiffers |= firstBlack != attackBlack;
                }
                assertTrue(patternDiffers, "distinct W/X pattern at size " + size);
            }
        }
    }

    private static MatchSnapshot.PlayerView player(int count, int offset) {
        List<int[]> rows = new ArrayList<>();
        for (int r = 0; r < count; r++) {
            int[] row = new int[VersusRules.BOARD_COLS]; java.util.Arrays.fill(row, VersusRules.ATTACK_CELL);
            row[(r + offset) % row.length] = 0; rows.add(row);
        }
        return new MatchSnapshot.PlayerView(new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS], new int[0][], 0, rows);
    }
}
