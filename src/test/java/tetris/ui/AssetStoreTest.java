package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.ui.fx.AssetStore;

/** 없는 그림은 null, 같은 목표 크기는 한 번만 축소한다 */
@Timeout(30)
class AssetStoreTest {
    @Test
    void missingImageIsNullAndScaledImagesAreCachedPerSize() {
        AssetStore store = new AssetStore(AssetStoreTest.class);
        assertNull(store.image("/assets/does-not-exist.png"));
        assertNull(store.scaled("/assets/does-not-exist.png", 16, 16));
        assertNull(store.cover("/assets/does-not-exist.png", 32, 18));
        assertNull(store.scaled("/assets/test/dot.png", 0, 8));

        var first = store.scaled("/assets/test/dot.png", 12, 8);
        var again = store.scaled("/assets/test/dot.png", 12, 8);
        var other = store.scaled("/assets/test/dot.png", 6, 6);
        assertEquals(12, first.getWidth());
        assertEquals(8, first.getHeight());
        assertSame(first, again);
        assertNotSame(first, other);
        assertSame(store.image("/assets/test/dot.png"), store.image("/assets/test/dot.png"));
    }
}
