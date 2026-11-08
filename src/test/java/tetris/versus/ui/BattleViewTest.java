package tetris.versus.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import tetris.ui.versus.*;

class BattleViewTest {
    @Test void drawingBoardPreservesTextStyleOfCaller() {
        var image=new BufferedImage(50,50,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        var font=new java.awt.Font(java.awt.Font.DIALOG,java.awt.Font.BOLD,22); g.setFont(font);
        new BoardRenderer(false).paint(g,new int[][]{{1}},0,0,20);
        assertEquals(java.awt.Color.WHITE,g.getColor()); assertEquals(font,g.getFont()); g.dispose();
    }

    @Test void attackDiffersFromWeightAndPanelPaintsSmallAndLargeInBothPalettes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (boolean colorBlind : new boolean[]{false, true}) {
                var view = new BattlePanel(colorBlind);
                view.setSnapshot(FakeBattleClient.sample());
                for (int[] size : new int[][]{{480,360},{960,720}}) {
                    view.setSize(size[0], size[1]);
                    var image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                    var g = image.createGraphics(); view.paint(g); g.dispose();
                    double scale=size[0]/960.0;
                    assertNotEquals(image.getRGB(0,0), image.getRGB((int)(40*scale),(int)(610*scale)));
                    assertNotEquals(image.getRGB(0,0), image.getRGB((int)(512*scale),(int)(610*scale)));
                    if(size[0]==960 && colorBlind) {
                        try {
                            var file=new java.io.File("build/reports/sprint7-b-preview.png");
                            file.getParentFile().mkdirs(); javax.imageio.ImageIO.write(image,"png",file);
                        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                    }
                }
                var renderer = new BoardRenderer(colorBlind);
                var image = new BufferedImage(40,20,BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics();
                renderer.paintCell(g,8,0,0,20); renderer.paintCell(g,9,20,0,20); g.dispose();
                boolean different = false;
                for(int y=0;y<20;y++) for(int x=0;x<20;x++)
                    different |= image.getRGB(x,y) != image.getRGB(x+20,y);
                assertTrue(different, "W and attack must differ without color");
            }
        });
    }

}
