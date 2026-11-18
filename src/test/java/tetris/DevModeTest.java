package tetris;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 개발자 모드는 --dev 인자 또는 설정 토글 중 하나라도 켜져 있으면 켜진다 */
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

    @Test
    void flag만_켜도_켜진다() {
        DevMode.set(false);
        DevMode.enableFrom(new String[]{"--dev"});
        assertTrue(DevMode.enabled());
        assertFalse(DevMode.settingEnabled());
    }

    @Test
    void 설정만_켜도_켜진다() {
        DevMode.set(false);
        DevMode.setSettingEnabled(true);
        assertTrue(DevMode.enabled());
        assertFalse(DevMode.flagEnabled());
    }

    @Test
    void flag가_켜져_있으면_설정을_꺼도_켜져_있다() {
        DevMode.set(false);
        DevMode.enableFrom(new String[]{"--dev"});
        DevMode.setSettingEnabled(true);
        DevMode.setSettingEnabled(false);
        assertTrue(DevMode.enabled(), "--dev로 켠 개발자 모드는 설정 토글로 꺼지지 않는다");
    }

    @Test
    void 둘_다_꺼지면_꺼진다() {
        DevMode.set(false);
        DevMode.enableFrom(new String[]{"--dev"});
        DevMode.setSettingEnabled(true);
        DevMode.set(false);
        DevMode.enableFrom(new String[0]);
        DevMode.setSettingEnabled(false);
        assertFalse(DevMode.enabled());
    }
}
