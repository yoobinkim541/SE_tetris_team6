package tetris.ui.theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import tetris.ui.fx.StarfieldBackground;

/**
 * 줄 삭제 입자, 짧은 흔들림, 팝업, 점수 오름, 레벨 배너, 카운트다운.
 * 연출이 낮으면 입자와 흔들림만 끄고, 점수·삭제 표시는 남긴다.
 */
public final class PlayFx {
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Dust> dust = new ArrayList<>();
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

    public void resetScore(long score) {
        shownScore = score;
        targetScore = score;
    }

    public void noteScore(long score) {
        if (shownScore == Long.MIN_VALUE) shownScore = score;
        targetScore = score;
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
        if (!StarfieldBackground.lowEffects()) burst(centerX, centerY, lines >= 4 ? 0x3DE0FF : 0xFFFFFF);
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
        shake = 5;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            dust.add(new Dust(x, y, Math.cos(angle) * 1.6, -Math.abs(Math.sin(angle)) * 1.2 - 0.4));
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
        Iterator<Spark> sparksLeft = sparks.iterator();
        while (sparksLeft.hasNext()) {
            if (!sparksLeft.next().tick()) sparksLeft.remove();
        }
        Iterator<Dust> dustLeft = dust.iterator();
        while (dustLeft.hasNext()) {
            if (!dustLeft.next().tick()) dustLeft.remove();
        }
    }

    public boolean active() {
        return shake > 0 || popupLife > 0 || bannerLife > 0 || countdownStep >= 0
                || !sparks.isEmpty() || !dust.isEmpty() || shownScore != targetScore;
    }

    public int shakeX() {
        return StarfieldBackground.lowEffects() ? 0 : shake * shakeDir;
    }

    public void paintSparks(Graphics2D g) {
        if (StarfieldBackground.lowEffects()) return;
        for (Spark spark : sparks) spark.paint(g);
        for (Dust bit : dust) bit.paint(g);
    }

    public void paintPopup(Graphics2D g, int centerX, int baseline, int windowHeight) {
        if (popupLife <= 0 || popup.isEmpty()) return;
        float alpha = popupLife > 8 ? 1f : popupLife / 8f;
        Font font = GameTheme.popup(windowHeight);
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        UiKit.paintGlowText(g, popup, font, popupColor, centerX, baseline);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
    }

    public void paintBanner(Graphics2D g, int centerX, int y, int width) {
        if (bannerLife <= 0 || banner.isEmpty()) return;
        int w = Math.min(width - 16, Math.max(160, banner.length() * 18));
        int h = 36;
        int x = centerX - w / 2;
        UiKit.paintBanner(g, x, y, w, h);
        g.setFont(GameTheme.digits(22));
        g.setColor(Color.WHITE);
        UiKit.drawCentered(g, banner, centerX, y + 25);
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

    private void burst(int x, int y, int rgb) {
        for (int i = 0; i < 14; i++) {
            double angle = i * Math.PI * 2 / 14;
            double speed = 1.2 + (i % 3) * 0.45;
            sparks.add(new Spark(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed - 0.6, rgb));
        }
    }

    private static final class Spark {
        double x;
        double y;
        double vx;
        double vy;
        int life = 18;
        final int rgb;

        Spark(double x, double y, double vx, double vy, int rgb) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.rgb = rgb;
        }

        boolean tick() {
            x += vx;
            y += vy;
            vy += 0.08;
            return --life > 0;
        }

        void paint(Graphics2D g) {
            int alpha = Math.min(255, life * 14);
            g.setColor(new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, alpha));
            int s = 2 + life / 8;
            g.fillOval((int) x, (int) y, s, s);
        }
    }

    private static final class Dust {
        double x;
        double y;
        double vx;
        double vy;
        int life = 12;

        Dust(double x, double y, double vx, double vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }

        boolean tick() {
            x += vx;
            y += vy;
            vy += 0.12;
            return --life > 0;
        }

        void paint(Graphics2D g) {
            g.setColor(new Color(180, 220, 255, Math.min(180, life * 16)));
            g.fillRect((int) x, (int) y, 2, 2);
        }
    }
}
