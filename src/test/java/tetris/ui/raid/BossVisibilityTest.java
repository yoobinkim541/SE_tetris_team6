package tetris.ui.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;

/**
 * 첫 처치 연출이 끝난 뒤에도 다음 보스가 8% 알파로 정적 층에 구워지면 안 된다.
 * 처치 카운터가 0에 머물면 defeatAlpha가 0이 되고, 플레이트 키는 그 상태를 기억하지 않는다.
 */
class BossVisibilityTest {
    @Test
    void defeatFadeEndsAndTheNextBossIsFullyOpaque() {
        BossMotion motion = new BossMotion();
        motion.note(BossId.JELLY_KING, 1, 100, 100, List.of(spawn(BossId.JELLY_KING)));
        assertEquals(1f, motion.defeatAlpha(), 0.001f);

        motion.note(BossId.JELLY_KING, 1, 0, 100, List.of(new RaidEvent(RaidEvent.Type.BOSS_DEFEATED, -1, 0)));
        motion.tick();
        assertTrue(motion.defeatAlpha() < 1f, "처치 연출이 진행 중이면 알파는 1보다 작다");
        for (int i = 0; i < 40; i++) motion.tick();
        assertEquals(1f, motion.defeatAlpha(), 0.001f, "연출이 끝나면 알파가 0에 머물면 안 된다");

        motion.note(BossId.STONE_GOLEM, 3, 258, 280, List.of(spawn(BossId.STONE_GOLEM)));
        assertEquals(1f, motion.defeatAlpha(), 0.001f);
    }

    @Test
    void secondBossIsNotBakedAsAGhost() {
        RaidPanel afterKill = new RaidPanel(false);
        afterKill.setSnapshot(playing(1, BossId.JELLY_KING, 1, 1, List.of(new RaidEvent(RaidEvent.Type.BOSS_DEFEATED, -1, 0))));
        afterKill.advance(40);
        afterKill.setSnapshot(playing(2, BossId.MECHA_DRAGON, 3, 2, List.of(spawn(BossId.MECHA_DRAGON))));
        BufferedImage faded = render(afterKill);

        RaidPanel fresh = new RaidPanel(false);
        fresh.setSnapshot(playing(1, BossId.MECHA_DRAGON, 3, 2, List.of(spawn(BossId.MECHA_DRAGON))));
        BufferedImage solid = render(fresh);

        var window = afterKill.bossBounds();
        double diff = meanAbs(faded, solid, window.x, window.y, window.width, window.height);
        assertTrue(diff < 12, "처치 뒤 보스가 정적 층에 흐리게 구워짐 diff=" + diff);
        afterKill.removeNotify();
        fresh.removeNotify();
    }

    private static RaidEvent spawn(BossId id) {
        return new RaidEvent(RaidEvent.Type.BOSS_SPAWN, -1, id.ordinal());
    }

    private static RaidSnapshot playing(long revision, BossId id, int level, int kills, List<RaidEvent> events) {
        return new RaidSnapshot("visibility", revision, RaidMode.SOLO, RaidPhase.PLAYING,
                new RaidSnapshot.BossView(id, level, 258, 280, PatternId.FIRE_BREATH, 0, 1, new int[0], -1),
                List.of(RaidSamples.fighter(false)), kills, 180_000L, 40, null, events, null);
    }

    private static BufferedImage render(RaidPanel panel) {
        panel.setSize(RaidPanel.VW, RaidPanel.VH);
        BufferedImage image = new BufferedImage(RaidPanel.VW, RaidPanel.VH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static double meanAbs(BufferedImage left, BufferedImage right, int x, int y, int width, int height) {
        long sum = 0;
        int count = 0;
        int x1 = Math.min(left.getWidth(), Math.min(right.getWidth(), x + width));
        int y1 = Math.min(left.getHeight(), Math.min(right.getHeight(), y + height));
        for (int row = Math.max(0, y); row < y1; row += 2) {
            for (int col = Math.max(0, x); col < x1; col += 2) {
                sum += Math.abs(luma(left.getRGB(col, row)) - luma(right.getRGB(col, row)));
                count++;
            }
        }
        return count == 0 ? 255 : sum / (double) count;
    }

    private static int luma(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (r * 3 + g * 6 + b) / 10;
    }
}
