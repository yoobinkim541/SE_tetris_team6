package tetris.ui.versus;

import java.awt.*;
import java.util.Objects;
import javax.swing.JPanel;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.VersusRules;

/** One reusable two-player view. Owns no engine, socket or key bindings. EDT confined. */
public final class BattlePanel extends JPanel {
    private final BoardRenderer renderer;
    private final String guide;
    private final String modeLabel;
    private MatchSnapshot snapshot;
    public BattlePanel(boolean colorBlind) {
        this(colorBlind, new BattleKeyMap().guide());
    }
    public BattlePanel(boolean colorBlind, String guide) {
        this(colorBlind, guide, "");
    }
    /** modeLabel: 화면 위쪽에 표시할 대전 모드 이름 (S9-B1) */
    public BattlePanel(boolean colorBlind, String guide, String modeLabel) {
        this.modeLabel = Objects.requireNonNull(modeLabel);
        setFocusable(true);
        renderer = new BoardRenderer(colorBlind);
        this.guide = Objects.requireNonNull(guide);
        getAccessibleContext().setAccessibleDescription(guide);
        setBackground(new Color(0x1B1E24)); setPreferredSize(new Dimension(960,720));
    }
    public String modeLabel() { return modeLabel; }
    /** 시간제한 대전의 남은 시간. 올림해서 0:00이면 끝났다는 뜻. 시간 제한이 없으면 빈 문자열 */
    public static String timeText(long remainingMillis) {
        if (remainingMillis < 0) return "";
        long seconds = (remainingMillis + 999) / 1000;
        return String.format("남은 시간 %d:%02d", seconds / 60, seconds % 60);
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
            if (!modeLabel.isEmpty()) g.drawString(modeLabel,
                    480 - g.getFontMetrics().stringWidth(modeLabel) / 2, 32);
            String time = timeText(snapshot.remainingMillis());
            g.drawString(time, 932 - g.getFontMetrics().stringWidth(time), 32);
            paintPlayer(g,snapshot.one(),28,"P1"); paintPlayer(g,snapshot.two(),500,"P2");
            g.setFont(new Font(Font.DIALOG,Font.PLAIN,18)); g.setColor(Color.WHITE);
            g.drawString("X = Attack cell   W = Weight   Queue: top -> bottom (max " + VersusRules.MAX_PENDING_ROWS + ")",28,650);
            g.setFont(new Font(Font.DIALOG,Font.PLAIN,14));
            String[] lines = guide.split("\\R", -1);
            int lineHeight = Math.min(18, 54 / Math.max(1, lines.length));
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Font font = g.getFont();
                int width = g.getFontMetrics().stringWidth(line);
                if (width > 904) g.setFont(font.deriveFont((float) (font.getSize2D() * 904 / width)));
                g.drawString(line, 28, 670 + i * lineHeight);
                g.setFont(font);
            }
        } finally { g.dispose(); }
    }
    private void paintPlayer(Graphics2D g, MatchSnapshot.PlayerView player, int x, String name) {
        g.setColor(Color.WHITE); g.setFont(new Font(Font.DIALOG,Font.BOLD,22));
        g.drawString(name+"  Score: "+player.score(),x,70);
        renderer.paint(g,player.cells(),player.items(),x,90,27);
        g.drawString("Next",x+288,114); renderer.paint(g,player.next(),x+288,130,22);
        var queue = player.pendingRows();
        g.setColor(Color.WHITE); g.drawString("Queue "+queue.size()+"/"+VersusRules.MAX_PENDING_ROWS,x+288,260);
        for(int r=0;r<VersusRules.MAX_PENDING_ROWS;r++) {
            int[] row = r < queue.size() ? queue.get(r) : new int[VersusRules.BOARD_COLS];
            renderer.paint(g,new int[][]{row},x+288,280+r*16,14);
        }
    }
}
