package tetris.ui.fx;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import tetris.feature.raid.api.RaidEvent;

/**
 * 줄 삭제 불꽃, 화면 흔들림, 데미지 숫자, 가운데 큰 문구, 보스 피격 번쩍임.
 * 스냅샷의 {@link RaidEvent}만 보고 그린다. 타이머는 화면이 하나 돌린다.
 */
public final class FxLayer {
    private static final int BIG_DAMAGE = 70;

    private final List<Spark> sparks = new ArrayList<>();
    private final List<Floater> numbers = new ArrayList<>();
    private final List<Banner> banners = new ArrayList<>();
    private int shake;
    private int shakeDir = 1;
    private int flash;
    private boolean colorBlind;

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
        if (flash > 0) flash = Math.max(0, flash - 18);
        sparks.removeIf(spark -> --spark.life <= 0);
        for (Spark spark : sparks) {
            spark.x += spark.vx;
            spark.y += spark.vy;
            spark.vy += 0.15;
        }
        Iterator<Floater> numbersLeft = numbers.iterator();
        while (numbersLeft.hasNext()) {
            Floater floater = numbersLeft.next();
            floater.y -= 1.4;
            if (--floater.life <= 0) numbersLeft.remove();
        }
        banners.removeIf(banner -> --banner.life <= 0);
    }

    /** 보드를 옆으로 밀 픽셀. 4줄(데미지 70)과 필살은 크게 */
    public int shakeX() {
        if (shake <= 0) return 0;
        int amp = shake > 8 ? 7 : 3;
        return shakeDir * Math.min(amp, shake);
    }

    public int flashAlpha() {
        return flash;
    }

    public void paintSparks(Graphics2D g) {
        if (StarfieldBackground.lowEffects()) return;
        for (Spark spark : sparks) {
            g.setColor(new Color(spark.rgb, true));
            g.drawLine((int) spark.x, (int) spark.y, (int) (spark.x - spark.vx * 3), (int) (spark.y - spark.vy * 3));
        }
    }

    /** boardX/boardTop: 참가자 0, 1의 보드 가운데·위. 없는 참가자는 건너뛴다 */
    public void paintNumbers(Graphics2D g, int[] boardCenterX, int boardTop) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 22));
        for (Floater floater : numbers) {
            if (floater.player < 0 || floater.player >= boardCenterX.length) continue;
            int alpha = Math.min(255, floater.life * 8);
            int rgb = colorBlind ? (floater.crit ? 0xE69F00 : 0x56B4E9) : floater.rgb;
            g.setColor(new Color((rgb & 0x00ffffff) | (alpha << 24), true));
            String text = floater.crit ? "치명 " + floater.text : floater.text;
            int x = boardCenterX[floater.player] - g.getFontMetrics().stringWidth(text) / 2;
            g.drawString(text, x, boardTop + (int) floater.y);
        }
    }

    public void paintBanner(Graphics2D g, int centerX, int centerY) {
        if (banners.isEmpty()) return;
        Banner banner = banners.get(banners.size() - 1);
        int alpha = Math.min(255, banner.life * 10);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 42));
        g.setColor(new Color(0, 0, 0, alpha / 2));
        int width = g.getFontMetrics().stringWidth(banner.text);
        g.drawString(banner.text, centerX - width / 2 + 2, centerY + 2);
        int rgb = colorBlind ? 0xF0E442 : banner.rgb;
        g.setColor(new Color((rgb & 0x00ffffff) | (alpha << 24), true));
        g.drawString(banner.text, centerX - width / 2, centerY);
    }

    private void absorb(RaidEvent event) {
        switch (event.type()) {
            case DAMAGE -> {
                addNumber(event.player(), String.valueOf(event.value()), 0xFFD36A);
                burst(event.player(), 0x7EE7FF);
                shake = Math.max(shake, event.value() >= BIG_DAMAGE ? 14 : 6);
                flash = Math.max(flash, event.value() >= BIG_DAMAGE ? 180 : 110);
            }
            case CRITICAL -> {
                addNumber(event.player(), String.valueOf(event.value()), 0xFFE27A, true);
                banner(colorBlind ? "치명타!" : "CRITICAL!", 0xFFE27A);
                burst(event.player(), 0xFFD36A);
                shake = Math.max(shake, 12);
                flash = 200;
            }
            case COMBO -> banner(event.value() + " COMBO!", 0x7EE7FF);
            case TSPIN -> banner("T-SPIN", 0xE85CFF);
            case SYNC -> addNumber(event.player(), "+" + event.value(), 0x7EE7FF);
            case BLOCKED, SHIELDED -> banner("BLOCKED!", 0x8BE7FF);
            case REVIVED -> banner("REVIVED!", 0x8CFFC1);
            case ULTIMATE -> {
                addNumber(event.player(), String.valueOf(event.value()), 0xE85CFF);
                banner("필살!", 0xE85CFF);
                shake = 16;
                flash = 220;
                burst(0, 0xE85CFF);
                burst(1, 0x7EE7FF);
            }
            case BOSS_ATTACK -> flash = Math.max(flash, 70);
            case BOSS_DEFEATED -> { if (event.player() >= 0) banner("선수를 빼앗김", 0xFFC94D); }
            default -> {
            }
        }
    }

    private void addNumber(int player, String text, int rgb) {
        addNumber(player, text, rgb, false);
    }

    private void addNumber(int player, String text, int rgb, boolean crit) {
        if (player < 0) return;
        numbers.add(new Floater(player, text, rgb, 28, crit));
    }

    private void banner(String text, int rgb) {
        banners.add(new Banner(text, rgb, 46));
    }

    private void burst(int player, int rgb) {
        if (player < 0 || StarfieldBackground.lowEffects()) return;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            sparks.add(new Spark(player, Math.cos(angle) * 2.2, -2 + Math.sin(angle), rgb, 16));
        }
    }

    private static final class Spark {
        final int player;
        double x;
        double y;
        double vx;
        double vy;
        final int rgb;
        int life;

        Spark(int player, double vx, double vy, int rgb, int life) {
            this.player = player;
            this.vx = vx;
            this.vy = vy;
            this.rgb = 0xff000000 | rgb;
            this.life = life;
        }
    }

    private static final class Floater {
        final int player;
        final String text;
        final int rgb;
        final boolean crit;
        double y = 36;
        int life;

        Floater(int player, String text, int rgb, int life, boolean crit) {
            this.player = player;
            this.text = text;
            this.rgb = 0xff000000 | rgb;
            this.life = life;
            this.crit = crit;
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

    /** 불꽃의 시작점. 패널이 보드 좌표를 알고 나서 채운다 */
    public void placeSparks(int[] centerX, int[] centerY) {
        for (Spark spark : sparks) {
            if (spark.x == 0 && spark.y == 0 && spark.player < centerX.length) {
                spark.x = centerX[spark.player];
                spark.y = centerY[spark.player];
            }
        }
    }
}
