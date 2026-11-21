package tetris.ui.input;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 그릴 때 만든 사각형으로만 클릭을 판정한다.
 * {@link #begin}에 넘긴 변환의 역으로 좌표를 맞춘 뒤 고른다.
 */
public final class Hits {
    public record Zone(String id, Rectangle bounds) {
    }

    private final List<Zone> zones = new ArrayList<>();
    private final AffineTransform toPaint = new AffineTransform();

    /** 이번 그리기에서 쓸 사각형을 비운다. g의 변환을 역으로 보관한다 */
    public void begin(AffineTransform paint) {
        zones.clear();
        toPaint.setToIdentity();
        if (paint != null) toPaint.setTransform(paint);
    }

    public void add(String id, int x, int y, int w, int h) {
        if (id == null || w <= 0 || h <= 0) return;
        zones.add(new Zone(id, new Rectangle(x, y, w, h)));
    }

    public void add(String id, Rectangle bounds) {
        if (bounds == null) return;
        add(id, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /** 컴포넌트 좌표. 보관한 페인트 변환의 역을 적용한 뒤 사각형을 본다 */
    public String hit(int x, int y) {
        Point2D.Double point = new Point2D.Double(x, y);
        try {
            toPaint.createInverse().transform(point, point);
        } catch (NoninvertibleTransformException e) {
            return null;
        }
        for (int i = zones.size() - 1; i >= 0; i--) {
            if (zones.get(i).bounds.contains(point)) return zones.get(i).id;
        }
        return null;
    }

    public List<Zone> zones() {
        return List.copyOf(zones);
    }
}
