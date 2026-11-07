package tetris.ui.versus;

import java.awt.*;
import java.util.Objects;
import javax.swing.JPanel;

/** One reusable two-player view. Owns no engine, socket or key bindings. EDT confined. */
public final class BattlePanel extends JPanel {
    private final BoardRenderer renderer;
    private MatchSnapshot snapshot;
    public BattlePanel(boolean colorBlind) {
        renderer = new BoardRenderer(colorBlind);
        setBackground(new Color(0x1B1E24)); setPreferredSize(new Dimension(960,720));
    }
    public void setSnapshot(MatchSnapshot snapshot) { this.snapshot = Objects.requireNonNull(snapshot); repaint(); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (snapshot == null) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            double scale = Math.min(getWidth()/960.0,getHeight()/720.0);
            if (scale <= 0) return;
            g.translate((getWidth()-960*scale)/2,(getHeight()-720*scale)/2); g.scale(scale,scale);
            g.setFont(new Font(Font.DIALOG,Font.BOLD,22)); g.setColor(Color.WHITE);
            g.drawString(snapshot.status(),28,32);
            String time = snapshot.remainingMillis() < 0 ? "Time: --" : "Time: " + (snapshot.remainingMillis()+999)/1000 + " s";
            g.drawString(time,740,32);
            paintPlayer(g,snapshot.one(),28,"P1"); paintPlayer(g,snapshot.two(),500,"P2");
            g.setFont(new Font(Font.DIALOG,Font.PLAIN,18)); g.setColor(Color.WHITE);
            g.drawString("X = Attack cell   W = Weight   Queue: top -> bottom (max 10)",28,680);
            g.drawString("P1: Arrows / Space    P2: A D S W / F    Shared: P pause / Q quit",28,706);
        } finally { g.dispose(); }
    }
    private void paintPlayer(Graphics2D g, MatchSnapshot.Player player, int x, String name) {
        g.setColor(Color.WHITE); g.setFont(new Font(Font.DIALOG,Font.BOLD,22));
        g.drawString(name+"  Score: "+player.score(),x,70);
        renderer.paint(g,player.cells(),x,90,27);
        g.drawString("Next",x+288,114); renderer.paint(g,player.next(),x+288,130,22);
        var queue = player.pendingRows();
        g.setColor(Color.WHITE); g.drawString("Queue "+queue.size()+"/10",x+288,260);
        for(int r=0;r<10;r++) {
            int[] row = r < queue.size() ? queue.get(r) : new int[10];
            renderer.paint(g,new int[][]{row},x+288,280+r*16,14);
        }
    }
}
