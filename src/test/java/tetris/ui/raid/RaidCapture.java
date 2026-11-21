package tetris.ui.raid;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.ScreenSize;
import tetris.ui.GamePanel;

/** 레이드 화면 검수용 오프스크린 캡처. 테스트 스위트에는 넣지 않는다 */
public final class RaidCapture {
    private static final Path OUT = Path.of(System.getProperty("tetris.capture.dir", "build/verification/raid"));

    private RaidCapture() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        var medium = GamePanel.preferredSizeFor(ScreenSize.MEDIUM.cellPixels());
        var small = GamePanel.preferredSizeFor(ScreenSize.SMALL.cellPixels());
        var large = GamePanel.preferredSizeFor(ScreenSize.LARGE.cellPixels());
        capture("coop", panel(false, aliveCoop()), medium.width, medium.height);
        capture("coop-downed", panel(false, RaidSamples.playing(RaidMode.COOP)), medium.width, medium.height);
        capture("solo", panel(false, RaidSamples.playing(RaidMode.SOLO)), medium.width, medium.height);
        capture("race", panel(false, race()), medium.width, medium.height);
        capture("cards", panel(false, RaidSamples.phase(RaidMode.COOP, RaidPhase.CARD_PICK)), medium.width, medium.height);
        capture("result", panel(false, RaidSamples.phase(RaidMode.COOP, RaidPhase.FINISHED)), medium.width, medium.height);
        capture("coop-small", panel(false, aliveCoop()), small.width, small.height);
        capture("solo-small", panel(false, RaidSamples.playing(RaidMode.SOLO)), small.width, small.height);
        capture("race-small", panel(false, race()), small.width, small.height);
        capture("coop-large", panel(false, aliveCoop()), large.width, large.height);
        capture("coop-colorblind", panel(true, aliveCoop()), medium.width, medium.height);
    }

    private static RaidPanel panel(boolean colorBlind, RaidSnapshot snapshot) {
        RaidPanel panel = new RaidPanel(colorBlind);
        panel.setSnapshot(snapshot);
        panel.setGuide("P1 A D 이동 · R 필살");
        panel.setCardSelection(1);
        return panel;
    }

    private static RaidSnapshot aliveCoop() {
        var boss = new RaidSnapshot.BossView(BossId.STONE_GOLEM, 2, 412, 580, PatternId.ROCK_THROW,
                0, 3, new int[0], -1);
        return new RaidSnapshot("raid-preview", 2, RaidMode.COOP, RaidPhase.PLAYING, boss,
                List.of(RaidSamples.fighter(true), RaidSamples.fighter(false)),
                3, 192_000L, 62, null, List.of(), null);
    }

    private static RaidSnapshot race() {
        var one = new RaidSnapshot.BossView(BossId.STONE_GOLEM, 2, 180, 400, PatternId.ROCK_THROW,
                0, 2, new int[0], -1);
        var two = new RaidSnapshot.BossView(BossId.NIGHT_WITCH, 2, 260, 400, PatternId.SEAL_PREVIEW,
                1, 5, new int[0], -1);
        var stolen = List.of(new RaidEvent(RaidEvent.Type.BOSS_DEFEATED, 0, BossId.STONE_GOLEM.ordinal()));
        return new RaidSnapshot("raid-preview", 3, RaidMode.RACE, RaidPhase.PLAYING, one,
                List.of(RaidSamples.fighter(true), RaidSamples.fighter(false)),
                4, 192_000L, 40, null, stolen, null,
                List.of(one, two), new int[] {3, 1}, new int[] {80, 35});
    }

    private static void capture(String name, JComponent panel, int width, int height) throws Exception {
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
            panel.removeNotify();
        }
        ImageIO.write(image, "png", OUT.resolve(name + ".png").toFile());
    }
}
