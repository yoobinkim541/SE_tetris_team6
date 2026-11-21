package tetris.play;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** 결정적이고 1분 안에 끝나는 실제 창 시나리오. 긴 시나리오는 -Dtetris.play.long=true */
@Timeout(90)
class SoakPlayTest {
    @Test
    void fastScenariosFinishWithoutStallOrException() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "창이 없는 검사에서는 건너뛴다");
        PlayHarness harness = new PlayHarness();
        long started = System.nanoTime();
        harness.runAll();
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        assertTrue(harness.failures().isEmpty(), harness.report());
        assertTrue(seconds < 60, "fast soak took " + seconds + "s\n" + harness.report());
    }
}
