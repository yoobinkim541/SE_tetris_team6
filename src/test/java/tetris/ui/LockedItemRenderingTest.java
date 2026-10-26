package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.item.Cell;
import tetris.feature.item.ItemType;
import tetris.feature.setting.Settings;

/** 보드에 고정된 칸에 아이템 문자(D·S 등)가 남아 있으면 아이템 모양(흰 바탕 검은 글자)으로 그리는지 확인한다 */
public class LockedItemRenderingTest {
    private static BufferedImage render(int cell, Map<Cell, ItemType> lockedItems) {
        int[][] cells = new int[20][10];
        cells[19][2] = 4; // O블록 칸
        GamePanel panel = new GamePanel(new Settings(), new PausePanel(() -> {}, () -> {}, () -> {}), s -> {});
        panel.setCellSize(cell);
        panel.setSize(GamePanel.preferredSizeFor(cell));
        panel.onChanged(new GameSnapshot(cells, null, null, 1234, 3, 12, GameState.PLAYING, GameMode.ITEM,
                Difficulty.NORMAL, List.of(), 8, List.of(), lockedItems));
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();
        return image;
    }

    private static BufferedImage[] onEdt(int cell, Map<Cell, ItemType> locked) throws Exception {
        BufferedImage[] result = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> result[0] = render(cell, locked));
        return result;
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 30, 40})
    void 고정된_칸에_남은_아이템_문자는_흰_바탕으로_그린다(int cell) throws Exception {
        BufferedImage plain = onEdt(cell, Map.of())[0];
        BufferedImage marked = onEdt(cell, Map.of(new Cell(19, 2), ItemType.DOUBLE_SCORE))[0];
        int padding = cell / 2;
        int cx = padding + 2 * cell + 2, cy = padding + 19 * cell + 2; // (19, 2) 칸의 왼쪽 위 안쪽

        assertEquals(Color.WHITE.getRGB(), marked.getRGB(cx, cy), "아이템 문자 칸은 흰 바탕");
        assertNotEquals(Color.WHITE.getRGB(), plain.getRGB(cx, cy), "일반 칸은 블록 색");
    }
}
