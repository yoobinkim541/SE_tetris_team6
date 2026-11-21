package tetris.ui.input;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;

/**
 * 세로 배치를 화면 안에 맞춘다. 16:9에서는 높이에 맞추고 좌우에 여백이 남는다.
 * 마우스는 이 변환의 역으로 그리기 좌표에 맞춘다. DPI 배율은 마우스 좌표에 이미 빠져 있다.
 */
public record Fit(double scale, double offsetX, double offsetY, int logicalW, int logicalH, int screenW, int screenH) {
    public static Fit contain(int screenW, int screenH, int logicalW, int logicalH) {
        int sw = Math.max(1, screenW);
        int sh = Math.max(1, screenH);
        int lw = Math.max(1, logicalW);
        int lh = Math.max(1, logicalH);
        double scale = Math.min(sw / (double) lw, sh / (double) lh);
        if (scale <= 0 || Double.isNaN(scale) || Double.isInfinite(scale)) scale = 1;
        double drawnW = lw * scale;
        double drawnH = lh * scale;
        return new Fit(scale, (sw - drawnW) / 2.0, (sh - drawnH) / 2.0, lw, lh, sw, sh);
    }

    /** 16:9처럼 세로 배치보다 넓은 화면이면 좌우 여백이 있다 */
    public boolean hasSideMargin() {
        return offsetX >= 1;
    }

    /** 그리기에 거는 변환. 벡터는 이 배율로 장치 해상도에 직접 그려진다 */
    public AffineTransform transform() {
        AffineTransform paint = new AffineTransform();
        paint.translate(offsetX, offsetY);
        paint.scale(scale, scale);
        return paint;
    }

    /** 화면 좌표를 그리기 좌표로. 페인트 변환의 역 */
    public Point2D.Double toPaint(double x, double y) {
        Point2D.Double out = new Point2D.Double();
        try {
            transform().inverseTransform(new Point2D.Double(x, y), out);
        } catch (NoninvertibleTransformException e) {
            out.setLocation(x, y);
        }
        return out;
    }
}
