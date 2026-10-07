package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tetris.feature.setting.ScreenSize;

/** RND-1: 화면 크기는 셀 표시 크기만 바꾼다 (Small -> Medium -> Large 순환) */
public class ScreenSizeTest {
    @Test
    void 셀_크기는_Small_20_Medium_30_Large_40() {
        assertEquals(20, ScreenSize.SMALL.cellPixels());
        assertEquals(30, ScreenSize.MEDIUM.cellPixels());
        assertEquals(40, ScreenSize.LARGE.cellPixels());
    }

    @Test
    void getNextSize는_Small_Medium_Large_Small_순서로_순환한다() {
        assertEquals(ScreenSize.MEDIUM, ScreenSize.SMALL.getNextSize());
        assertEquals(ScreenSize.LARGE, ScreenSize.MEDIUM.getNextSize());
        assertEquals(ScreenSize.SMALL, ScreenSize.LARGE.getNextSize());
    }
}
