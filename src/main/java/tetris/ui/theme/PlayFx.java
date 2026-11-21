package tetris.ui.theme;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.image.BufferedImage;
import tetris.ui.fx.StarfieldBackground;

/**
 * 줄 삭제 입자, 짧은 흔들림, 팝업, 점수 오름, 레벨 배너, 카운트다운.
 * 연출이 낮으면 입자와 흔들림만 끄고, 점수·삭제 표시는 남긴다.
 */
public final class PlayFx {
    private static final int POOL = 40;

    private final Spark[] sparks = new Spark[POOL];
    private final Dust[] dust = new Dust[16];
    private final Wave wave = new Wave();
    private BufferedImage glow;
    private int glowX;
    private int glowY;
    private int glowLife;
    private String popup = "";
    private Color popupColor = Color.WHITE;
    private int popupLife;
    private String banner = "";
    private int bannerLife;
    private int shake;
    private int shakeDir = 1;
    private long shownScore = Long.MIN_VALUE;
    private long targetScore;
    private int combo;
    private boolean lastSpecial;
    private boolean backToBack;
    private int countdownStep = -1;
    private int countdownLife;
    private float countdownScale = 1f;

    public PlayFx() {
        for (int i = 0; i < sparks.length; i++) sparks[i] = new Spark();
        for (int i = 0; i < dust.length; i++) dust[i] = new Dust();
    }

    public void resetScore(long score) {
        shownScore = score;
        targetScore = score;
    }

    public void noteScore(long score) {
        targetScore = score;
        if (shownScore == Long.MIN_VALUE) shownScore = score;
    }

    public long displayScore() {
        return shownScore == Long.MIN_VALUE ? 0 : shownScore;
    }

    /** 줄 삭제. lines가 0이면 콤보를 끊는다 */
    public void noteClear(int lines, boolean tSpin, boolean mini, int centerX, int centerY) {
        if (lines <= 0) {
            combo = 0;
            return;
        }
        combo++;
        boolean special = lines >= 4 || tSpin;
        backToBack = special && lastSpecial;
        lastSpecial = special;
        if (!StarfieldBackground.lowEffects()) {
            burst(centerX, centerY, lines >= 4 ? 0x3DE0FF : 0xFFFFFF, 12 + lines);
            wave.arm(centerX, centerY, lines >= 4 ? 0x3DE0FF : 0xF4FBFF);
            glowX = centerX;
            glowY = centerY;
            glowLife = 7;
            shake = Math.min(4, 1 + lines);
        }
        if (lines >= 4) showPopup("TETRIS", new Color(0x3DE0FF));
        else if (tSpin) showPopup(mini ? "T-SPIN MINI" : "T-SPIN", new Color(0xFF4AD8));
        else if (combo >= 2) showPopup(combo + " COMBO", Color.WHITE);
        if (backToBack && special) showPopup("BACK-TO-BACK", new Color(0xF0E442));
    }

    public void noteLevel(int level) {
        banner = "LEVEL " + level;
        bannerLife = 46;
    }

    public void noteHardDrop(int x, int y) {
        if (StarfieldBackground.lowEffects()) return;
        shake = 3;
        int spawned = 0;
        for (Dust bit : dust) {
            if (bit.life > 0) continue;
            double angle = spawned * Math.PI / 4;
            bit.arm(x, y, Math.cos(angle) * 1.6, -Math.abs(Math.sin(angle)) * 1.2 - 0.4);
            if (++spawned >= 8) return;
        }
    }

    public void armCountdown() {
        countdownStep = 3;
        countdownLife = 16;
        countdownScale = 1.4f;
    }

    public void tick() {
        if (shownScore < targetScore) {
            long step = Math.max(1, (targetScore - shownScore) / 8);
            shownScore = Math.min(targetScore, shownScore + step);
        } else if (shownScore > targetScore) {
            shownScore = targetScore;
        }
        if (shake > 0) {
            shake--;
            shakeDir = -shakeDir;
        }
        if (popupLife > 0) popupLife--;
        if (bannerLife > 0) bannerLife--;
        if (glowLife > 0) glowLife--;
        if (countdownLife > 0) {
            countdownLife--;
            countdownScale = 1f + countdownLife / 16f * 0.45f;
            if (countdownLife == 0 && countdownStep > 0) {
                countdownStep--;
                countdownLife = countdownStep == 0 ? 22 : 16;
                countdownScale = 1.35f;
            } else if (countdownLife == 0) {
                countdownStep = -1;
            }
        }
        for (Spark spark : sparks) if (spark.life > 0) spark.tick();
        for (Dust bit : dust) if (bit.life > 0) bit.tick();
        if (wave.life > 0) wave.tick();
    }

    public boolean active() {
        if (shake > 0 || popupLife > 0 || bannerLife > 0 || countdownStep >= 0 || glowLife > 0 || wave.life > 0) {
            return true;
        }
        if (shownScore != targetScore) return true;
        for (Spark spark : sparks) if (spark.life > 0) return true;
        for (Dust bit : dust) if (bit.life > 0) return true;
        return false;
    }

    public int shakeX() {
        return StarfieldBackground.lowEffects() ? 0 : shake * shakeDir;
    }

    public void paintSparks(Graphics2D g) {
        if (StarfieldBackground.lowEffects()) return;
        if (glowLife > 0) paintGlow(g);
        if (wave.life > 0) wave.paint(g);
        for (Spark spark : sparks) if (spark.life > 0) spark.paint(g);
        for (Dust bit : dust) if (bit.life > 0) bit.paint(g);
    }

    public void paintPopup(Graphics2D g, int centerX, int baseline, int windowHeight) {
        if (popupLife <= 0 || popup.isEmpty()) return;
        float alpha = popupLife > 8 ? 1f : popupLife / 8f;
        int size = Math.max(16, windowHeight / 18);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setFont(GameTheme.display(size));
        UiKit.drawInBox(g, popup, new java.awt.Rectangle(centerX - windowHeight / 3, baseline - size, windowHeight * 2 / 3, size + 8),
                UiKit.Align.CENTER);
        g.setComposite(AlphaComposite.SrcOver);
    }

    public void paintBanner(Graphics2D g, int centerX, int y, int width) {
        if (bannerLife <= 0 || banner.isEmpty()) return;
        int w = Math.min(width - 16, Math.max(160, banner.length() * 18));
        int h = 36;
        int x = centerX - w / 2;
        UiKit.paintBanner(g, x, y, w, h);
        g.setFont(GameTheme.display(22));
        g.setColor(java.awt.Color.WHITE);
        UiKit.drawInAsset(g, UiKit.AssetSlot.BANNER, new java.awt.Rectangle(x, y, w, h), banner, UiKit.Align.CENTER);
    }

    /** 3, 2, 1, GO. 진행 중이 아니면 빈 문자열 */
    public String countdownText() {
        if (countdownStep < 0) return "";
        return countdownStep == 0 ? "GO" : Integer.toString(countdownStep);
    }

    public float countdownScale() {
        return countdownScale;
    }

    public void clearCountdown() {
        countdownStep = -1;
        countdownLife = 0;
    }

    private void showPopup(String text, Color color) {
        popup = text;
        popupColor = color;
        popupLife = 36;
    }

    private void burst(int x, int y, int rgb, int count) {
        int spawned = 0;
        for (Spark spark : sparks) {
            if (spark.life > 0) continue;
            double angle = spawned * Math.PI * 2 / count;
            double speed = 1.1 + (spawned % 3) * 0.4;
            spark.arm(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed - 0.5, rgb);
            if (++spawned >= count) return;
        }
    }

    private void paintGlow(Graphics2D g) {
        if (glow == null) glow = glowSprite(64);
        Composite previous = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.12f, glowLife / 8f)));
        g.drawImage(glow, glowX - glow.getWidth() / 2, glowY - glow.getHeight() / 2, null);
        g.setComposite(previous);
    }

    private static BufferedImage glowSprite(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setPaint(new RadialGradientPaint(size / 2f, size / 2f, size / 2f, new float[] {0f, 0.4f, 1f},
                    new Color[] {new Color(255, 255, 255, 200), new Color(90, 220, 255, 80), new Color(60, 160, 255, 0)}));
            g.fillOval(0, 0, size, size);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static final class Spark {
        double x;
        double y;
        double vx;
        double vy;
        int life;
        int rgb;

        void arm(double x, double y, double vx, double vy, int rgb) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.rgb = rgb;
            this.life = 16;
        }

        void tick() {
            x += vx;
            y += vy;
            vx *= 0.93;
            vy = vy * 0.93 + 0.07;
            life--;
        }

        void paint(Graphics2D g) {
            int alpha = Math.min(220, life * 16);
            int s = 4 + life / 6;
            UiKit.softDot(g, (int) x, (int) y, s, new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, alpha));
        }
    }

    private static final class Dust {
        double x;
        double y;
        double vx;
        double vy;
        int life;

        void arm(double x, double y, double vx, double vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = 12;
        }

        void tick() {
            x += vx;
            y += vy;
            vy += 0.12;
            life--;
        }

        void paint(Graphics2D g) {
            UiKit.softDot(g, (int) x, (int) y, 4, new Color(180, 220, 255, Math.min(180, life * 16)));
        }
    }

    private static final class Wave {
        int x;
        int y;
        int radius;
        int life;
        int rgb;

        void arm(int x, int y, int rgb) {
            this.x = x;
            this.y = y;
            this.rgb = rgb;
            this.radius = 6;
            this.life = 10;
        }

        void tick() {
            radius += 5;
            life--;
        }

        void paint(Graphics2D g) {
            int alpha = Math.min(170, life * 18);
            g.setColor(new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, alpha));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(x - radius, y - radius / 3, radius * 2, (int) (radius * 0.7));
            g.setStroke(new BasicStroke(1f));
        }
    }
}
