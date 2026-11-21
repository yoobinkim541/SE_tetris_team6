package tetris.ui.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import tetris.feature.raid.api.BossId;

/** 축소 캐시는 장치 픽셀 키·화면 이탈·현재 보스만 유지한다 */
class AssetStoreMemoryTest {
    @Test
    void devicePixelsIncludeTransformScale() {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.scale(1.5, 1.5);
        assertEquals(15, AssetStore.devicePixels(g, 10));
        g.dispose();
        assertEquals(10, AssetStore.devicePixels(null, 10));
    }

    @Test
    void scaledCacheDropsDecodedOriginal() {
        AssetStore store = new AssetStore(AssetStoreMemoryTest.class);
        BufferedImage original = store.image("/assets/test/dot.png");
        BufferedImage scaled = store.scaled("/assets/test/dot.png", 12, 8);
        BufferedImage reloaded = store.image("/assets/test/dot.png");
        assertNotNull(scaled);
        assertNotSame(original, reloaded);
        assertSame(scaled, store.scaled("/assets/test/dot.png", 12, 8));
    }

    @Test
    void leavingAScreenDropsThatScreensImages() {
        AssetStore store = new AssetStore(AssetStore.class);
        store.enterScreen("menu");
        BufferedImage menu = store.cover(AssetStore.BG_MENU, 48, 27);
        assertNotNull(menu);
        store.enterScreen("raid");
        BufferedImage again = store.cover(AssetStore.BG_MENU, 48, 27);
        assertNotSame(menu, again);
        assertEquals("raid", AssetStore.ownerOf("/assets/raid/scene-raid-wide.jpg"));
        assertEquals("shared", AssetStore.ownerOf("/assets/ui/logo.png"));
    }

    @Test
    void onlyTheCurrentBossIsKept() {
        AssetStore store = new AssetStore(AssetStore.class);
        BufferedImage jelly = store.boss(BossId.JELLY_KING, 1, 24);
        BufferedImage golem = store.boss(BossId.STONE_GOLEM, 1, 24);
        assertNotNull(jelly);
        assertNotNull(golem);
        BufferedImage jellyAgain = store.boss(BossId.JELLY_KING, 1, 24);
        assertNotSame(jelly, jellyAgain);
        assertSame(jellyAgain, store.boss(BossId.JELLY_KING, 1, 24));
        assertTrue(store.scaledBytes() > 0);
    }
}
