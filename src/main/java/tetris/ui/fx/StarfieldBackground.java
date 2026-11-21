package tetris.ui.fx;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.image.BufferedImage;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * 밤하늘 배경. 그림 한 장(없으면 남색 그라데이션) 위에 별·먼지·별똥별을 코드로 그린다.
 * 화면마다 타이머는 하나(약 60fps)이고, 패널이 보이지 않으면 멈춘다.
 *
 * <p>시스템 속성 {@code tetris.fx=low} 이거나 설정의 연출이 낮음이면 입자(별·먼지·별똥별)를 그리지 않는다.
 * 배경 그림은 그대로 둔다.
 */
public final class StarfieldBackground {
    public static final String FX_PROPERTY = "tetris.fx";
    public static final String FX_LOW = "low";
    private static volatile boolean effectsHigh = true;
    private static final int FRAME_MILLIS = 16;

    private final JComponent host;
    private final String resource;
    private final AssetStore assets;
    private final Timer timer;
    private final Star[] stars;
    private final Dust[] dust;
    private Shoot shoot;
    private int frame;
    private int shootIn = 90;
    private Runnable onFrame;

    public StarfieldBackground(JComponent host, String resource) {
        this(host, resource, AssetStore.SHARED, new Random(0x5E7E_7215L));
    }

    /** 테스트가 저장소·난수를 고정할 때 쓴다 */
    public StarfieldBackground(JComponent host, String resource, AssetStore assets, Random random) {
        this.host = host;
        this.resource = resource;
        this.assets = assets == null ? AssetStore.SHARED : assets;
        this.stars = new Star[42];
        this.dust = new Dust[18];
        Random rng = random == null ? new Random(1) : random;
        for (int i = 0; i < stars.length; i++) stars[i] = Star.create(rng, i);
        for (int i = 0; i < dust.length; i++) dust[i] = Dust.create(rng);
        timer = new Timer(FRAME_MILLIS, event -> tick());
        timer.setRepeats(true);
        timer.setCoalesce(true);
        host.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { sync(); }
            @Override public void componentHidden(ComponentEvent e) { sync(); }
        });
        host.addHierarchyListener(new HierarchyListener() {
            @Override public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.DISPLAYABILITY_CHANGED)) != 0) {
                    sync();
                }
            }
        });
        sync();
    }

    /** {@code tetris.fx=low} 이거나 설정 연출이 낮음이면 입자를 끈다 */
    public static boolean lowEffects() {
        return !effectsHigh || FX_LOW.equalsIgnoreCase(System.getProperty(FX_PROPERTY));
    }

    /** 설정 화면의 연출 값. 시스템 속성이 low면 이 값과 관계없이 입자를 끈다 */
    public static void setEffectsHigh(boolean high) {
        effectsHigh = high;
    }

    /** 같은 타이머로 연출(흔들림·숫자)을 갱신한다. 화면당 타이머는 이 하나다 */
    public void onFrame(Runnable listener) {
        onFrame = listener;
    }

    /** 입자 수. 낮은 연출이면 0. 테스트가 속성 적용을 확인한다 */
    public int particleCount() {
        return lowEffects() ? 0 : stars.length + dust.length;
    }

    public boolean timerRunning() {
        return timer.isRunning();
    }

    public int frame() {
        return frame;
    }

    /**
     * 보이는 동안만 타이머를 돌린다. 숨기면 멈춘다.
     * 컴포넌트 리스너가 {@code isShowing()}으로 이 메서드를 부른다. 테스트도 같은 경로를 쓴다.
     */
    public void setShown(boolean shown) {
        if (shown) {
            if (!timer.isRunning()) timer.start();
        } else if (timer.isRunning()) {
            timer.stop();
        }
    }

    /** 패널을 버릴 때 타이머를 멈춘다 */
    public void dispose() {
        timer.stop();
    }

    public void paint(Graphics2D g, int width, int height) {
        if (width <= 0 || height <= 0) return;
        Graphics2D layer = (Graphics2D) g.create();
        try {
            paintSky(layer, width, height);
            if (!lowEffects()) paintParticles(layer, width, height);
        } finally {
            layer.dispose();
        }
    }

    private void sync() {
        setShown(host.isShowing());
    }

    private void tick() {
        if (!host.isShowing()) {
            setShown(false);
            return;
        }
        frame++;
        if (onFrame != null) onFrame.run();
        if (!lowEffects()) {
            if (shoot != null) {
                shoot.x += shoot.vx;
                shoot.y += shoot.vy;
                shoot.life--;
                if (shoot.life <= 0) shoot = null;
            } else if (--shootIn <= 0) {
                shoot = Shoot.create(frame);
                shootIn = 220 + (frame % 80);
            }
        }
        host.repaint();
    }

    private void paintSky(Graphics2D g, int width, int height) {
        // 배율이 걸린 화면에서 배경 한 장을 매 프레임 늘리면 느리다. 장치 픽셀 크기 그림을 1:1로 찍는다
        boolean[] drawn = {false};
        tetris.ui.theme.UiKit.drawDeviceImage(g, 0, 0, width, height, (w, h) -> {
            BufferedImage cover = assets.cover(resource, w, h);
            drawn[0] = cover != null;
            return cover;
        });
        if (drawn[0]) return;
        g.setPaint(new LinearGradientPaint(0, 0, 0, height,
                new float[] {0f, 0.55f, 1f},
                new Color[] {new Color(0x12082A), new Color(0x07091A), new Color(0x1A0A32)}));
        g.fillRect(0, 0, width, height);
        paintCrystal(g, width, height, true);
        paintCrystal(g, width, height, false);
    }

    /** 에셋이 없을 때 가장자리에만 수정 섬을 그린다. 가운데 보드는 비워 둔다 */
    private static void paintCrystal(Graphics2D g, int width, int height, boolean left) {
        int spike = Math.max(16, Math.min(width, height) / 16);
        int base = left ? 2 : width - spike - 4;
        int top = left ? 4 : height - spike * 2 - 4;
        int[] xs = {base, base + spike / 2, base + spike, base + spike / 3};
        int[] ys = {top + spike, top, top + spike / 2, top + spike + spike / 3};
        g.setColor(left ? new Color(0x3DE0FF) : new Color(0xE85CFF));
        g.fillPolygon(xs, ys, 4);
        g.setColor(new Color(255, 255, 255, 90));
        g.drawLine(xs[1], ys[1], xs[0] + spike / 2, ys[3]);
    }

    private void paintParticles(Graphics2D g, int width, int height) {
        for (Star star : stars) {
            double twinkle = 0.35 + 0.65 * Math.abs(Math.sin(frame * star.speed + star.phase));
            int alpha = (int) (star.alpha * twinkle);
            int x = (int) ((star.x * width + frame * star.drift) % width);
            if (x < 0) x += width;
            int y = (int) (star.y * height);
            tetris.ui.theme.UiKit.softDot(g, x, y, Math.max(2, star.size + 1),
                    new Color((star.rgb & 0x00ffffff) | (alpha << 24), true));
        }
        for (Dust mote : dust) {
            int x = (int) ((mote.x * width + frame * mote.vx) % width);
            if (x < 0) x += width;
            int y = (int) ((mote.y * height + frame * mote.vy) % height);
            if (y < 0) y += height;
            tetris.ui.theme.UiKit.softDot(g, x, y, Math.max(2, mote.size + 1), new Color(180, 170, 230, mote.alpha));
        }
        if (shoot != null) paintShoot(g, width, height);
    }

    private void paintShoot(Graphics2D g, int width, int height) {
        int x = (int) (shoot.x * width);
        int y = (int) (shoot.y * height);
        for (int i = 6; i >= 0; i--) {
            int alpha = Math.max(0, 210 - i * 32) * shoot.life / Shoot.LIFE;
            g.setColor(new Color(255, 245, 220, alpha));
            int tx = x - (int) (shoot.vx * width * i * 0.35);
            int ty = y - (int) (shoot.vy * height * i * 0.35);
            g.drawLine(tx, ty, tx + 2, ty + 1);
        }
        g.setColor(new Color(255, 255, 255, 220));
        g.fillOval(x, y, 3, 3);
    }

    private static final class Star {
        final double x;
        final double y;
        final double speed;
        final double phase;
        final double drift;
        final int size;
        final int alpha;
        final int rgb;

        private Star(double x, double y, double speed, double phase, double drift, int size, int alpha, int rgb) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.phase = phase;
            this.drift = drift;
            this.size = size;
            this.alpha = alpha;
            this.rgb = rgb;
        }

        static Star create(Random rng, int index) {
            int layer = index % 3;
            int rgb = layer == 0 ? 0xF4F7FF : layer == 1 ? 0x7EE7FF : 0xE7A0FF;
            return new Star(rng.nextDouble(), rng.nextDouble(), 0.04 + layer * 0.03, rng.nextDouble() * Math.PI * 2,
                    0.02 + layer * 0.03, layer == 2 ? 2 : 1, 140 + layer * 30, rgb);
        }
    }

    private static final class Dust {
        final double x;
        final double y;
        final double vx;
        final double vy;
        final int size;
        final int alpha;

        private Dust(double x, double y, double vx, double vy, int size, int alpha) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.alpha = alpha;
        }

        static Dust create(Random rng) {
            return new Dust(rng.nextDouble(), rng.nextDouble(), 0.08 + rng.nextDouble() * 0.12,
                    0.02 + rng.nextDouble() * 0.04, 2 + rng.nextInt(2), 40 + rng.nextInt(50));
        }
    }

    private static final class Shoot {
        static final int LIFE = 28;
        double x;
        double y;
        final double vx;
        final double vy;
        int life = LIFE;

        private Shoot(double x, double y, double vx, double vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }

        static Shoot create(int frame) {
            double start = (frame % 5) / 5.0;
            return new Shoot(0.05 + start * 0.4, 0.02, 0.012, 0.008);
        }
    }
}
