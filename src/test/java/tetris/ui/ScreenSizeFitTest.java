package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import org.junit.jupiter.api.Test;
import tetris.feature.setting.ScreenSize;

/** RND-3: 화면이 작으면 셀 크기를 줄이되, 세 화면 크기가 서로 구분되게 남는다 (요구사항 p12 "최소 3가지") */
public class ScreenSizeFitTest {
    private static int fitted(ScreenSize size, int largestFit) {
        return MainFrame.scaledCellSize(size.cellPixels(), largestFit);
    }

    @Test
    void sizesAreUnchangedWhenLargeFits() {
        int largestFit = MainFrame.largestFittingCell(2000, 2000);
        assertEquals(ScreenSize.LARGE.cellPixels(), largestFit);
        for (ScreenSize size : ScreenSize.values()) assertEquals(size.cellPixels(), fitted(size, largestFit));
    }

    @Test
    void largestFittingCellIsTheBiggestThatFits() {
        int height = 688; // 1080p·배율 150%에서 작업 표시줄을 뺀 높이 정도
        int cell = MainFrame.largestFittingCell(1280, height);
        assertTrue(GamePanel.preferredSizeFor(cell).height <= height);
        assertTrue(GamePanel.preferredSizeFor(cell + 1).height > height);
    }

    @Test
    void allThreeSizesStayDistinctOnASmallScreen() {
        int largestFit = MainFrame.largestFittingCell(1280, 688);
        int small = fitted(ScreenSize.SMALL, largestFit);
        int medium = fitted(ScreenSize.MEDIUM, largestFit);
        int large = fitted(ScreenSize.LARGE, largestFit);

        assertEquals(largestFit, large);
        assertTrue(small < medium && medium < large, small + " < " + medium + " < " + large);
        Dimension window = GamePanel.preferredSizeFor(large);
        assertTrue(window.height <= 688);
    }

    @Test
    void cellNeverGoesBelowMinimum() {
        int largestFit = MainFrame.largestFittingCell(100, 100);
        assertTrue(fitted(ScreenSize.SMALL, largestFit) >= 12);
    }
}
