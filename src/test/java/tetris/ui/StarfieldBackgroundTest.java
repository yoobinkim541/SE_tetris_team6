package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ComponentEvent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.ui.fx.AssetStore;
import tetris.ui.fx.StarfieldBackground;

/**
 * 별하늘 타이머는 보이는 동안만 돈다. 헤드리스에서는 창을 띄우지 않고,
 * 컴포넌트 숨김 이벤트가 {@code isShowing()}으로 타이머를 멈추는 경로를 확인한다.
 * {@code tetris.fx=low} 이면 입자를 그리지 않는다.
 */
@Timeout(30)
class StarfieldBackgroundTest {
    @Test
    void timerStopsWhenHiddenAndLowEffectsDisablesParticles() {
        String previous = System.getProperty(StarfieldBackground.FX_PROPERTY);
        JPanel panel = new JPanel();
        StarfieldBackground stars = new StarfieldBackground(panel, AssetStore.BG_MENU);
        try {
            assertFalse(stars.timerRunning(), "아직 보이지 않으면 타이머가 꺼져 있다");
            assertTrue(stars.particleCount() > 0);

            stars.setShown(true);
            assertTrue(stars.timerRunning());
            panel.dispatchEvent(new ComponentEvent(panel, ComponentEvent.COMPONENT_HIDDEN));
            assertFalse(stars.timerRunning(), "숨기면 타이머가 멈춘다");

            System.setProperty(StarfieldBackground.FX_PROPERTY, StarfieldBackground.FX_LOW);
            assertEqualsLow(stars);
            stars.setShown(true);
            assertTrue(stars.timerRunning(), "낮은 연출도 보이는 동안 시계는 돈다");
            stars.setShown(false);
            assertFalse(stars.timerRunning());
        } finally {
            stars.dispose();
            if (previous == null) System.clearProperty(StarfieldBackground.FX_PROPERTY);
            else System.setProperty(StarfieldBackground.FX_PROPERTY, previous);
        }
    }

    private static void assertEqualsLow(StarfieldBackground stars) {
        assertTrue(StarfieldBackground.lowEffects());
        assertTrue(stars.particleCount() == 0);
    }
}
