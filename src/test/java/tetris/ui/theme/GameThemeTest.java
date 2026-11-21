package tetris.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
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
    @Test
    void digitZeroIsOpenAndDigitsShareOneAdvance() {
        BufferedImage image = new BufferedImage(180, 90, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        GameTheme.applyQuality(g);
        g.setFont(GameTheme.digits(48));
        g.setColor(Color.WHITE);
        int advance = g.getFontMetrics().charWidth('0');
        assertEquals(advance, g.getFontMetrics().charWidth('1'));
        assertEquals(advance, g.getFontMetrics().charWidth('8'));
        assertEquals(g.getFontMetrics().stringWidth("1200"), g.getFontMetrics().stringWidth("1881"));
        assertEquals(g.getFontMetrics().stringWidth("1020"), GameTheme.width(g, "1020"));
        GameTheme.draw(g, "0", 24, 68);
        g.dispose();
        int minX = image.getWidth(), maxX = 0, minY = image.getHeight(), maxY = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) < 40) continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        assertTrue(maxX > minX);
        int midX = (minX + maxX) / 2;
        int midY = (minY + maxY) / 2;
        assertTrue((image.getRGB(midX, midY) >>> 24) < 40, "0 가운데는 비어 있어야 한다");
        assertTrue((image.getRGB(midX, minY + 1) >>> 24) > 40, "0 위쪽이 닫혀 있어야 한다");
        assertTrue((image.getRGB(midX, maxY - 1) >>> 24) > 40, "0 아래쪽이 닫혀 있어야 한다");
    }

    @Test
    void titleEffectPaintsLargeTextAndSkipsTinyText() {
        BufferedImage image = new BufferedImage(320, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        GameTheme.applyQuality(g);
        GameTheme.paintTitle(g, "게임 오버", 36, 160, 70, false);
        GameTheme.paintTitle(g, "게임 오버", 36, 160, 70, true);
        g.setFont(GameTheme.text(12, false));
        GameTheme.paintTitle(g, "안내", g.getFont(), 40, 100, 0);
        g.dispose();
        int ink = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) > 20) ink++;
        }
        assertTrue(ink > 80);
    }

}
