package tetris.ui.input;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JPanel;
import tetris.feature.raid.api.BossId;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.BattleMode;
import tetris.ui.GamePanel;
import tetris.ui.MainMenuPanel;
import tetris.ui.MenuItem;
import tetris.ui.PausePanel;
import tetris.ui.fx.AssetStore;
import tetris.ui.raid.RaidPanel;
import tetris.ui.raid.RaidSamples;
import tetris.ui.versus.BattlePanel;

/**
 * -Xmx256m 으로 메뉴 → 싱글 → 대전 → 보스 5종 → 전체 화면 → 메뉴.
 * 표준 출력에 peakUsedMb 와 afterGcMb 를 남긴다.
 */
public final class MemorySmoke {
    private static long peak;
    public static void main(String[] args) {
        walk();
        System.gc();
        long settled = used();
        System.out.println("peakUsedMb=" + (peak / (1024 * 1024)));
        System.out.println("afterGcMb=" + (settled / (1024 * 1024)));
        System.out.println("maxMb=" + (Runtime.getRuntime().maxMemory() / (1024 * 1024)));
        if (peak >= 200L * 1024 * 1024) {
            throw new IllegalStateException("peak heap " + peak + " exceeds 200MB");
        }
    }

    static void walk() {
        phase(() -> {
            AssetStore.SHARED.enterScreen("menu");
            paint(menu(), logical().width, logical().height);
        });
        phase(() -> {
            AssetStore.SHARED.enterScreen("game");
            GamePanel game = new GamePanel(new Settings(), new PausePanel(() -> { }, () -> { }, () -> { }), snapshot -> { });
            game.setCellSize(ScreenSize.MEDIUM.cellPixels());
            paint(game, logical().width, logical().height);
        });
        phase(() -> {
            AssetStore.SHARED.enterScreen("battle");
            BattlePanel battle = new BattlePanel(false, "대전", "클래식", BattleMode.CLASSIC, null);
            paint(battle, logical().width, logical().height);
            AssetStore.SHARED.cover(AssetStore.BG_VERSUS, 1920, 1080);
        });
        AssetStore.SHARED.enterScreen("raid");
        RaidPanel raid = new RaidPanel(false);
        for (BossId boss : BossId.values()) {
            phase(() -> {
                AssetStore.SHARED.retainBoss(boss);
                raid.setSnapshot(RaidSamples.snapshot(
                        tetris.feature.raid.api.RaidMode.SOLO,
                        tetris.feature.raid.api.RaidPhase.PLAYING,
                        boss, 2, 400, 600, tetris.feature.raid.api.PatternId.ROCK_THROW,
                        0, 3, new int[0], -1, 2, 10_000L, 40, null, List.of(), null));
                paint(raid, logical().width, logical().height);
                AssetStore.SHARED.boss(boss, 1, 320);
                AssetStore.SHARED.boss(boss, 3, 320);
                AssetStore.SHARED.ledge(boss, 2, 900);
            });
        }
        phase(() -> paintStage(1920, 1080, logical()));
        phase(() -> paintStage(2560, 1440, logical()));
        phase(() -> {
            AssetStore.SHARED.enterScreen("menu");
            paint(menu(), logical().width, logical().height);
        });
    }

    private static Dimension logical() {
        return GamePanel.preferredSizeFor(ScreenSize.MEDIUM.cellPixels());
    }

    private static void phase(Runnable work) {
        System.gc();
        work.run();
        peak = Math.max(peak, used());
    }


    private static void paintStage(int width, int height, Dimension logical) {
        JPanel deck = new JPanel(new java.awt.CardLayout());
        MainMenuPanel menu = menu();
        deck.add(menu, "MENU");
        ((java.awt.CardLayout) deck.getLayout()).show(deck, "MENU");
        Stage stage = new Stage(deck);
        stage.setLogicalSize(logical.width, logical.height);
        stage.setBackdrop(AssetStore.BG_MENU);
        stage.setPauseAction(() -> { });
        stage.setSize(width, height);
        stage.doLayout();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        stage.paint(g);
        g.dispose();
        image.flush();
    }

    private static MainMenuPanel menu() {
        return new MainMenuPanel(List.of(new MenuItem("시작", () -> { })));
    }

    private static void paint(javax.swing.JComponent panel, int width, int height) {
        panel.setSize(width, height);
        panel.doLayout();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();
        image.flush();
    }


    private static long used() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
