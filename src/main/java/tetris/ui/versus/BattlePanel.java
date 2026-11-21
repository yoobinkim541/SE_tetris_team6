package tetris.ui.versus;

import java.awt.*;
import java.util.List;
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
    private final boolean colorBlind;
    private final tetris.ui.fx.StarfieldBackground stars;
    private final tetris.ui.theme.PlayFx fx = new tetris.ui.theme.PlayFx();
    private MatchPhase seenPhase;
    private final String guide;
    private BattleKeyMap keys;
    private final String modeLabel;
    private final BattleMode mode;
    private final PlayerId viewer;
    private String notice = "";
    private String connectionNotice = "";
    /** 개발자 모드 연결 상태 줄 (dev-net-overlay). 비어 있으면 그리지 않는다 */
    private List<String> debugLines = List.of();
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
        this.colorBlind = colorBlind;
        setFocusable(true);
        renderer = new BoardRenderer(colorBlind);
        stars = new tetris.ui.fx.StarfieldBackground(this, tetris.ui.fx.AssetStore.BG_VERSUS);
        this.guide = Objects.requireNonNull(guide);
        stars.onFrame(this::tickFx);
        getAccessibleContext().setAccessibleDescription(guide);
    }
    private int comboOne;
    private int comboTwo;
    private int filledOne = -1;
    private int filledTwo = -1;
    public String modeLabel() { return modeLabel; }
    /** 보드 옆에 각자의 키를 크게 그린다 (S12-B1). 없으면 화면 아래 한 줄 안내만 쓴다 */
    public void setKeys(BattleKeyMap keys) { this.keys = Objects.requireNonNull(keys); repaint(); }
    public BattleKeyMap keys() { return keys; }
    /** P2P에서 이 PC가 조작하는 참가자. 로컬 대전이면 null */
    public PlayerId viewer() { return viewer; }
    public String notice() { return notice; }
    /** 경기 화면 가운데 위에 잠깐 띄우는 안내 (예: 온라인 기권 확인). 빈 문자열이면 지운다 */
    public void setNotice(String text) { notice = Objects.requireNonNull(text); repaint(); }
    /** P2P 연결 상태 안내 (예: 네트워크 지연). 빈 문자열이면 지운다 (S10-B2, R3-LAG) */
    public void setConnectionNotice(String text) { connectionNotice = Objects.requireNonNull(text); repaint(); }
    public String connectionNotice() { return connectionNotice; }
    /** 개발자 모드 연결 상태 줄 (dev-net-overlay). 빈 목록이면 보드 아래에 아무것도 그리지 않는다 */
    public void setDebugLines(List<String> lines) {
        debugLines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        repaint();
    }
    public List<String> debugLines() { return debugLines; }
    /** 시간제한 대전의 남은 시간. 올림해서 0:00이면 끝났다는 뜻. 시간 제한이 없으면 빈 문자열 */
    public static String timeText(long remainingMillis) {
        if (remainingMillis < 0) return "";
        long seconds = (remainingMillis + 999) / 1000;
        return String.format("남은 시간 %d:%02d", seconds / 60, seconds % 60);
    }
    public void setSnapshot(MatchSnapshot snapshot) {
        MatchSnapshot next = Objects.requireNonNull(snapshot);
        if (this.snapshot != null) {
            int one = filled(next.one().cells());
            int two = filled(next.two().cells());
            if (filledOne >= 0 && one < filledOne) comboOne++;
            else if (one > filledOne) comboOne = 0;
            if (filledTwo >= 0 && two < filledTwo) comboTwo++;
            else if (two > filledTwo) comboTwo = 0;
            filledOne = one;
            filledTwo = two;
        }
        this.snapshot = next;
        if (next.phase() == MatchPhase.COUNTDOWN && seenPhase != MatchPhase.COUNTDOWN) fx.armCountdown();
        if (next.phase() != MatchPhase.COUNTDOWN) fx.clearCountdown();
        seenPhase = next.phase();
        paintLog.received(System.nanoTime());
        repaint();
    }

    private static int filled(int[][] cells) {
        int count = 0;
        for (int[] row : cells) for (int value : row) if (value != 0) count++;
        return count;
    }

    private void tickFx() {
        fx.tick();
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

    @Override public void removeNotify() {
        stars.dispose();
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D sky = (Graphics2D) graphics.create();
        tetris.ui.theme.GameTheme.applyQuality(sky);
        try { stars.paint(sky, getWidth(), getHeight()); }
        finally { sky.dispose(); }
        if (snapshot == null) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            tetris.ui.theme.GameTheme.applyQuality(g);
            double scale = designScale(getWidth(), getHeight());
            if (scale <= 0) return;
            g.translate(designOrigin(getWidth(), 960, scale), designOrigin(getHeight(), 720, scale));
            g.scale(scale, scale);
            paintModeCapsule(g);
            if (!connectionNotice.isEmpty()) {
                g.setFont(tetris.ui.theme.GameTheme.fontFor(connectionNotice, 13, false));
                g.setColor(new Color(0xFFB74D));
                g.drawString(connectionNotice, 480 - g.getFontMetrics().stringWidth(connectionNotice) / 2, 68);
            }
            paintPlayer(g, snapshot.one(), PlayerId.ONE); paintPlayer(g, snapshot.two(), PlayerId.TWO);
            paintVersusChrome(g);
            paintOverlay(g);
            if (!notice.isEmpty()) {
                g.setFont(tetris.ui.theme.GameTheme.fontFor(notice, 13, false));
                g.setColor(new Color(0xFFD54F));
                g.drawString(notice, 480 - g.getFontMetrics().stringWidth(notice) / 2, 636);
            }
            paintKeyBars(g);
            paintDebugLines(g);
            paintLog.painted(snapshot, System.nanoTime());
        } finally { g.dispose(); }
    }
    /** 개발자 모드 연결 상태 줄을 그린다 (dev-net-overlay). 기존 화면 요소를 가리지 않도록 마지막에 그린다 */
    private void paintDebugLines(Graphics2D g) {
        if (debugLines.isEmpty()) return;
        g.setFont(tetris.ui.theme.GameTheme.text(12, false));
        FontMetrics metrics = g.getFontMetrics();
        int lineHeight = metrics.getHeight();
        int width = 0;
        for (String line : debugLines) width = Math.max(width, metrics.stringWidth(line));
        // 보드 아래 영역의 왼쪽은 범례·조작키 안내가 쓰므로 오른쪽 아래에 붙인다 (connectionNotice는 위쪽)
        int right = 932;
        int bottom = 712;
        int left = right - width - 8;
        int top = bottom - debugLines.size() * lineHeight - 2;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(left, top, width + 16, debugLines.size() * lineHeight + 6);
        g.setColor(new Color(0xB9F6CA));
        for (int i = 0; i < debugLines.size(); i++) {
            g.drawString(debugLines.get(i), left + 8, top + metrics.getAscent() + 3 + i * lineHeight);
        }
    }

    private void paintVersusChrome(Graphics2D g) {
        paintBoardFrame(g, PlayerId.ONE);
        paintBoardFrame(g, PlayerId.TWO);
        paintBadgeOnFrame(g, PlayerId.ONE, true);
        paintBadgeOnFrame(g, PlayerId.TWO, false);
        if (snapshot.remainingMillis() >= 0) {
            int w = 120;
            int h = 28;
            tetris.ui.theme.UiKit.paintCapsule(g, 480 - w / 2, 78, w, h);
            String digits = timeText(snapshot.remainingMillis()).replace("남은 시간 ", "");
            g.setFont(tetris.ui.theme.GameTheme.digits(16));
            g.setColor(Color.WHITE);
            center(g, digits, 480, 78 + h / 2 + g.getFontMetrics().getAscent() / 2 - 1);
        }
    }

    private void paintBadgeOnFrame(Graphics2D g, PlayerId id, boolean diamond) {
        java.awt.Rectangle board = boardBounds(id);
        int size = 30;
        int frameTop = board.y - 90;
        tetris.ui.theme.UiKit.paintBadge(g, diamond, board.x + board.width / 2 - size / 2, frameTop - size / 2, size);
    }
    private static void center(Graphics2D g, String text, int x, int baseline) {
        tetris.ui.theme.GameTheme.drawCentered(g, text, x, baseline);
    }

    private void paintBoardFrame(Graphics2D g, PlayerId id) {
        java.awt.Rectangle board = boardBounds(id);
        int inset = 8;
        int left = board.x - inset;
        int top = board.y - 118 - inset;
        int width = board.width + inset * 2;
        int height = board.height + 118 + inset * 2;
        var clip = g.getClip();
        java.awt.geom.Area outside = new java.awt.geom.Area(new java.awt.Rectangle(0, 0, 960, 720));
        outside.subtract(new java.awt.geom.Area(board));
        outside.subtract(new java.awt.geom.Area(new java.awt.Rectangle(board.x, board.y - 118, board.width, 110)));
        g.setClip(outside);
        tetris.ui.theme.UiKit.paintFrame(g, id == PlayerId.ONE ? tetris.ui.theme.UiKit.Tone.CYAN : tetris.ui.theme.UiKit.Tone.MAGENTA,
                left, top, width, height);
        g.setClip(clip);
    }

    /** 일시정지·카운트다운 동안 두 보드를 어둡게 덮고 상태를 크게 쓴다. 누가 눌러도 함께 풀린다 (D-03) */
    private void paintOverlay(Graphics2D g) {
        String text = overlayText(snapshot);
        if (text.isEmpty()) return;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(28, 90, 904, 540);
        g.setColor(Color.WHITE);
        g.setFont(tetris.ui.theme.GameTheme.display(36));
        tetris.ui.theme.UiKit.drawInBox(g, text, new java.awt.Rectangle(80, 300, 800, 56), tetris.ui.theme.UiKit.Align.CENTER);
        if (snapshot.phase() == MatchPhase.COUNTDOWN && !fx.countdownText().isEmpty()) {
            var saved = g.getTransform();
            g.setFont(tetris.ui.theme.GameTheme.digits(64));
            g.setColor(Color.WHITE);
            String count = fx.countdownText();
            g.translate(480, 280);
            g.scale(fx.countdownScale(), fx.countdownScale());
            tetris.ui.theme.GameTheme.draw(g, count, -tetris.ui.theme.GameTheme.width(g, count) / 2, 0);
            g.setTransform(saved);
        }
        if (snapshot.phase() == MatchPhase.COUNTDOWN) {
            // 처음 보는 사람이 설명 없이 자기 보드와 키를 찾게 한다 (S11-B2, R3-DEMO)
            String hint = viewer == null ? "왼쪽 P1 · 오른쪽 P2 · 조작 키는 보드 옆"
                    : "파란 테두리가 내 보드 (" + boardTitle(viewer, viewer) + ") · 조작 키는 보드 옆";
            g.setFont(tetris.ui.theme.GameTheme.text(16, false));
            tetris.ui.theme.UiKit.drawInBox(g, hint, new java.awt.Rectangle(40, 364, 880, 28), tetris.ui.theme.UiKit.Align.CENTER);
        }
        if (snapshot.phase() == MatchPhase.PAUSED) {
            String hint = "일시정지 키를 누르면 두 보드가 함께 다시 시작합니다";
            g.setFont(tetris.ui.theme.GameTheme.text(16, false));
            tetris.ui.theme.UiKit.drawInBox(g, hint, new java.awt.Rectangle(40, 364, 880, 28), tetris.ui.theme.UiKit.Align.CENTER);
        }
    }
    public static final int BOARD_CELL = 20;
    public static final int QUEUE_CELL = 12;
    public static final int QUEUE_STRIDE = 14;

    public java.awt.Rectangle boardBounds(PlayerId id) {
        int x = id == PlayerId.ONE ? 148 : 592;
        return new java.awt.Rectangle(x, 210, BOARD_CELL * 10, BOARD_CELL * 20);
    }

    public java.awt.Point boardSample(PlayerId id, int row, int col) {
        java.awt.Rectangle board = boardBounds(id);
        return new java.awt.Point(board.x + col * BOARD_CELL + BOARD_CELL / 2, board.y + row * BOARD_CELL + BOARD_CELL / 2);
    }

    public java.awt.Rectangle attackQueueBounds(PlayerId id) {
        java.awt.Rectangle board = boardBounds(id);
        int x = id == PlayerId.ONE ? 16 : board.x + board.width + 8;
        int height = QUEUE_STRIDE * VersusRules.MAX_PENDING_ROWS;
        return new java.awt.Rectangle(x, board.y + board.height - height, QUEUE_CELL * 10, height);
    }

    /** 대기열 칸 안쪽. row 0이 맨 아래(먼저 들어올 줄)다 */
    public java.awt.Point attackQueueSample(int player, int row, int col) {
        java.awt.Rectangle queue = attackQueueBounds(player == 0 ? PlayerId.ONE : PlayerId.TWO);
        int visual = VersusRules.MAX_PENDING_ROWS - 1 - row;
        return new java.awt.Point(queue.x + col * QUEUE_CELL + 2, queue.y + visual * QUEUE_STRIDE + 1);
    }

    public java.awt.Point toView(java.awt.Point design, int viewWidth, int viewHeight) {
        double scale = designScale(viewWidth, viewHeight);
        return new java.awt.Point(designOrigin(viewWidth, 960, scale) + (int) Math.round(design.x * scale),
                designOrigin(viewHeight, 720, scale) + (int) Math.round(design.y * scale));
    }
    private static double designScale(int viewWidth, int viewHeight) {
        if (viewWidth <= 0 || viewHeight <= 0) return 0;
        double raw = Math.min(viewWidth / 960.0, viewHeight / 720.0);
        int cellPx = Math.max(1, (int) Math.round(BOARD_CELL * raw));
        return cellPx / (double) BOARD_CELL;
    }

    private static int designOrigin(int view, int design, double scale) {
        return (int) Math.round((view - design * scale) / 2.0);
    }


    private void paintModeCapsule(Graphics2D g) {
        String mode = modeLabel.isEmpty() ? "대전" : modeLabel;
        int w = 168;
        int h = 28;
        tetris.ui.theme.UiKit.paintCapsule(g, 480 - w / 2, 8, w, h);
        g.setFont(tetris.ui.theme.GameTheme.fontFor(mode, 16, false));
        g.setColor(Color.WHITE);
        center(g, mode, 480, 8 + h / 2 + g.getFontMetrics().getAscent() / 2 - 1);
        String status = statusText(snapshot, viewer);
        g.setFont(tetris.ui.theme.GameTheme.fontFor(status, 12, false));
        g.setColor(tetris.ui.theme.UiKit.CYAN);
        center(g, status, 480, 50);
    }

    private void paintKeyBars(Graphics2D g) {
        String[] lines = guide.split("\\R");
        String first = lines.length > 0 ? shorten(lines[0]) : "";
        String second = lines.length > 1 ? shorten(lines[1]) : "";
        if (lines.length > 2) second = second + "   " + shorten(lines[2]);
        int inset = 16;
        int barH = 36;
        int y2 = 720 - inset - barH;
        int y1 = y2 - 8 - barH;
        paintBar(g, inset, y1, 960 - inset * 2, first);
        paintBar(g, inset, y2, 960 - inset * 2, second);
    }

    private static String shorten(java.util.List<String> lines) {
        return shorten(String.join("  ", lines));
    }

    private static String shorten(String text) {
        return text.replace("왼쪽 ", "").replace("오른쪽 ", "").replace("즉시 내리기", "낙하")
                .replace("내리기", "낙하").replace("반시계", "반대");
    }

    private void paintBar(Graphics2D g, int x, int y, int w, String text) {
        int h = 36;
        tetris.ui.theme.UiKit.paintCapsule(g, x, y, w, h);
        g.setFont(tetris.ui.theme.GameTheme.text(12, false));
        g.setColor(Color.WHITE);
        tetris.ui.theme.UiKit.drawInAsset(g, tetris.ui.theme.UiKit.AssetSlot.CAPSULE,
                new java.awt.Rectangle(x, y, w, h), text == null ? "" : text, tetris.ui.theme.UiKit.Align.CENTER);
    }
    private static String fitGuide(Graphics2D g, String text, int limit) {
        String drawn = text;
        int size = 13;
        g.setFont(tetris.ui.theme.GameTheme.text(size, false));
        while (size > 10 && tetris.ui.theme.GameTheme.width(g, drawn) > limit) {
            g.setFont(tetris.ui.theme.GameTheme.text(--size, false));
        }
        while (tetris.ui.theme.GameTheme.width(g, drawn) > limit && (drawn.contains("  ") || drawn.contains(" / "))) {
            int cut = Math.max(drawn.lastIndexOf("  "), drawn.lastIndexOf(" / "));
            if (cut <= 0) break;
            if (drawn.contains("일시정지") && cut < drawn.indexOf("일시정지")) break;
            drawn = drawn.substring(0, cut);
        }
        while (!drawn.isEmpty() && tetris.ui.theme.GameTheme.width(g, drawn) > limit) {
            drawn = drawn.substring(0, drawn.length() - 1);
        }
        return drawn;
    }


    private void paintPlayer(Graphics2D g, MatchSnapshot.PlayerView player, PlayerId id) {
        java.awt.Rectangle board = boardBounds(id);
        boolean mine = viewer != null && viewer == id;
        paintNameTab(g, player, id, board, mine);
        if (mine && colorBlind) {
            g.setColor(tetris.ui.fx.A11yColors.mine(true));
            g.setStroke(new BasicStroke(3f));
            g.drawRect(board.x - 2, board.y - 2, board.width + 3, board.height + 3);
            g.setStroke(new BasicStroke(1f));
        }
        int boxW = board.width / 2 - 6;
        int boxY = board.y - 78;
        int boxH = 70;
        tetris.ui.theme.UiKit.paintTab(g, id == PlayerId.ONE, board.x, boxY, boxW, boxH);
        tetris.ui.theme.UiKit.paintTab(g, id != PlayerId.ONE, board.x + boxW + 8, boxY, boxW, boxH);
        labelTab(g, "홀드", board.x, boxY, boxW, boxH);
        labelTab(g, "다음", board.x + boxW + 8, boxY, boxW, boxH);
        java.awt.Rectangle holdSlot = previewSlot(board.x, boxY, boxW, boxH);
        java.awt.Rectangle nextSlot = previewSlot(board.x + boxW + 8, boxY, boxW, boxH);
        renderer.paintFitted(g, player.hold(), holdSlot.x, holdSlot.y, holdSlot.width, holdSlot.height);
        java.util.List<int[][]> previews = player.previews().isEmpty()
                ? java.util.List.<int[][]>of(player.next()) : player.previews();
        if (!previews.isEmpty()) {
            int slotW = Math.max(1, nextSlot.width / previews.size());
            for (int i = 0; i < previews.size(); i++) {
                renderer.paintFitted(g, previews.get(i), nextSlot.x + i * slotW, nextSlot.y, Math.max(1, slotW - 2), nextSlot.height);
            }
        }
        renderer.paintGhost(g, player.ghost(), board.x, board.y, BOARD_CELL);
        renderer.paintPlayfield(g, player.cells(), player.items(), board.x, board.y, BOARD_CELL);
        paintAttackQueue(g, player, id);
        paintThreatGauge(g, player, id, board);
        int combo = id == PlayerId.ONE ? comboOne : comboTwo;
        if (combo >= 2) {
            g.setFont(tetris.ui.theme.GameTheme.display(28));
            tetris.ui.theme.UiKit.drawInBox(g, combo + " COMBO",
                    new java.awt.Rectangle(board.x, board.y + 20, board.width, 40), tetris.ui.theme.UiKit.Align.CENTER);
        }
    }

    private static void labelTab(Graphics2D g, String text, int x, int y, int width, int height) {
        int inset = tetris.ui.theme.UiKit.tabTitleInset(width, height);
        int size = 13;
        g.setFont(tetris.ui.theme.GameTheme.text(size, true));
        while (size > 9 && g.getFontMetrics().stringWidth(text) > width - inset * 2) {
            g.setFont(tetris.ui.theme.GameTheme.text(--size, true));
        }
        tetris.ui.theme.UiKit.drawInAsset(g, tetris.ui.theme.UiKit.AssetSlot.TAB,
                new java.awt.Rectangle(x, y, width, Math.max(16, inset + 16)), text, tetris.ui.theme.UiKit.Align.CENTER);
    }

    private static java.awt.Rectangle previewSlot(int x, int y, int width, int height) {
        int top = tetris.ui.theme.UiKit.tabTitleInset(width, height) + 8;
        int inset = 8;
        return new java.awt.Rectangle(x + inset, y + top, Math.max(1, width - inset * 2), Math.max(1, height - top - inset));
    }

    private void paintNameTab(Graphics2D g, MatchSnapshot.PlayerView player, PlayerId id,
                              java.awt.Rectangle board, boolean mine) {
        int h = 28;
        int y = board.y - 118;
        tetris.ui.theme.UiKit.paintCapsule(g, board.x, y, board.width, h);
        String mark = colorBlind ? (id == PlayerId.ONE ? "■ " : "● ") : (id == PlayerId.ONE ? "◆ " : "★ ");
        String name = mark + boardTitle(id, viewer);
        g.setFont(tetris.ui.theme.GameTheme.fontFor(name, Math.max(14, h * 3 / 5), false));
        g.setColor(mine ? tetris.ui.fx.A11yColors.mine(colorBlind) : Color.WHITE);
        java.awt.Rectangle tab = new java.awt.Rectangle(board.x, y, board.width, h);
        java.awt.Rectangle inner = tetris.ui.theme.UiKit.contentBox(tetris.ui.theme.UiKit.AssetSlot.CAPSULE, tab);
        java.awt.Rectangle left = new java.awt.Rectangle(inner.x, inner.y, inner.width * 55 / 100, inner.height);
        tetris.ui.theme.UiKit.drawIconText(g, left, mark, boardTitle(id, viewer));
        String score = String.valueOf(player.score());
        g.setFont(tetris.ui.theme.GameTheme.digits(Math.max(12, h * 3 / 5)));
        g.setColor(Color.WHITE);
        tetris.ui.theme.UiKit.drawInBox(g, score, new java.awt.Rectangle(inner.x + inner.width / 2, inner.y,
                inner.width / 2, inner.height), tetris.ui.theme.UiKit.Align.RIGHT);
    }

    private void paintAttackQueue(Graphics2D g, MatchSnapshot.PlayerView player, PlayerId id) {
        java.awt.Rectangle queue = attackQueueBounds(id);
        int count = player.pendingRows().size();
        String label = "받을 공격 " + count + "/" + VersusRules.MAX_PENDING_ROWS;
        g.setFont(tetris.ui.theme.GameTheme.fontFor(label, 12, false));
        g.setColor(colorBlind ? tetris.ui.fx.A11yColors.ORANGE : Color.WHITE);
        tetris.ui.theme.UiKit.drawInBox(g, label,
                new java.awt.Rectangle(queue.x - 2, queue.y - 18, queue.width + 4, 16),
                id == PlayerId.ONE ? tetris.ui.theme.UiKit.Align.LEFT : tetris.ui.theme.UiKit.Align.RIGHT);
        int pad = 3;
        var clip = g.getClip();
        java.awt.geom.Area outside = new java.awt.geom.Area(new java.awt.Rectangle(0, 0, 960, 720));
        outside.subtract(new java.awt.geom.Area(queue));
        g.setClip(outside);
        tetris.ui.theme.UiKit.paintFrame(g, id == PlayerId.ONE ? tetris.ui.theme.UiKit.Tone.CYAN : tetris.ui.theme.UiKit.Tone.MAGENTA,
                queue.x - pad, queue.y - pad, queue.width + pad * 2, queue.height + pad * 2);
        g.setClip(clip);
        for (int r = 0; r < VersusRules.MAX_PENDING_ROWS; r++) {
            int[] row = r < count ? player.pendingRows().get(r) : new int[VersusRules.BOARD_COLS];
            int visual = VersusRules.MAX_PENDING_ROWS - 1 - r;
            renderer.paint(g, new int[][] {row}, queue.x, queue.y + visual * QUEUE_STRIDE, QUEUE_CELL);
        }
    }

    private void paintThreatGauge(Graphics2D g, MatchSnapshot.PlayerView player, PlayerId id, java.awt.Rectangle board) {
        int count = player.pendingRows().size();
        java.awt.Rectangle queue = attackQueueBounds(id);
        int gaugeW = 4;
        int x = id == PlayerId.ONE ? queue.x + queue.width + 2 : board.x + board.width + 2;
        g.setColor(new Color(20, 8, 16, 140));
        g.fillRect(x, board.y, gaugeW, board.height);
        int filled = board.height * count / VersusRules.MAX_PENDING_ROWS;
        if (filled <= 0) return;
        g.setColor(colorBlind ? tetris.ui.fx.A11yColors.ORANGE : new Color(0xFF5A3C));
        g.fillRect(x, board.y + board.height - filled, gaugeW, filled);
        if (!colorBlind) return;
        g.setColor(Color.BLACK);
        for (int y = board.y + board.height - filled; y < board.y + board.height; y += 4) {
            g.drawLine(x, y, x + gaugeW - 1, y);
        }
    }
}
