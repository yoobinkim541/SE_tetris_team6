package tetris.ui.input;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import tetris.ui.fx.AssetStore;
import tetris.ui.theme.UiKit;

/**
 * 카드 덱을 세로 논리 크기로 두고, 창이 더 크면 g.scale로 가운데에 맞춘다.
 * 좌우 여백은 넓은 배경이다. 작은 버퍼를 늘려 그리지 않는다.
 * 마우스는 페인트 변환의 역으로 덱 좌표에 다시 보낸다.
 */
public final class Stage extends JPanel {
    public static final String WIDE_RAID = "/assets/raid/scene-raid-wide.jpg";

    private final JPanel deck;
    private final JPanel shim;
    private int logicalW = 1;
    private int logicalH = 1;
    private String backdrop;
    private Runnable pauseAction;
    private String notice = "";
    private final Rectangle pauseBox = new Rectangle();
    private AffineTransform paint = new AffineTransform();

    public Stage(JPanel deck) {
        super(null);
        this.deck = deck;
        shim = new JPanel(null) {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }
        };
        shim.setOpaque(false);
        shim.add(deck);
        setOpaque(true);
        add(shim);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    public void setLogicalSize(int width, int height) {
        logicalW = Math.max(1, width);
        logicalH = Math.max(1, height);
        revalidate();
        repaint();
    }

    public int logicalWidth() {
        return logicalW;
    }

    public int logicalHeight() {
        return logicalH;
    }

    public void setBackdrop(String path) {
        backdrop = path;
        repaint();
    }

    /** null이면 일시정지 아이콘을 그리지 않는다 */
    public void setPauseAction(Runnable action) {
        pauseAction = action;
        repaint();
    }

    /** 예외 안내. 화면 전체를 덮지 않는 한 줄이다 */
    public void setNotice(String text) {
        notice = text == null ? "" : text;
        repaint();
    }

    public Fit fit() {
        return Fit.contain(Math.max(1, getWidth()), Math.max(1, getHeight()), logicalW, logicalH);
    }

    /** 지금 그리기에 거는 변환. 테스트가 역변환을 확인한다 */
    public AffineTransform paintTransform() {
        return new AffineTransform(layoutTransform());
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(logicalW, logicalH);
    }

    @Override
    public void doLayout() {
        shim.setBounds(0, 0, logicalW, logicalH);
        deck.setBounds(0, 0, logicalW, logicalH);
        deck.doLayout();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            paintWide(g2);
            layoutPause();
            paintPause(g2);
            paintNotice(g2);
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.transform(layoutTransform());
            super.paintChildren(g2);
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED && pauseHit(e)) {
            e.consume();
            pauseAction.run();
            return;
        }
        forward(e);
        super.processMouseEvent(e);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
        if (pauseAction != null && pauseBox.contains(e.getPoint())) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            forward(e);
        }
        super.processMouseMotionEvent(e);
    }

    private AffineTransform layoutTransform() {
        Fit fit = fit();
        AffineTransform layout = fit.transform();
        paint = layout;
        return layout;
    }

    private void paintWide(Graphics2D g) {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        int dw = AssetStore.devicePixels(g, w);
        int dh = AssetStore.devicePixels(g, h);
        BufferedImage image = backdrop == null ? null : AssetStore.SHARED.cover(backdrop, dw, dh);
        if (image == null && WIDE_RAID.equals(backdrop)) {
            image = AssetStore.SHARED.cover(AssetStore.BG_RAID, dw, dh);
        }
        if (image != null) {
            g.drawImage(image, 0, 0, w, h, null);
            return;
        }
        UiKit.paintScene(g, w, h);
    }

    private void layoutPause() {
        if (pauseAction == null || getWidth() < 48 || getHeight() < 48) {
            pauseBox.setBounds(0, 0, 0, 0);
            return;
        }
        int size = 36;
        pauseBox.setBounds(getWidth() - size - 16, 16, size, size);
    }

    private void paintPause(Graphics2D g) {
        if (pauseBox.width <= 0) return;
        g.setColor(new Color(8, 12, 28, 190));
        g.fillRoundRect(pauseBox.x, pauseBox.y, pauseBox.width, pauseBox.height, 12, 12);
        g.setColor(Color.WHITE);
        int barW = 4;
        int gap = 4;
        int barH = 14;
        int x = pauseBox.x + (pauseBox.width - barW * 2 - gap) / 2;
        int y = pauseBox.y + (pauseBox.height - barH) / 2;
        g.fillRoundRect(x, y, barW, barH, 2, 2);
        g.fillRoundRect(x + barW + gap, y, barW, barH, 2, 2);
    }

    private void paintNotice(Graphics2D g) {
        if (notice.isEmpty() || getWidth() < 80) return;
        g.setFont(tetris.ui.theme.GameTheme.text(13, false));
        int width = Math.min(getWidth() - 24, g.getFontMetrics().stringWidth(notice) + 24);
        int x = (getWidth() - width) / 2;
        int y = getHeight() - 36;
        g.setColor(new Color(8, 12, 28, 210));
        g.fillRoundRect(x, y, width, 26, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(notice, x + 12, y + 18);
    }

    private boolean pauseHit(MouseEvent e) {
        return pauseAction != null && e.getButton() == MouseEvent.BUTTON1 && pauseBox.contains(e.getPoint());
    }

    private void forward(MouseEvent e) {
        Point2D.Double logical = new Point2D.Double();
        try {
            layoutTransform().inverseTransform(new Point2D.Double(e.getX(), e.getY()), logical);
        } catch (NoninvertibleTransformException ex) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        int x = (int) Math.floor(logical.x);
        int y = (int) Math.floor(logical.y);
        if (x < 0 || y < 0 || x >= logicalW || y >= logicalH) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        Component target = SwingUtilities.getDeepestComponentAt(deck, x, y);
        if (target == null) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        Point inTarget = SwingUtilities.convertPoint(deck, new Point(x, y), target);
        MouseEvent converted = new MouseEvent(target, e.getID(), e.getWhen(), e.getModifiersEx(),
                inTarget.x, inTarget.y, e.getXOnScreen(), e.getYOnScreen(),
                e.getClickCount(), e.isPopupTrigger(), e.getButton());
        target.dispatchEvent(converted);
        setCursor(target.getCursor());
    }
}
