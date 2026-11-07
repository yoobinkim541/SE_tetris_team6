package tetris.ui.versus;

import java.awt.*;
import java.util.Objects;
import javax.swing.JPanel;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.VersusRules;

/** One reusable two-player view. Owns no engine, socket or key bindings. EDT confined. */
public final class BattlePanel extends JPanel {
    private final BoardRenderer renderer;
    private MatchSnapshot snapshot;
    public BattlePanel(boolean colorBlind) {
        renderer = new BoardRenderer(colorBlind);
        setBackground(new Color(0x1B1E24)); setPreferredSize(new Dimension(960,720));
    }
    public void setSnapshot(MatchSnapshot snapshot) { this.snapshot = Objects.requireNonNull(snapshot); repaint(); }
    /** Human-readable status derived solely from the shared match contract. */
    public static String statusText(MatchSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        return switch (snapshot.phase()) {
            case COUNTDOWN -> "Countdown";
            case PLAYING -> "Playing";
            case PAUSED -> "Paused";
            case FINISHED -> {
                var result = snapshot.result();
                String outcome = result.isDraw() ? "Draw" : (result.winner() == tetris.feature.versus.api.PlayerId.ONE ? "P1 wins" : "P2 wins");
                String reason = switch (result.reason()) {
                    case TOPPED_OUT -> "Top out";
                    case TIME_UP -> "Time up";
                    case FORFEIT -> "Forfeit";
                };
                yield outcome + " / " + reason;
            }
        };
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (snapshot == null) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            double scale = Math.min(getWidth()/960.0,getHeight()/720.0);
            if (scale <= 0) return;
            g.translate((getWidth()-960*scale)/2,(getHeight()-720*scale)/2); g.scale(scale,scale);
            g.setFont(new Font(Font.DIALOG,Font.BOLD,22)); g.setColor(Color.WHITE);
            g.drawString(statusText(snapshot),28,32);
            String time = snapshot.remainingMillis() < 0 ? "Time: --" : "Time: " + (snapshot.remainingMillis()+999)/1000 + " s";
            g.drawString(time,740,32);
            paintPlayer(g,snapshot.one(),28,"P1"); paintPlayer(g,snapshot.two(),500,"P2");
            g.setFont(new Font(Font.DIALOG,Font.PLAIN,18)); g.setColor(Color.WHITE);
            g.drawString("X = Attack cell   W = Weight   Queue: top -> bottom (max " + VersusRules.MAX_PENDING_ROWS + ")",28,680);
            g.drawString("P1: Arrows / Space    P2: A D S W / F    Shared: P pause / Q quit",28,706);
        } finally { g.dispose(); }
    }
    private void paintPlayer(Graphics2D g, MatchSnapshot.PlayerView player, int x, String name) {
        g.setColor(Color.WHITE); g.setFont(new Font(Font.DIALOG,Font.BOLD,22));
        g.drawString(name+"  Score: "+player.score(),x,70);
        renderer.paint(g,player.cells(),x,90,27);
        g.drawString("Next",x+288,114); renderer.paint(g,player.next(),x+288,130,22);
        var queue = player.pendingRows();
        g.setColor(Color.WHITE); g.drawString("Queue "+queue.size()+"/"+VersusRules.MAX_PENDING_ROWS,x+288,260);
        for(int r=0;r<VersusRules.MAX_PENDING_ROWS;r++) {
            int[] row = r < queue.size() ? queue.get(r) : new int[VersusRules.BOARD_COLS];
            renderer.paint(g,new int[][]{row},x+288,280+r*16,14);
        }
    }
}
