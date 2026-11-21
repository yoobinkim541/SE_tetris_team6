package tetris.ui.theme;

import java.awt.Rectangle;

/**
 * 에셋 원본에서 잰 글자 칸. 분수는 불투명 경계의 어두운 안쪽(또는 뚫린 구멍)이다.
 * 가로 3등분·9등분은 그릴 때와 같은 캡 변환으로 늘리고, 통째로 늘리는 그림은 비율 그대로다.
 */
final class AssetInsets {
    /** button.png 512x128, 캡 64, 가로 3등분. 어두운 칸 59,13,424x100 */
    static final Hole BUTTON = horizontal(512, 128, 64, 59, 13, 29, 15);
    /** capsule.png 256x96, 캡 48. 어두운 칸 25,11,205x74 */
    static final Hole CAPSULE = horizontal(256, 96, 48, 25, 11, 26, 11);
    /** tab-cyan.png 192x192, 캡 48, 9등분. 어두운 칸 14,16,164x160 */
    static final Hole TAB = nine(192, 192, 48, 14, 16, 14, 16);
    /** banner-warning.png 900x160, 캡 48. 어두운 칸 230,25,568x108 */
    static final Hole BANNER = horizontal(900, 160, 48, 230, 25, 102, 27);
    /** nametab-p1.png 520x136, 통째 확대. 어두운 칸 146,33,342x72 */
    static final Hole NAME_P1 = uniform(520, 136, 146, 33, 32, 31);
    /** nametab-p2.png 520x136. 어두운 칸 33,38,351x69 */
    static final Hole NAME_P2 = uniform(520, 136, 33, 38, 136, 29);
    /** hp-frame.png 1080x161 막대 구멍 184,60,644x45. 프레임 전체에만 적용한다 */
    static final Hole HP = uniform(1080, 161, 184, 60, 252, 56);
    /** frame-cyan.png 256x256 안쪽 구멍 19,23,218x211 */
    static final Hole FRAME = nine(256, 256, 64, 19, 23, 19, 22);

    private AssetInsets() {
    }

    static Rectangle map(Hole hole, Rectangle rect, int maxCorner) {
        if (rect == null) return new Rectangle();
        if (hole == null || rect.width <= 0 || rect.height <= 0) {
            return new Rectangle(rect.x, rect.y, Math.max(1, rect.width), Math.max(1, rect.height));
        }
        int left = inset(hole, hole.left, hole.srcW, rect.width, hole.cap, hole.nine, maxCorner, true);
        int right = inset(hole, hole.right, hole.srcW, rect.width, hole.cap, hole.nine, maxCorner, true);
        int top = inset(hole, hole.top, hole.srcH, rect.height, hole.cap, hole.nine, maxCorner, false);
        int bottom = inset(hole, hole.bottom, hole.srcH, rect.height, hole.cap, hole.nine, maxCorner, false);
        if (left + right >= rect.width) {
            left = Math.max(1, rect.width / 8);
            right = left;
        }
        if (top + bottom >= rect.height) {
            top = Math.max(1, rect.height / 6);
            bottom = top;
        }
        return new Rectangle(rect.x + left, rect.y + top,
                Math.max(1, rect.width - left - right), Math.max(1, rect.height - top - bottom));
    }

    /** HP 프레임 전체인지. 이미 구멍인 사각형에는 프레임 분수를 다시 넣지 않는다 */
    static boolean hpFrame(Rectangle rect) {
        if (rect == null || rect.height <= 0) return false;
        double aspect = rect.width / (double) rect.height;
        double frame = 1080.0 / 161.0;
        return Math.abs(aspect - frame) / frame < 0.12;
    }

    private static int inset(Hole hole, int sourceInset, int sourceSpan, int destSpan, int cap, boolean nine,
                              int maxCorner, boolean horizontal) {
        if (hole.uniform || sourceSpan <= 0 || destSpan <= 0) {
            return Math.max(0, (int) Math.round(sourceInset * (destSpan / (double) Math.max(1, sourceSpan))));
        }
        int sc = Math.min(cap, sourceSpan / 2);
        int dc = Math.min(sc, destSpan / 2);
        if (nine && maxCorner > 0) dc = Math.min(dc, maxCorner);
        if (sc <= 0 || dc <= 0) {
            return Math.max(0, (int) Math.round(sourceInset * (destSpan / (double) sourceSpan)));
        }
        if (!horizontal && !nine) {
            return Math.max(0, (int) Math.round(sourceInset * (destSpan / (double) sourceSpan)));
        }
        if (sourceInset <= sc) return Math.max(0, (int) Math.round(sourceInset * (dc / (double) sc)));
        int stretchSrc = Math.max(1, sourceSpan - 2 * sc);
        int stretchDst = Math.max(1, destSpan - 2 * dc);
        return dc + (int) Math.round((sourceInset - sc) * (stretchDst / (double) stretchSrc));
    }

    private static Hole horizontal(int srcW, int srcH, int cap, int left, int top, int right, int bottom) {
        return new Hole(srcW, srcH, cap, false, false, left, top, right, bottom);
    }

    private static Hole nine(int srcW, int srcH, int cap, int left, int top, int right, int bottom) {
        return new Hole(srcW, srcH, cap, true, false, left, top, right, bottom);
    }

    private static Hole uniform(int srcW, int srcH, int left, int top, int right, int bottom) {
        return new Hole(srcW, srcH, 0, false, true, left, top, right, bottom);
    }

    record Hole(int srcW, int srcH, int cap, boolean nine, boolean uniform,
                int left, int top, int right, int bottom) {
    }
}
