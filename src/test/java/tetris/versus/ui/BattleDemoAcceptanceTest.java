package tetris.versus.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import tetris.ui.versus.BattleDemo;

/** Automated delegated demonstration of the real Swing Fake preview, not a full match. */
@EnabledIfSystemProperty(named="audit.gui",matches="true")
class BattleDemoAcceptanceTest {
    @Test void demonstratesBothPlayersPauseResumeAndQuitInTheRealPreviewWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var frame=BattleDemo.open(true);
            try {
                var root=frame.getRootPane();
                JLabel commands=null;
                for(var component:frame.getContentPane().getComponents()) if(component instanceof JLabel label) commands=label;
                assertNotNull(commands); assertTrue(frame.isShowing());
                key(root,KeyEvent.VK_LEFT,false); assertTrue(commands.getText().contains("ONE / MOVE_LEFT / press"));
                key(root,KeyEvent.VK_LEFT,true);
                key(root,KeyEvent.VK_A,false); assertTrue(commands.getText().contains("TWO / MOVE_LEFT / press"));
                key(root,KeyEvent.VK_A,true);
                key(root,KeyEvent.VK_P,false); assertTrue(commands.getText().contains("PAUSE / press"));
                String paused=commands.getText();
                key(root,KeyEvent.VK_LEFT,false); assertEquals(paused,commands.getText());
                key(root,KeyEvent.VK_P,true); key(root,KeyEvent.VK_P,false); key(root,KeyEvent.VK_P,true);
                key(root,KeyEvent.VK_F,false); assertTrue(commands.getText().contains("TWO / HARD_DROP / press"));
                key(root,KeyEvent.VK_F,true);
                var image=new BufferedImage(frame.getContentPane().getWidth(),frame.getContentPane().getHeight(),BufferedImage.TYPE_INT_RGB);
                var graphics=image.createGraphics(); frame.getContentPane().paintAll(graphics); graphics.dispose();
                try {
                    var output=new java.io.File("build/reports/sprint7-demo.png"); output.getParentFile().mkdirs();
                    javax.imageio.ImageIO.write(image,"png",output);
                } catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                key(root,KeyEvent.VK_Q,false); assertFalse(frame.isDisplayable());
            } finally { frame.dispose(); }
        });
    }
    private static void key(JRootPane root,int code,boolean released) {
        Object name=root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(code,0,released));
        assertNotNull(name); root.getActionMap().get(name).actionPerformed(new ActionEvent(root,0,"demo"));
    }
}
