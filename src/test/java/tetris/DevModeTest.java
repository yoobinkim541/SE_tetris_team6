package tetris;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 개발자 모드는 --dev 인자로만 켜지고, 인자가 없으면 꺼진 채다 */
class DevModeTest {
    @AfterEach
    void reset() {
        DevMode.set(false);
    }

    @Test
    void onlyTheDevFlagTurnsItOn() {
        DevMode.set(false);
        DevMode.enableFrom(new String[0]);
        assertFalse(DevMode.enabled());
        DevMode.enableFrom(new String[]{"--developer", "dev"});
        assertFalse(DevMode.enabled());
        DevMode.enableFrom(null);
        assertFalse(DevMode.enabled());
        DevMode.enableFrom(new String[]{"--dev"});
        assertTrue(DevMode.enabled());
    }
}
