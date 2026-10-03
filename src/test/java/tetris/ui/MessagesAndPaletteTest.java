package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tetris.component.block.BlockType;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;

/** KEY-6(Key Guide는 현재 키에서 생성), RND-4~6(팔레트) */
public class MessagesAndPaletteTest {
    private final BlockPalette palette = new BlockPalette();

    @Test
    void gameKeyGuideFollowsCurrentKeys() {
        KeyMap keyMap = new KeyMap();
        String before = Messages.keyGuide(MainFrame.Screen.GAME, keyMap);
        assertTrue(before.contains("←/→"));
        assertTrue(before.contains(KeyEvent.getKeyText(KeyEvent.VK_SPACE)));

        keyMap.assign(GameAction.HARD_DROP, KeyEvent.VK_X);
        String after = Messages.keyGuide(MainFrame.Screen.GAME, keyMap);
        assertTrue(after.contains("X  " + Messages.actionName(GameAction.HARD_DROP)));
        assertFalse(after.contains(KeyEvent.getKeyText(KeyEvent.VK_SPACE)));
    }

    @Test
    void pauseKeyGuideUsesPauseKey() {
        KeyMap keyMap = new KeyMap();
        keyMap.assign(GameAction.PAUSE, KeyEvent.VK_K);
        assertTrue(Messages.pauseKeyGuide(keyMap).contains("K/Esc"));
    }

    @Test
    void everyScreenHasKeyGuide() {
        for (MainFrame.Screen screen : MainFrame.Screen.values()) {
            assertFalse(Messages.keyGuide(screen, new KeyMap()).isBlank(), screen.name());
        }
    }

    @Test
    void everyActionHasName() {
        for (GameAction action : GameAction.values()) assertFalse(Messages.actionName(action).isBlank());
    }

    @Test
    void unknownKeyFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> Messages.get("no.such.key"));
    }

    @Test
    void sevenDistinctColorsInEachMode() {
        for (boolean colorBlind : new boolean[] {false, true}) {
            Set<Color> colors = new HashSet<>();
            for (BlockType type : BlockType.values()) colors.add(palette.color(type, colorBlind));
            assertEquals(7, colors.size());
        }
        assertNotEquals(palette.color(BlockType.S, false), palette.color(BlockType.S, true));
    }

    @Test
    void textColorContrastsWithBackground() {
        assertEquals(Color.BLACK, palette.textColor(palette.color(BlockType.O, false))); // 노랑
        assertEquals(Color.WHITE, palette.textColor(palette.color(BlockType.J, false))); // 파랑
        assertEquals(Color.WHITE, palette.textColor(palette.color(BlockType.Z, true)));
    }
}
