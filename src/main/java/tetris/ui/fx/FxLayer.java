package tetris.ui.fx;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import tetris.feature.raid.api.RaidEvent;
import tetris.ui.theme.GameTheme;
import tetris.ui.theme.UiKit;

/**
 * 줄 삭제 불꽃, 화면 흔들림, 데미지 숫자, 가운데 큰 문구, 보스 피격 번쩍임.
 * 스냅샷의 {@link RaidEvent}만 보고 그린다. 타이머는 화면이 하나 돌린다.
 */
public final class FxLayer {
    private static final int POOL = 48;
    private static final int FLASH_FRAMES = 5;

    private final Spark[] pool = new Spark[POOL];
    private final List<Floater> numbers = new ArrayList<>();
    private final List<Banner> banners = new ArrayList<>();
    private final Wave[] waves = new Wave[4];
    private BufferedImage glow;
    private int shake;
    private int shakeAmp;
    private int shakeDir = 1;
    private int flash;
    private int recoil;
    private boolean colorBlind;
    private int glowX;
    private int glowY;
    private int glowLife;
    private boolean glowPlaced;

    public FxLayer() {
        for (int i = 0; i < pool.length; i++) pool[i] = new Spark();
    }

    public void setColorBlind(boolean colorBlind) {
        this.colorBlind = colorBlind;
    }

    public void absorb(List<RaidEvent> events) {
        if (events == null) return;
        for (RaidEvent event : events) absorb(event);
    }

    public void tick() {
        if (shake > 0) {
            shake--;
            shakeDir = -shakeDir;
        }
        if (flash > 0) flash--;
        if (recoil > 0) recoil--;
        if (glowLife > 0) glowLife--;
        for (Spark spark : pool) if (spark.life > 0) spark.tick();
        for (Wave wave : waves) if (wave != null && wave.life > 0) wave.tick();
        Iterator<Floater> numbersLeft = numbers.iterator();
        while (numbersLeft.hasNext()) {
            Floater floater = numbersLeft.next();
            floater.age++;
            if (floater.age >= floater.life) numbersLeft.remove();
        }
        banners.removeIf(banner -> --banner.life <= 0);
    }

    /** 보드를 옆으로 밀 픽셀. 데미지가 클수록 크다 */
    public int shakeX() {
        if (shake <= 0 || StarfieldBackground.lowEffects()) return 0;
        return shakeDir * Math.min(shakeAmp, shake + 1);
    }

    public int flashAlpha() {
        return flash > 0 ? 210 : 0;
    }

    public int recoilY() {
        return StarfieldBackground.lowEffects() ? 0 : recoil;
    }

    public void paintSparks(Graphics2D g) {
        if (StarfieldBackground.lowEffects()) return;
        paintGlow(g);
        for (Wave wave : waves) if (wave != null && wave.life > 0 && wave.placed) wave.paint(g);
        for (Spark spark : pool) if (spark.life > 0 && spark.placed) spark.paint(g);
    }

    /** boardX/boardTop: 참가자 0, 1의 보드 가운데·위. 없는 참가자는 건너뛴다 */
    public void paintNumbers(Graphics2D g, int[] boardCenterX, int boardTop) {
        for (Floater floater : numbers) {
            if (floater.onBoss || floater.player < 0 || floater.player >= boardCenterX.length) continue;
            paintFloater(g, floater, boardCenterX[floater.player], boardTop);
        }
    }

    public void paintBossNumbers(Graphics2D g, int x, int y) {
        for (Floater floater : numbers) {
            if (!floater.onBoss) continue;
            paintFloater(g, floater, x, y);
        }
    }

    public void paintBanner(Graphics2D g, int centerX, int centerY) {
        if (banners.isEmpty()) return;
        Banner banner = banners.get(banners.size() - 1);
        int alpha = Math.min(255, banner.life * 10);
        int rgb = colorBlind ? 0xF0E442 : banner.rgb;
        Composite previous = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.2f, alpha / 255f)));
        UiKit.paintGlowText(g, banner.text, GameTheme.popup(480), new Color(rgb), centerX, centerY);
        g.setComposite(previous);
    }

    /** 캡처·미리보기. 보드 한가운데에서 삭제 또는 폭탄이 터지는 중간 */
    public void previewBurst(int player, boolean bomb) {
        burst(player, bomb ? 0xFF8A3C : 0xFFFFFF, bomb ? 10 : 8);
        shock(player, bomb ? 0xFFB45A : 0xE8FBFF);
        glowLife = 8;
        glowPlaced = false;
    }

    private void absorb(RaidEvent event) {
        switch (event.type()) {
            case DAMAGE -> {
                addNumber(event.player(), String.valueOf(event.value()), 0xFFD36A, false, true);
                burst(event.player(), 0x7EE7FF, 8);
                shock(event.player(), 0xD8F6FF);
                hit(event.value());
            }
            case CRITICAL -> {
                addNumber(event.player(), String.valueOf(event.value()), 0xFFE27A, true, true);
                banner(colorBlind ? "치명타!" : "CRITICAL!", 0xFFE27A);
                burst(event.player(), 0xFFD36A, 6);
                shock(event.player(), 0xFFE27A);
                hit(Math.max(80, event.value()));
            }
            case COMBO -> banner(event.value() + " COMBO!", 0x7EE7FF);
            case TSPIN -> banner("T-SPIN", 0xE85CFF);
            case SYNC -> addNumber(event.player(), "+" + event.value(), 0x7EE7FF, false, false);
            case BLOCKED, SHIELDED -> banner("BLOCKED!", 0x8BE7FF);
            case REVIVED -> banner("REVIVED!", 0x8CFFC1);
            case ULTIMATE -> {
                addNumber(event.player(), String.valueOf(event.value()), 0xE85CFF, false, true);
                banner("필살!", 0xE85CFF);
                hit(120);
                burst(0, 0xE85CFF, 8);
                burst(1, 0x7EE7FF, 8);
            }
            case BOSS_ATTACK -> flash = Math.max(flash, 2);
            default -> {
            }
        }
    }

    private void hit(int damage) {
        int amp = Math.min(9, 2 + Math.max(0, damage) / 22);
        shakeAmp = Math.max(shakeAmp, amp);
        shake = Math.max(shake, 4 + amp);
        flash = FLASH_FRAMES;
        recoil = Math.max(recoil, Math.min(14, 3 + damage / 20));
        glowLife = 6;
        glowPlaced = false;
    }

    private void addNumber(int player, String text, int rgb, boolean crit, boolean onBoss) {
        if (player < 0 && !onBoss) return;
        numbers.add(new Floater(Math.max(0, player), text, rgb, crit ? 14 : 26, crit, onBoss));
    }

    private void banner(String text, int rgb) {
        banners.add(new Banner(text, rgb, 46));
    }

    private void burst(int player, int rgb, int count) {
        if (player < 0 || StarfieldBackground.lowEffects()) return;
        int spawned = 0;
        for (Spark spark : pool) {
            if (spark.life > 0) continue;
            double angle = spawned * Math.PI * 2 / count;
            double speed = 1.4 + (spawned % 3) * 0.45;
            spark.arm(player, Math.cos(angle) * speed, Math.sin(angle) * speed - 0.4, rgb, 16);
            if (++spawned >= count) return;
        }
    }

    private void shock(int player, int rgb) {
        if (StarfieldBackground.lowEffects()) return;
        for (int i = 0; i < waves.length; i++) {
            if (waves[i] == null) waves[i] = new Wave();
            if (waves[i].life <= 0) {
                waves[i].arm(player, rgb);
                return;
            }
        }
    }

    private void paintGlow(Graphics2D g) {
        if (glowLife <= 0 || !glowPlaced) return;
        if (glow == null) glow = glowSprite(72);
        Composite previous = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.15f, glowLife / 8f)));
        g.drawImage(glow, glowX - glow.getWidth() / 2, glowY - glow.getHeight() / 2, null);
        g.setComposite(previous);
    }

    private void paintFloater(Graphics2D g, Floater floater, int anchorX, int anchorY) {
        float t = Math.min(1f, floater.age / (float) floater.life);
        float rise = 1f - (1f - t) * (1f - t);
        int alpha = (int) (255 * (1f - t * t));
        int rgb = colorBlind ? (floater.crit ? 0xE69F00 : 0x56B4E9) : floater.rgb;
        String text = floater.crit ? "치명 " + floater.text : floater.text;
        int size = floater.crit ? 28 : 18;
        Font font = floater.crit ? GameTheme.digits(size) : GameTheme.fontFor(text, size, true);
        Composite previous = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.12f, alpha / 255f)));
        UiKit.paintGlowText(g, text, font, new Color(rgb), anchorX, anchorY - (int) (rise * (floater.crit ? 36 : 22)));
        g.setComposite(previous);
    }

    private static BufferedImage glowSprite(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setPaint(new RadialGradientPaint(size / 2f, size / 2f, size / 2f, new float[] {0f, 0.45f, 1f},
                    new Color[] {new Color(255, 255, 255, 210), new Color(120, 220, 255, 90), new Color(80, 180, 255, 0)}));
            g.fillOval(0, 0, size, size);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** 불꽃의 시작점. 패널이 보드 좌표를 알고 나서 채운다 */
    public void placeSparks(int[] centerX, int[] centerY) {
        for (Spark spark : pool) {
            if (spark.life > 0 && !spark.placed && spark.player < centerX.length) {
                spark.x = centerX[spark.player];
                spark.y = centerY[spark.player];
                spark.placed = true;
            }
        }
        for (Wave wave : waves) {
            if (wave != null && wave.life > 0 && !wave.placed && wave.player < centerX.length) {
                wave.x = centerX[wave.player];
                wave.y = centerY[wave.player];
                wave.placed = true;
            }
        }
        if (glowLife > 0 && !glowPlaced && centerX.length > 0) {
            glowX = centerX[0];
            glowY = centerY[0];
            glowPlaced = true;
        }
    }

    private static final class Spark {
        int player;
        double x;
        double y;
        double vx;
        double vy;
        int rgb;
        int life;
        boolean placed;

        void arm(int player, double vx, double vy, int rgb, int life) {
            this.player = player;
            this.vx = vx;
            this.vy = vy;
            this.rgb = 0xff000000 | rgb;
            this.life = life;
            this.placed = false;
            this.x = 0;
            this.y = 0;
        }

        void tick() {
            x += vx;
            y += vy;
            vx *= 0.94;
            vy = vy * 0.94 + 0.08;
            life--;
        }

        void paint(Graphics2D g) {
            int alpha = Math.min(255, life * 16);
            g.setColor(new Color((rgb & 0x00ffffff) | (alpha << 24), true));
            int s = 2 + life / 6;
            g.fillOval((int) x, (int) y, s, s);
        }
    }

    private static final class Wave {
        int player;
        int x;
        int y;
        int radius = 8;
        int life;
        int rgb;
        boolean placed;

        void arm(int player, int rgb) {
            this.player = player;
            this.rgb = rgb;
            this.life = 12;
            this.radius = 8;
            this.placed = false;
        }

        void tick() {
            radius += 4;
            life--;
        }

        void paint(Graphics2D g) {
            int alpha = Math.min(180, life * 16);
            g.setColor(new Color((rgb & 0xff0000) >> 16, (rgb >> 8) & 0xff, rgb & 0xff, alpha));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(x - radius, y - radius / 2, radius * 2, radius);
            g.setStroke(new BasicStroke(1f));
        }
    }

    private static final class Floater {
        final int player;
        final String text;
        final int rgb;
        final boolean crit;
        final boolean onBoss;
        final int life;
        int age;

        Floater(int player, String text, int rgb, int life, boolean crit, boolean onBoss) {
            this.player = player;
            this.text = text;
            this.rgb = 0xff000000 | rgb;
            this.life = life;
            this.crit = crit;
            this.onBoss = onBoss;
        }
    }

    private static final class Banner {
        final String text;
        final int rgb;
        int life;

        Banner(String text, int rgb, int life) {
            this.text = text;
            this.rgb = 0xff000000 | rgb;
            this.life = life;
        }
    }
}
