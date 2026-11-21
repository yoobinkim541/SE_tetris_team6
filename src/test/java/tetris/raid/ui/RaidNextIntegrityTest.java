package tetris.raid.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.IBlock;
import tetris.component.block.JBlock;
import tetris.component.block.OBlock;
import tetris.component.block.SBlock;
import tetris.component.block.TBlock;
import tetris.component.block.ZBlock;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.raid.RaidEngine;
import tetris.feature.raid.api.RaidConfig;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.setting.ControlStyle;
import tetris.feature.versus.api.MatchClock;
import tetris.ui.BlockPalette;
import tetris.ui.GamePanel;
import tetris.ui.raid.RaidPanel;
import tetris.ui.raid.RaidSamples;

/** 모던 레이드 NEXT 3개, 봉인 중에도 바로 다음은 그려지고, 저주는 보인 다음을 갈아끼우지 않는다 */
@Timeout(30)
class RaidNextIntegrityTest {
    @Test
    void modernEnginePassesThreePreviews() {
        MatchClock clock = () -> 0L;
        RaidEngine solo = new RaidEngine(new RaidConfig(RaidMode.SOLO, 7, ControlStyle.MODERN), clock);
        RaidEngine coop = new RaidEngine(new RaidConfig(RaidMode.COOP, 9, ControlStyle.MODERN), clock);
        try {
            assertEquals(3, solo.snapshot().players().get(0).board().previews().size());
            assertEquals(3, coop.snapshot().players().get(0).board().previews().size());
            assertEquals(3, coop.snapshot().players().get(1).board().previews().size());
        } finally {
            solo.close();
            coop.close();
        }
    }

    @Test
    void curseSpawnsThePreviouslyShownNext() {
        Queue<Block> pieces = new ArrayDeque<>();
        pieces.add(new IBlock());
        pieces.add(new OBlock());
        pieces.add(new TBlock());
        pieces.add(new JBlock());
        pieces.add(new OBlock());
        pieces.add(new OBlock());
        GameSession session = new GameSession(GameMode.CLASSIC, Difficulty.NORMAL,
                (BlockGenerator) () -> pieces.remove().copy(), silentClock(), silentListener(), ControlStyle.MODERN);
        session.start();
        BlockType shown = session.peekUpcomingType(0);
        assertEquals(BlockType.O, shown);
        session.forceUpcoming(new SBlock(), new ZBlock());
        assertEquals(shown, session.peekUpcomingType(0), "이미 보여 준 바로 다음은 그대로다");
        assertEquals(BlockType.S, session.peekUpcomingType(1));
        assertEquals(BlockType.Z, session.peekUpcomingType(2));
        session.hardDrop();
        if (session.getState() == GameState.CLEARING) session.tick();
        assertEquals(shown, session.snapshot().currentBlock().getType(), "저주 뒤에도 직전에 보인 다음이 나온다");
    }

    @Test
    void sealedColorBlindRenderStillDrawsTheImmediateNext() {
        RaidPanel panel = new RaidPanel(true);
        panel.setSnapshot(RaidSamples.playing(RaidMode.SOLO));
        var box = GamePanel.preferredSizeFor(30);
        BufferedImage image = render(panel, box.width, box.height);
        var next = panel.cueBounds("next");
        assertTrue(next.width > 2 && next.height > 2, "next cue " + next);
        int nextColor = new BlockPalette().color(BlockType.I, true).getRGB() & 0xffffff;
        assertTrue(contains(image, next, nextColor), "봉인 중에도 바로 다음 블록 색이 그려진다");
    }

    private static BufferedImage render(RaidPanel panel, int width, int height) {
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
            panel.removeNotify();
        }
        return image;
    }

    private static boolean contains(BufferedImage image, java.awt.Rectangle area, int rgb) {
        int x0 = Math.max(0, area.x);
        int y0 = Math.max(0, area.y);
        int x1 = Math.min(image.getWidth(), area.x + area.width);
        int y1 = Math.min(image.getHeight(), area.y + area.height);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((image.getRGB(x, y) & 0xffffff) == rgb) return true;
            }
        }
        return false;
    }

    private static GameClock silentClock() {
        return new GameClock() {
            public void start(int intervalMillis) { }
            public void stop() { }
        };
    }

    private static GameListener silentListener() {
        return new GameListener() {
            public void onChanged(GameSnapshot snapshot) { }
            public void onGameOver(GameSnapshot snapshot) { }
        };
    }
}
