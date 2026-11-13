package tetris.ui.versus;

import java.awt.*;
import java.util.Objects;
import javax.swing.JPanel;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchResult;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

/** One reusable two-player view. Owns no engine, socket or key bindings. EDT confined. */
public final class BattlePanel extends JPanel {
    private final BoardRenderer renderer;
    private final String guide;
    private final String modeLabel;
    private final BattleMode mode;
    private final PlayerId viewer;
    private String notice = "";
    private String connectionNotice = "";
    private MatchSnapshot snapshot;
    private final PaintLog paintLog = new PaintLog();
    public BattlePanel(boolean colorBlind) {
        this(colorBlind, new BattleKeyMap().guide());
    }
    public BattlePanel(boolean colorBlind, String guide) {
        this(colorBlind, guide, "");
    }
    /** modeLabel: 화면 위쪽에 표시할 대전 모드 이름 (S9-B1) */
    public BattlePanel(boolean colorBlind, String guide, String modeLabel) {
        this(colorBlind, guide, modeLabel, null, null);
    }
    /**
     * 모드와 보는 사람을 아는 경기 화면 (S10-B1). mode가 null이면 범례를 모두 보이고,
     * viewer가 null이면 로컬 2인(한 화면 두 사람), 아니면 P2P에서 이 PC가 조작하는 참가자다
     */
    public BattlePanel(boolean colorBlind, String guide, String modeLabel, BattleMode mode, PlayerId viewer) {
        this.modeLabel = Objects.requireNonNull(modeLabel);
        this.mode = mode;
        this.viewer = viewer;
        setFocusable(true);
        renderer = new BoardRenderer(colorBlind);
        this.guide = Objects.requireNonNull(guide);
        getAccessibleContext().setAccessibleDescription(guide);
        setBackground(new Color(0x1B1E24)); setPreferredSize(new Dimension(960,720));
    }
    public String modeLabel() { return modeLabel; }
    /** P2P에서 이 PC가 조작하는 참가자. 로컬 대전이면 null */
    public PlayerId viewer() { return viewer; }
    public String notice() { return notice; }
    /** 경기 화면 가운데 위에 잠깐 띄우는 안내 (예: 온라인 기권 확인). 빈 문자열이면 지운다 */
    public void setNotice(String text) { notice = Objects.requireNonNull(text); repaint(); }
    /** P2P 연결 상태 안내 (예: 네트워크 지연). 빈 문자열이면 지운다 (S10-B2, R3-LAG) */
    public void setConnectionNotice(String text) { connectionNotice = Objects.requireNonNull(text); repaint(); }
    public String connectionNotice() { return connectionNotice; }
    /** 시간제한 대전의 남은 시간. 올림해서 0:00이면 끝났다는 뜻. 시간 제한이 없으면 빈 문자열 */
    public static String timeText(long remainingMillis) {
        if (remainingMillis < 0) return "";
        long seconds = (remainingMillis + 999) / 1000;
        return String.format("남은 시간 %d:%02d", seconds / 60, seconds % 60);
    }
    public void setSnapshot(MatchSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot);
        paintLog.received(System.nanoTime());
        repaint();
    }
    /** 이 화면의 그리기 기록 (상태 수신→그리기 지연 측정, S11-B2) */
    public PaintLog paintLog() { return paintLog; }
    /** 경기 상태 한 줄 요약. 로컬 대전처럼 보는 사람이 정해지지 않은 화면용 */
    public static String statusText(MatchSnapshot snapshot) {
        return statusText(snapshot, null);
    }

    /** 보는 사람(viewer) 관점의 경기 상태. viewer가 있으면 결과를 "승리/패배"로 쓴다 (S10-B1) */
    public static String statusText(MatchSnapshot snapshot, PlayerId viewer) {
        Objects.requireNonNull(snapshot);
        return switch (snapshot.phase()) {
            case COUNTDOWN -> "곧 시작합니다";
            case PLAYING -> "경기 중";
            case PAUSED -> "일시정지";
            case FINISHED -> outcomeText(snapshot.result(), viewer) + " · " + reasonText(snapshot.result().reason());
        };
    }

    /** 승패 문구. viewer가 null이면 "P1 승리", 있으면 "승리!"/"패배" */
    public static String outcomeText(MatchResult result, PlayerId viewer) {
        Objects.requireNonNull(result);
        if (result.isDraw()) return "무승부";
        if (viewer == null) return playerName(result.winner()) + " 승리";
        return result.winner() == viewer ? "승리!" : "패배";
    }

    public static String reasonText(MatchResult.EndReason reason) {
        return switch (Objects.requireNonNull(reason)) {
            case TOPPED_OUT -> "블록 쌓임";
            case TIME_UP -> "시간 종료";
            case FORFEIT -> "기권";
        };
    }

    /** 보드 위 이름. P2P에서는 자기 보드에 "(나)", 상대 보드에 "(상대)"를 붙인다 */
    public static String boardTitle(PlayerId player, PlayerId viewer) {
        String name = playerName(player);
        if (viewer == null) return name;
        return name + (player == viewer ? " (나)" : " (상대)");
    }

    private static String playerName(PlayerId player) {
        return player == PlayerId.ONE ? "P1" : "P2";
    }

    /** 화면 아래 범례. 아이템 표시는 아이템 대전(또는 모드를 모를 때)에만 보인다 */
    public static String legendText(BattleMode mode) {
        String attack = "회색 X = 공격으로 올라온 줄   대기열: 위→아래 순서로 들어옴 (최대 " + VersusRules.MAX_PENDING_ROWS + "줄)";
        if (mode != null && !mode.itemsEnabled()) return attack;
        return attack + "\n아이템: L 줄 삭제 · B 폭탄 · D 점수 2배 · S 느리게 · W 무게추";
    }

    /** 현재 상태에서 보드 위에 크게 띄울 문구. 없으면 빈 문자열 */
    public static String overlayText(MatchSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        return switch (snapshot.phase()) {
            case COUNTDOWN -> "준비";
            case PAUSED -> "일시정지";
            default -> "";
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
            g.drawString(statusText(snapshot, viewer),28,32);
            if (!modeLabel.isEmpty()) g.drawString(modeLabel,
                    480 - g.getFontMetrics().stringWidth(modeLabel) / 2, 32);
            if (!connectionNotice.isEmpty()) {
                Font font = g.getFont();
                g.setFont(new Font(Font.DIALOG, Font.BOLD, 15));
                g.setColor(new Color(0xFFB74D));
                g.drawString(connectionNotice, 480 - g.getFontMetrics().stringWidth(connectionNotice) / 2, 50);
                g.setFont(font);
                g.setColor(Color.WHITE);
            }
            String time = timeText(snapshot.remainingMillis());
            g.drawString(time, 932 - g.getFontMetrics().stringWidth(time), 32);
            paintPlayer(g,snapshot.one(),28,PlayerId.ONE); paintPlayer(g,snapshot.two(),500,PlayerId.TWO);
            paintOverlay(g);
            if (!notice.isEmpty()) {
                g.setColor(new Color(0xFFD54F));
                g.drawString(notice, 480 - g.getFontMetrics().stringWidth(notice) / 2, 630);
            }
            g.setFont(new Font(Font.DIALOG,Font.PLAIN,15)); g.setColor(Color.WHITE);
            String[] legend = legendText(mode).split("\\R");
            for (int i = 0; i < legend.length; i++) g.drawString(legend[i], 28, (legend.length > 1 ? 640 : 652) + i * 17);
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
            paintLog.painted(snapshot, System.nanoTime());
        } finally { g.dispose(); }
    }
    /** 일시정지·카운트다운 동안 두 보드를 어둡게 덮고 상태를 크게 쓴다. 누가 눌러도 함께 풀린다 (D-03) */
    private void paintOverlay(Graphics2D g) {
        String text = overlayText(snapshot);
        if (text.isEmpty()) return;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(28, 90, 904, 540);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.DIALOG, Font.BOLD, 44));
        g.drawString(text, 480 - g.getFontMetrics().stringWidth(text) / 2, 340);
        if (snapshot.phase() == MatchPhase.COUNTDOWN) {
            // 처음 보는 사람이 설명 없이 자기 보드와 키를 찾게 한다 (S11-B2, R3-DEMO)
            String hint = viewer == null ? "왼쪽 P1 · 오른쪽 P2 · 조작 키는 화면 아래"
                    : "파란 테두리가 내 보드 (" + boardTitle(viewer, viewer) + ") · 조작 키는 화면 아래";
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 18));
            g.drawString(hint, 480 - g.getFontMetrics().stringWidth(hint) / 2, 380);
        }
        if (snapshot.phase() == MatchPhase.PAUSED) {
            String hint = "일시정지 키를 누르면 두 보드가 함께 다시 시작합니다";
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 18));
            g.drawString(hint, 480 - g.getFontMetrics().stringWidth(hint) / 2, 380);
        }
    }
    private void paintPlayer(Graphics2D g, MatchSnapshot.PlayerView player, int x, PlayerId id) {
        boolean mine = viewer != null && viewer == id;
        g.setColor(mine ? new Color(0x8BD3FF) : Color.WHITE); g.setFont(new Font(Font.DIALOG,Font.BOLD,22));
        g.drawString(boardTitle(id, viewer)+"  점수 "+player.score(),x,70);
        if (mine) { g.drawRect(x-3,87,10*27+5,20*27+5); g.setColor(Color.WHITE); }
        renderer.paint(g,player.cells(),player.items(),x,90,27);
        g.drawString("다음",x+288,114); renderer.paint(g,player.next(),x+288,130,22);
        var queue = player.pendingRows();
        g.setColor(Color.WHITE); g.drawString("공격 대기 "+queue.size()+"/"+VersusRules.MAX_PENDING_ROWS,x+288,260);
        for(int r=0;r<VersusRules.MAX_PENDING_ROWS;r++) {
            int[] row = r < queue.size() ? queue.get(r) : new int[VersusRules.BOARD_COLS];
            renderer.paint(g,new int[][]{row},x+288,280+r*16,14);
        }
    }
}
