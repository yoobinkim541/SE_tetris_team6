package tetris.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import tetris.ui.fx.AssetStore;

/** 서체 로드와 9등분 캐시. 그림이 없어도 도형으로 그릴 수 있어야 한다 */
class GameThemeTest {
    @Test
    void fontsLoadOrFallBackToARealFace() {
        assertNotNull(GameTheme.text(18, true));
        assertNotNull(GameTheme.digits(22));
        assertEquals(18, GameTheme.text(18, false).getSize());
        assertEquals(22, GameTheme.digits(22).getSize());
        assertTrue(GameTheme.title(720).getSize() >= 18);
        assertTrue(GameTheme.doHyeonLoaded(), "Do Hyeon should ship in resources/fonts");
        assertTrue(GameTheme.orbitronLoaded(), "Orbitron should ship in resources/fonts");
    }

    @Test
    void nineSliceReusesTheSameScaledImage() {
        NineSlice slice = new NineSlice("/assets/ui/frame-cyan.png", 64, NineSlice.Axis.NINE, new AssetStore(GameThemeTest.class));
        if (!slice.available()) return; // 그림이 아직 없으면 도형으로 그린다
        BufferedImage first = slice.scaled(180, 90);
        BufferedImage second = slice.scaled(180, 90);
        assertSame(first, second);
    }

    @Test
    void missingSliceDoesNotThrowWhenPainting() {
        NineSlice slice = new NineSlice("/assets/ui/does-not-exist.png", 16, NineSlice.Axis.HORIZONTAL, new AssetStore(GameThemeTest.class));
        assertTrue(slice.scaled(40, 20) == null);
    }
}
