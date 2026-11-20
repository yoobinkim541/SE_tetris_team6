package tetris.ui.raid;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JPanel;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.CardId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.versus.api.VersusRules;
import tetris.ui.fx.AssetStore;
import tetris.ui.fx.FxLayer;
import tetris.ui.fx.StarfieldBackground;
import tetris.ui.versus.BoardRenderer;

/**
 * 보스 레이드 화면. 솔로는 보드 | 보스, 협동은 P1 | 보스 | P2.
 * 600×720 가상 화면은 게임 창(5:6)과 같아서 세 크기에서 보드가 최대한 커진다.
 * 에셋이 없으면 도형으로 대신 그린다.
 */
public final class RaidPanel extends JPanel {
    static final int VW = 600;
    static final int VH = 720;

    private static final Color PANEL = new Color(8, 10, 28, 210);
    private static final Color CYAN = new Color(0x3DE0FF);
    private static final Color MAGENTA = new Color(0xE85CFF);
    private static final Color GOLD = new Color(0xFFC94D);
    private static final Color TEXT = new Color(0xF4F7FF);
    private static final Color DIM = new Color(0x9AA4C7);
    private static final Color HP = new Color(0xFF4D6A);

    private final BoardRenderer boards;
    private final boolean colorBlind;
    private final StarfieldBackground stars;
    private final FxLayer fx = new FxLayer();
    private final AssetStore assets = AssetStore.SHARED;
    private RaidSnapshot snapshot;
    private String guide = "";
    private int cardSelection = 1;
    private int resultIndex;
    private boolean confirming;
    private boolean confirmYes;
    private Runnable onMenu = () -> { };
    private Runnable onRetry = () -> { };
    private Runnable onCancelConfirm = () -> setConfirm(false, false);
    private long countdownAnchor = -1;
    private long lastRevision = Long.MIN_VALUE;
    private final Rectangle bossBounds = new Rectangle();
    private double scale = 1;
    private int originX;
    private int originY;
    private Rectangle menuButton = new Rectangle();
    private Rectangle retryButton = new Rectangle();
    private final Rectangle cueTarget = new Rectangle();
    private final Rectangle cueLucky = new Rectangle();
    private final Rectangle cueNext = new Rectangle();
    private final Rectangle cueScore = new Rectangle();

    public RaidPanel(boolean colorBlind) {
        this.colorBlind = colorBlind;
        boards = new BoardRenderer(colorBlind);
        fx.setColorBlind(colorBlind);
        setOpaque(true);
        setBackground(new Color(0x07091A));
        stars = new StarfieldBackground(this, AssetStore.BG_RAID);
        stars.onFrame(fx::tick);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { click(e.getX(), e.getY()); }
        });
    }

    public void setSnapshot(RaidSnapshot snapshot) {
        RaidPhase previous = this.snapshot == null ? null : this.snapshot.phase();
        this.snapshot = snapshot;
        if (snapshot != null && snapshot.phase() == RaidPhase.COUNTDOWN && previous != RaidPhase.COUNTDOWN) {
            countdownAnchor = System.currentTimeMillis();
        }
        if (snapshot != null && snapshot.revision() != lastRevision) {
            fx.absorb(snapshot.events());
            lastRevision = snapshot.revision();
        }
        repaint();
    }

    public void setGuide(String guide) {
        this.guide = guide == null ? "" : guide;
        repaint();
    }

    public void setCardSelection(int index) {
        cardSelection = Math.max(0, Math.min(2, index));
        repaint();
    }

    public int cardSelection() {
        return cardSelection;
    }

    public void setConfirm(boolean showing, boolean yes) {
        confirming = showing;
        confirmYes = yes;
        repaint();
    }

    public void setResultActions(Runnable menu, Runnable retry) {
        onMenu = menu == null ? () -> { } : menu;
        onRetry = retry == null ? () -> { } : retry;
    }

    public void setConfirmAction(Runnable cancel) {
        onCancelConfirm = cancel == null ? () -> setConfirm(false, false) : cancel;
    }
    public void moveResult(int delta) {
        resultIndex = Math.floorMod(resultIndex + delta, 2);
        repaint();
    }

    public int resultIndex() {
        return resultIndex;
    }

    public void activateResult() {
        if (resultIndex == 0) onMenu.run();
        else onRetry.run();
    }

    /** 마지막으로 그린 보스 영역(패널 좌표). 테스트가 빈 배경이 아닌지 본다 */
    public Rectangle bossBounds() {
        return new Rectangle(bossBounds);
    }

    public void click(int x, int y) {
        if (snapshot == null || scale <= 0) return;
        int vx = (int) ((x - originX) / scale);
        int vy = (int) ((y - originY) / scale);
        if (snapshot.phase() == RaidPhase.FINISHED) {
            if (menuButton.contains(vx, vy)) onMenu.run();
            else if (retryButton.contains(vx, vy)) onRetry.run();
            return;
        }
        if (confirming) {
            if (menuButton.contains(vx, vy)) onMenu.run();
            else if (retryButton.contains(vx, vy)) onCancelConfirm.run();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            stars.paint(g, getWidth(), getHeight());
            if (snapshot == null || getWidth() <= 0 || getHeight() <= 0) return;
            scale = Math.min(getWidth() / (double) VW, getHeight() / (double) VH);
            originX = (int) ((getWidth() - VW * scale) / 2);
            originY = (int) ((getHeight() - VH * scale) / 2);
            g.translate(originX, originY);
            g.scale(scale, scale);
            g.translate(fx.shakeX(), 0);
            paintScene(g);
        } finally {
            g.dispose();
        }
    }

    @Override
    public void removeNotify() {
        stars.dispose();
        super.removeNotify();
    }

    private void paintScene(Graphics2D g) {
        boolean coop = snapshot.mode() == RaidMode.COOP;
        Slot left = coop ? coopSlot(6) : soloSlot();
        Slot right = coop ? coopSlot(VW - 6 - (22 * 10 + 12)) : null;
        int bossX = coop ? left.x + left.w + 6 : left.x + left.w + 8;
        int bossW = coop ? right.x - bossX - 6 : VW - bossX - 8;
        paintPlayer(g, snapshot.players().get(0), 0, left);
        if (right != null) paintPlayer(g, snapshot.players().get(1), 1, right);
        Rectangle boss = new Rectangle(bossX, 8, Math.max(80, bossW), VH - 44);
        paintBoss(g, boss);
        bossBounds.setBounds(originX + (int) (boss.x * scale), originY + (int) (boss.y * scale),
                Math.max(1, (int) (boss.width * scale)), Math.max(1, (int) (boss.height * scale)));
        int[] centers = right == null
                ? new int[] {left.boardX + left.cell * 5}
                : new int[] {left.boardX + left.cell * 5, right.boardX + right.cell * 5};
        int[] mids = right == null
                ? new int[] {left.boardY + left.cell * 10}
                : new int[] {left.boardY + left.cell * 10, right.boardY + right.cell * 10};
        fx.placeSparks(centers, mids);
        fx.paintSparks(g);
        fx.paintNumbers(g, centers, left.boardY);
        fx.paintBanner(g, VW / 2, 250);
        paintGuide(g);
        if (snapshot.phase() == RaidPhase.COUNTDOWN) paintCenter(g, String.valueOf(countdownDigit()), "곧 시작합니다");
        if (snapshot.phase() == RaidPhase.PAUSED && !confirming) paintCenter(g, "일시정지", "일시정지 키로 다시 시작합니다");
        if (snapshot.phase() == RaidPhase.CARD_PICK && snapshot.offer() != null) paintCards(g);
        if (confirming) paintConfirm(g);
        if (snapshot.phase() == RaidPhase.FINISHED && snapshot.result() != null) paintResult(g);
    }

    private static Slot soloSlot() {
        int cell = 26;
        int x = 8;
        int boardX = x + 8;
        int boardY = 34;
        int sideX = boardX + cell * 10 + 8;
        int w = sideX + 72 - x;
        int h = boardY - 8 + cell * 20 + 88;
        return new Slot(x, 8, w, h, boardX, boardY, cell, sideX);
    }

    private static Slot coopSlot(int x) {
        int cell = 22;
        int boardX = x + 6;
        int boardY = 28;
        int w = cell * 10 + 12;
        int h = boardY - 6 + cell * 20 + 156;
        return new Slot(x, 6, w, h, boardX, boardY, cell, boardX);
    }

    private static Slot slot(int x, int width, int cell) {
        int boardX = x + 10;
        int boardY = 78;
        return new Slot(x, 48, width, boardY - 48 + cell * 20 + 78, boardX, boardY, cell, boardX + cell * 10 + 8);
    }

    private void paintPlayer(Graphics2D g, RaidSnapshot.PlayerView player, int index, Slot slot) {
        g.setColor(PANEL);
        g.fillRoundRect(slot.x, slot.y, slot.w, slot.h, 18, 18);
        g.setColor(index == 0 ? tetris.ui.fx.A11yColors.mine(colorBlind) : TEXT);
        g.setFont(font(14, true));
        String who = (colorBlind ? (index == 0 ? "■ " : "● ") : "") + (index == 0 ? "P1" : "P2");
        String title = who + "  점수 " + player.board().score() + (player.alive() ? "" : "  쓰러짐");
        g.drawString(title, slot.x + 8, slot.y + 18);
        mark(cueScore, slot.x + 8, slot.y + 4, Math.min(slot.w - 16, 160), 18);
        var board = player.board();
        boards.paintGhost(g, board.ghost(), slot.boardX, slot.boardY, slot.cell);
        boards.paint(g, board.cells(), board.items(), slot.boardX, slot.boardY, slot.cell);
        paintLucky(g, player.lucky(), slot);
        paintCloud(g, player, slot);
        paintSide(g, player, slot);
        paintBadges(g, player, slot);
        if (!player.alive()) paintDowned(g, player, slot);
        if (isTarget(index)) paintTargetBorder(g, slot);
    }

    private boolean isTarget(int index) {
        if (snapshot.phase() != RaidPhase.PLAYING && snapshot.phase() != RaidPhase.COUNTDOWN) return false;
        int target = snapshot.boss().target();
        return target < 0 || target == index;
    }


    private void paintTargetBorder(Graphics2D g, Slot slot) {
        float pulse = 0.45f + 0.55f * (float) Math.abs(Math.sin(stars.frame() / 6.0));
        Color markColor = tetris.ui.fx.A11yColors.target(colorBlind);
        g.setColor(new Color(markColor.getRed(), markColor.getGreen(), markColor.getBlue(), (int) (160 + 80 * pulse)));
        g.setStroke(colorBlind
                ? new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {6f, 4f}, 0f)
                : new BasicStroke(3f + pulse * 2f));
        g.drawRoundRect(slot.x + 2, slot.y + 2, slot.w - 4, slot.h - 4, 18, 18);
        g.setFont(font(13, true));
        g.setColor(markColor);
        String label = "▼ 대상";
        int labelX = slot.x + slot.w - g.getFontMetrics().stringWidth(label) - 6;
        g.drawString(label, labelX, slot.y + 18);
        mark(cueTarget, labelX, slot.y + 4, g.getFontMetrics().stringWidth(label) + 2, 16);
        g.setStroke(new BasicStroke(1f));
    }

    private void paintLucky(Graphics2D g, int[][] lucky, Slot slot) {
        if (lucky == null) return;
        boolean marked = false;
        for (int r = 0; r < lucky.length && r < VersusRules.BOARD_ROWS; r++) {
            for (int c = 0; c < lucky[r].length && c < VersusRules.BOARD_COLS; c++) {
                if (lucky[r][c] == 0) continue;
                int x = slot.boardX + c * slot.cell;
                int y = slot.boardY + r * slot.cell;
                tetris.ui.fx.A11yColors.paintLuckyMark(g, x, y, slot.cell, colorBlind);
                if (!marked) {
                    mark(cueLucky, x, y, slot.cell, slot.cell);
                    marked = true;
                }
            }
        }
    }

    private void paintCloud(Graphics2D g, RaidSnapshot.PlayerView player, Slot slot) {
        for (RaidSnapshot.Effect effect : player.effects()) {
            if (effect.pattern() != PatternId.CLOUD) continue;
            int[] area = effect.area();
            if (area.length < 4) continue;
            int row = area[0];
            int col = area[1];
            int height = area[2];
            int width = area[3];
            int x = slot.boardX + col * slot.cell;
            int y = slot.boardY + row * slot.cell;
            int w = width * slot.cell;
            int h = height * slot.cell;
            Graphics2D layer = (Graphics2D) g.create();
            try {
                layer.clipRect(slot.boardX, slot.boardY, slot.cell * VersusRules.BOARD_COLS, slot.cell * VersusRules.BOARD_ROWS);
                layer.clipRect(x, y, w, h);
                layer.setColor(new Color(180, 220, 255, 70));
                layer.fillRect(x, y, w, h);
                layer.setColor(colorBlind ? Color.WHITE : new Color(0xD7F6FF));
                for (int i = -h; i < w; i += 6) layer.drawLine(x + i, y, x + i + h, y + h);
            } finally {
                layer.dispose();
            }
        }
    }

    private void paintSide(Graphics2D g, RaidSnapshot.PlayerView player, Slot slot) {
        boolean sealed = player.effects().stream().anyMatch(effect -> effect.pattern() == PatternId.SEAL_PREVIEW);
        boolean stacked = slot.sideX == slot.boardX;
        int x = stacked ? slot.boardX : slot.sideX;
        int top = stacked ? slot.boardY + slot.cell * 20 + 6 : slot.boardY;
        int previewCell = stacked ? 8 : 10;
        g.setFont(font(11, false));
        int ascent = g.getFontMetrics().getAscent();
        int labelH = g.getFontMetrics().getHeight();
        int gap = 6;
        g.setColor(TEXT);
        g.drawString("홀드", x, top + ascent);
        int holdY = top + labelH + gap;
        int holdH = Math.max(28, previewCell * 2);
        var hold = player.board().hold();
        if (!sealed) boards.paint(g, hold.length == 0 ? new int[0][] : hold, x, holdY, previewCell);
        else paintLock(g, x, holdY, 40, holdH);
        int nextTop = holdY + holdH + gap;
        g.setColor(TEXT);
        g.drawString(sealed ? "다음 1" : "다음", x, nextTop + ascent);
        List<int[][]> previews = player.board().previews().isEmpty()
                ? List.<int[][]>of(player.board().next()) : player.board().previews();
        List<int[][]> extra = player.extendedPreviews();
        int count = previews.size() + extra.size();
        int step = stacked ? 22 : (count > 3 ? 28 : 36);
        int previewY = nextTop + labelH + gap;
        for (int i = 0; i < count; i++) {
            int[][] shape = i < previews.size() ? previews.get(i) : extra.get(i - previews.size());
            int y = previewY + i * step;
            if (sealed && i > 0) paintLock(g, x, y, 40, Math.max(12, step - 6));
            else boards.paint(g, shape, x, y, previewCell);
            if (i == 0) mark(cueNext, x, y, previewCell * 4, previewCell * 4);
        }
    }

    private void paintLock(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(12, 14, 32, 220));
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(GOLD);
        int cx = x + w / 2;
        int cy = y + h / 2;
        g.drawArc(cx - 7, cy - 12, 14, 12, 0, 180);
        g.fillRoundRect(cx - 8, cy - 2, 16, 12, 3, 3);
        g.setColor(new Color(0x07091A));
        g.fillOval(cx - 2, cy + 1, 4, 4);
    }

    private void paintBadges(Graphics2D g, RaidSnapshot.PlayerView player, Slot slot) {
        boolean stacked = slot.sideX == slot.boardX;
        g.setFont(font(11, true));
        int line = g.getFontMetrics().getHeight() + 3;
        int x = stacked ? slot.boardX + 52 : slot.x + 8;
        int y = (stacked ? slot.boardY + slot.cell * 20 + 6 : slot.boardY + slot.cell * 20 + 8)
                + g.getFontMetrics().getAscent();
        for (RaidSnapshot.Effect effect : player.effects()) {
            String label = switch (effect.pattern()) {
                case REVERSE -> "↔ 반전 " + effect.remainingPieces();
                case OVERLOAD -> "⚡ 과부하 " + effect.remainingPieces();
                case SEAL_PREVIEW -> "잠금 봉인 " + effect.remainingPieces();
                case CLOUD -> "빗금 구름 " + effect.remainingPieces();
                default -> null;
            };
            if (label == null) continue;
            g.setColor(switch (effect.pattern()) {
                case REVERSE -> colorBlind ? tetris.ui.fx.A11yColors.PURPLE : MAGENTA;
                case OVERLOAD -> colorBlind ? tetris.ui.fx.A11yColors.ORANGE : CYAN;
                case SEAL_PREVIEW -> colorBlind ? tetris.ui.fx.A11yColors.BLUE : CYAN;
                default -> colorBlind ? tetris.ui.fx.A11yColors.WHITE : CYAN;
            });
            g.drawString(label, x, y);
            y += line;
        }
    }

    private void paintDowned(Graphics2D g, RaidSnapshot.PlayerView player, Slot slot) {
        g.setColor(new Color(30, 32, 40, 170));
        g.fillRect(slot.boardX, slot.boardY, slot.cell * 10, slot.cell * 20);
        int required = Math.max(1, player.reviveRequired());
        int progress = Math.max(0, Math.min(required, player.reviveProgress()));
        int cx = slot.boardX + slot.cell * 5;
        int cy = slot.boardY + slot.cell * 10;
        int r = Math.max(18, slot.cell * 3);
        g.setStroke(new BasicStroke(4f));
        g.setColor(new Color(90, 90, 100));
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        g.setColor(colorBlind ? tetris.ui.fx.A11yColors.SKY : new Color(0x8CFFC1));
        g.drawArc(cx - r, cy - r, r * 2, r * 2, 90, -(int) (360.0 * progress / required));
        g.setStroke(new BasicStroke(1f));
        g.setFont(font(14, true));
        g.setColor(TEXT);
        drawCentered(g, "부활 " + progress + "/" + required, cx, cy + 6);
    }

    private void paintBoss(Graphics2D g, Rectangle area) {
        g.setColor(PANEL);
        g.fillRoundRect(area.x, area.y, area.width, area.height, 20, 20);
        RaidSnapshot.BossView boss = snapshot.boss();
        int pad = 8;
        int art = Math.max(48, area.width - pad * 2);
        int artX = area.x + (area.width - art) / 2;
        int artY = area.y + 8;
        BufferedImage image = assets.boss(boss.id(), boss.level(), art);
        if (image != null) g.drawImage(image, artX, artY, null);
        else paintBossFallback(g, boss.id(), artX, artY, art);
        if (fx.flashAlpha() > 0) {
            g.setColor(new Color(255, 255, 255, Math.min(180, fx.flashAlpha())));
            g.fillRoundRect(artX, artY, art, art, 16, 16);
        }
        int y = artY + art + 14;
        String title = boss.id().displayName() + "  Lv." + boss.level();
        int titleSize = fittedSize(g, title, area.width - pad * 2, 18, 10, true);
        g.setColor(TEXT);
        g.setFont(font(titleSize, true));
        drawCentered(g, title, area.x + area.width / 2, y + g.getFontMetrics().getAscent());
        y += g.getFontMetrics().getHeight() + 6;
        y = paintHp(g, area.x + pad, y, area.width - pad * 2, boss) + 8;
        y = paintPattern(g, area, y, boss) + 14;
        paintGauge(g, area.x + pad, y, area.width - pad * 2);
        String footer = "처치 " + snapshot.kills() + "  ·  " + clock(snapshot.elapsedMillis());
        int footerSize = fittedSize(g, footer, area.width - pad * 2, 13, 9, false);
        g.setFont(font(footerSize, false));
        int footerBase = area.y + area.height - 10;
        if (footerBase > y + 36) {
            g.setColor(DIM);
            drawCentered(g, footer, area.x + area.width / 2, footerBase);
        }
    }

    private void paintBossFallback(Graphics2D g, BossId id, int x, int y, int size) {
        Color body = switch (id) {
            case JELLY_KING -> new Color(0x3DDC8A);
            case STONE_GOLEM -> new Color(0x8D8478);
            case NIGHT_WITCH -> new Color(0x7A4DFF);
            case MECHA_DRAGON -> new Color(0xFF5A3C);
            case GOLDEN_TETRO -> GOLD;
        };
        g.setColor(new Color(body.getRed(), body.getGreen(), body.getBlue(), 40));
        g.fillOval(x + 8, y + 8, size - 16, size - 16);
        g.setColor(body);
        g.fillRoundRect(x + size / 5, y + size / 4, size * 3 / 5, size / 2, 28, 28);
        g.setColor(CYAN);
        g.fillPolygon(new int[] {x + size / 2, x + size / 2 - 16, x + size / 2 + 16},
                new int[] {y + 18, y + size / 3, y + size / 3}, 3);
        g.setColor(new Color(0x07091A));
        g.fillOval(x + size / 2 - 22, y + size / 2 - 8, 10, 10);
        g.fillOval(x + size / 2 + 12, y + size / 2 - 8, 10, 10);
        g.setColor(TEXT);
        g.setFont(font(Math.max(10, size / 12), true));
        drawCentered(g, id.displayName(), x + size / 2, y + size - 12);
    }

    private int paintHp(Graphics2D g, int x, int y, int width, RaidSnapshot.BossView boss) {
        String label = "보스 " + boss.hp() + "/" + boss.maxHp();
        int size = fittedSize(g, label, width, 12, 8, true);
        g.setFont(font(size, true));
        g.setColor(TEXT);
        int base = y + g.getFontMetrics().getAscent();
        g.drawString(label, x, base);
        int barY = base + 4;
        paintBar(g, x, barY, width, 12, boss.hp() / (double) boss.maxHp(), tetris.ui.fx.A11yColors.hp(colorBlind));
        int bottom = barY + 12;
        int[] split = boss.splitHp();
        if (split.length == 0) return bottom;
        int gap = 6;
        int mini = Math.max(16, (width - gap * (split.length - 1)) / split.length);
        int splitY = bottom + 6;
        int labelBottom = splitY + 8;
        for (int i = 0; i < split.length; i++) {
            int hp = Math.max(0, split[i]);
            int max = Math.max(hp, 1);
            int bx = x + i * (mini + gap);
            paintBar(g, bx, splitY, mini, 8, hp / (double) max, tetris.ui.fx.A11yColors.splitHp(colorBlind));
            String splitLabel = "분열" + (i + 1) + " " + hp;
            int labelSize = fittedSize(g, splitLabel, mini, 10, 7, true);
            g.setFont(font(labelSize, true));
            g.setColor(TEXT);
            int splitBase = splitY + 8 + 3 + g.getFontMetrics().getAscent();
            g.drawString(splitLabel, bx, splitBase);
            labelBottom = Math.max(labelBottom, splitBase + 2);
        }
        return labelBottom;
    }

    private int paintPattern(Graphics2D g, Rectangle area, int y, RaidSnapshot.BossView boss) {
        int pad = 8;
        int innerRight = area.x + area.width - pad;
        int icon = Math.min(26, Math.max(16, (area.width - pad * 2) / 5));
        int x = area.x + pad;
        PatternId pattern = boss.nextPattern();
        BufferedImage image = pattern == null ? null : assets.scaled(AssetStore.patternPath(pattern), icon, icon);
        if (image != null) g.drawImage(image, x, y, null);
        else paintPatternFallback(g, pattern, x, y, icon);
        String name = pattern == null ? "패턴 없음" : pattern.displayName();
        String who = targetText(boss.target());
        int textX = x + icon + 6;
        int textW = Math.max(12, innerRight - textX);
        int nameSize = fittedSize(g, name, textW, 13, 8, true);
        g.setColor(TEXT);
        g.setFont(font(nameSize, true));
        int nameBase = y + g.getFontMetrics().getAscent();
        g.drawString(name, textX, nameBase);
        int nameBottom = nameBase + g.getFontMetrics().getDescent();
        int whoSize = fittedSize(g, who, textW, 11, 8, false);
        g.setColor(DIM);
        g.setFont(font(whoSize, false));
        int whoBase = nameBottom + 4 + g.getFontMetrics().getAscent();
        g.drawString(who, textX, whoBase);
        int blockBottom = Math.max(y + icon, whoBase + 2);
        String hint = pattern == PatternId.SEAL_PREVIEW ? "다음 1개만 표시"
                : pattern == PatternId.CURSE_BLOCKS ? "보인 다음 유지"
                : boss.fleeMillis() >= 0 ? "도망까지 " + Math.max(0, boss.fleeMillis() / 1000) + "초"
                : "조각 후 발동";
        String pieces = boss.fleeMillis() >= 0 ? String.valueOf(Math.max(0, boss.fleeMillis() / 1000))
                : String.valueOf(Math.max(0, boss.piecesLeft()));
        int hintSize = fittedSize(g, hint, area.width - pad * 2, 11, 8, false);
        g.setFont(font(hintSize, false));
        g.setColor(DIM);
        int hintBase = blockBottom + 8 + g.getFontMetrics().getAscent();
        g.drawString(hint, x, hintBase);
        int hintBottom = hintBase + g.getFontMetrics().getDescent();
        int numberSize = Math.min(16, Math.max(12, area.width / 9));
        g.setFont(font(numberSize, true));
        int numberBase = hintBottom + 8 + g.getFontMetrics().getAscent();
        g.setColor(colorBlind ? tetris.ui.fx.A11yColors.SKY : CYAN);
        int numW = g.getFontMetrics().stringWidth(pieces);
        g.drawString(pieces, innerRight - numW, numberBase);
        return numberBase + g.getFontMetrics().getDescent() + 8;
    }

    private String targetText(int target) {
        if (target < 0) return "→ 둘 다";
        if (snapshot.mode() == RaidMode.SOLO) return "→ 나";
        return target == 0 ? "→ P1" : "→ P2";
    }

    private void paintPatternFallback(Graphics2D g, PatternId pattern, int x, int y, int size) {
        g.setColor(new Color(20, 16, 40));
        g.fillRoundRect(x, y, size, size, 8, 8);
        g.setColor(CYAN);
        if (pattern == PatternId.CLOUD) {
            for (int i = 4; i < size; i += 5) g.drawLine(x + i, y + 4, x + 4, y + i);
        } else if (pattern == PatternId.PILLAR) {
            g.fillRect(x + size / 2 - 3, y + 6, 6, size - 12);
        } else if (pattern == PatternId.REVERSE) {
            g.drawLine(x + 8, y + size / 2, x + size - 8, y + size / 2);
            g.drawLine(x + 8, y + size / 2, x + 14, y + size / 2 - 5);
            g.drawLine(x + size - 8, y + size / 2, x + size - 14, y + size / 2 + 5);
        } else if (pattern == null) {
            g.drawOval(x + 8, y + 8, size - 16, size - 16);
        } else {
            g.drawRect(x + 8, y + 8, size - 16, size - 16);
            g.drawLine(x + 8, y + 8, x + size - 8, y + size - 8);
        }
    }

    private void paintGauge(Graphics2D g, int x, int y, int width) {
        g.setColor(DIM);
        g.setFont(font(12, false));
        String name = (snapshot.mode() == RaidMode.SOLO ? "필살" : "협동") + " " + snapshot.gauge();
        g.drawString(name, x, y);
        paintBar(g, x, y + 6, width, 12, snapshot.gauge() / 100.0, tetris.ui.fx.A11yColors.gauge(colorBlind));
    }

    private static void paintBar(Graphics2D g, int x, int y, int width, int height, double ratio, Color fill) {
        g.setColor(new Color(16, 12, 32));
        g.fillRoundRect(x, y, width, height, 8, 8);
        int filled = (int) Math.round(width * Math.max(0, Math.min(1, ratio)));
        if (filled <= 0) return;
        g.setColor(fill);
        g.fillRoundRect(x, y, Math.max(height, filled), height, 8, 8);
    }

    private void paintCards(Graphics2D g) {
        RaidSnapshot.CardOffer offer = snapshot.offer();
        g.setColor(new Color(4, 6, 16, 180));
        g.fillRect(0, 0, VW, VH);
        String who = offer.chooser() == 0 ? "P1" : "P2";
        g.setColor(TEXT);
        g.setFont(font(22, true));
        drawCentered(g, who + "이 카드를 고릅니다", VW / 2, 150);
        g.setFont(font(16, false));
        g.setColor(DIM);
        drawCentered(g, String.format("%.1f초  ·  좌우 이동, 하드 드롭 확정", offer.remainingMillis() / 1000.0), VW / 2, 176);
        int cardW = Math.min(140, (VW - 48) / 3 - 8);
        int cardH = 200;
        int gap = 18;
        int total = cardW * 3 + gap * 2;
        int x0 = (VW - total) / 2;
        for (int i = 0; i < offer.cards().size(); i++) {
            int x = x0 + i * (cardW + gap);
            int y = i == cardSelection ? 210 : 224;
            paintCard(g, offer.cards().get(i), x, y, cardW, cardH, i == cardSelection);
        }
    }

    private void paintCard(Graphics2D g, CardId card, int x, int y, int w, int h, boolean selected) {
        BufferedImage image = assets.scaled(AssetStore.cardPath(card), w, h - 52);
        g.setColor(selected ? new Color(18, 24, 48) : new Color(12, 14, 28));
        g.fillRoundRect(x, y, w, h, 14, 14);
        if (image != null) g.drawImage(image, x + 8, y + 8, null);
        else {
            g.setColor(selected ? CYAN : MAGENTA);
            g.fillRoundRect(x + 16, y + 16, w - 32, 90, 12, 12);
        }
        g.setColor(TEXT);
        g.setFont(font(14, true));
        drawCentered(g, card.displayName(), x + w / 2, y + h - 36);
        g.setFont(font(11, false));
        g.setColor(DIM);
        drawCentered(g, card.description(), x + w / 2, y + h - 16);
        g.setColor(selected ? tetris.ui.fx.A11yColors.selected(colorBlind) : new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(selected ? (colorBlind ? 5f : 3f) : 1f));
        g.drawRoundRect(x, y, w, h, 14, 14);
        if (selected) {
            g.setFont(font(16, true));
            g.setColor(tetris.ui.fx.A11yColors.selected(colorBlind));
            drawCentered(g, "▼", x + w / 2, y - 2);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void paintCenter(Graphics2D g, String title, String hint) {
        g.setColor(new Color(4, 6, 16, 150));
        g.fillRect(0, 80, VW, 560);
        g.setColor(TEXT);
        g.setFont(font(72, true));
        drawCentered(g, title, VW / 2, 340);
        g.setFont(font(18, false));
        g.setColor(DIM);
        drawCentered(g, hint, VW / 2, 380);
    }

    private void paintConfirm(Graphics2D g) {
        g.setColor(new Color(4, 6, 16, 170));
        g.fillRect(0, 0, VW, VH);
        int boxW = Math.min(420, VW - 40);
        int boxX = (VW - boxW) / 2;
        int boxY = 240;
        g.setColor(new Color(16, 18, 36));
        g.fillRoundRect(boxX, boxY, boxW, 200, 18, 18);
        g.setColor(colorBlind ? tetris.ui.fx.A11yColors.ORANGE : CYAN);
        g.drawRoundRect(boxX, boxY, boxW, 200, 18, 18);
        g.setColor(TEXT);
        g.setFont(font(20, true));
        drawCentered(g, "레이드를 끝낼까요?", VW / 2, 300);
        g.setFont(font(13, false));
        g.setColor(DIM);
        drawCentered(g, "점수는 저장되지 않습니다", VW / 2, 326);
        menuButton = new Rectangle(boxX + 24, 360, boxW / 2 - 36, 42);
        retryButton = new Rectangle(VW / 2 + 12, 360, boxW / 2 - 36, 42);
        paintChoice(g, menuButton, "끝내기", confirmYes);
        paintChoice(g, retryButton, "계속", !confirmYes);
    }

    private void paintResult(Graphics2D g) {
        RaidSnapshot.Result result = snapshot.result();
        g.setColor(new Color(4, 6, 16, 190));
        g.fillRect(0, 0, VW, VH);
        int boxW = Math.min(460, VW - 40);
        int boxH = 360;
        int boxX = (VW - boxW) / 2;
        int boxY = 160;
        g.setColor(new Color(14, 16, 34));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 20, 20);
        g.setColor(colorBlind ? tetris.ui.fx.A11yColors.PURPLE : MAGENTA);
        g.drawRoundRect(boxX, boxY, boxW, boxH, 20, 20);
        g.setColor(TEXT);
        g.setFont(font(26, true));
        drawCentered(g, "레이드 종료", VW / 2, 210);
        g.setFont(font(18, false));
        drawCentered(g, "처치  " + result.kills(), VW / 2, 260);
        drawCentered(g, "총 데미지  " + result.totalDamage(), VW / 2, 298);
        drawCentered(g, "시간  " + clock(result.survivedMillis()), VW / 2, 336);
        menuButton = new Rectangle(boxX + 24, 390, boxW / 2 - 36, 44);
        retryButton = new Rectangle(VW / 2 + 12, 390, boxW / 2 - 36, 44);
        paintChoice(g, menuButton, "메뉴로", resultIndex == 0);
        paintChoice(g, retryButton, "다시 하기", resultIndex == 1);
        g.setFont(font(13, false));
        g.setColor(DIM);
        drawCentered(g, "←/→ 선택   Enter 확인", VW / 2, 470);
    }

    private void paintChoice(Graphics2D g, Rectangle box, String label, boolean selected) {
        g.setColor(selected ? new Color(40, 28, 64) : new Color(20, 18, 32));
        g.fillRoundRect(box.x, box.y, box.width, box.height, 12, 12);
        g.setColor(selected ? CYAN : DIM);
        g.drawRoundRect(box.x, box.y, box.width, box.height, 12, 12);
        g.setFont(font(16, selected));
        g.setColor(selected ? TEXT : DIM);
        drawCentered(g, label, box.x + box.width / 2, box.y + 28);
    }

    private void paintGuide(Graphics2D g) {
        if (guide.isEmpty() || snapshot.phase() == RaidPhase.FINISHED) return;
        String[] lines = guide.split("\\R");
        g.setFont(font(lines.length > 2 ? 11 : 13, false));
        int lineHeight = g.getFontMetrics().getHeight();
        int band = lineHeight * lines.length + 8;
        int top = VH - band - 6;
        g.setColor(new Color(6, 8, 18, 170));
        g.fillRoundRect(12, top, VW - 24, band, 10, 10);
        g.setColor(DIM);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (g.getFontMetrics().stringWidth(line) > VW - 36) g.setFont(font(10, false));
            drawCentered(g, line, VW / 2, top + 4 + g.getFontMetrics().getAscent() + i * lineHeight);
        }
    }

    private int countdownDigit() {
        if (countdownAnchor < 0) return 3;
        int gone = (int) ((System.currentTimeMillis() - countdownAnchor) / 1000);
        return Math.max(1, 3 - gone);
    }

    private static String clock(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static Font font(int size, boolean bold) {
        return new Font(Font.DIALOG, bold ? Font.BOLD : Font.PLAIN, size);
    }

    private static int fittedSize(Graphics2D g, String text, int maxWidth, int preferred, int min, boolean bold) {
        int size = preferred;
        g.setFont(font(size, bold));
        while (size > min && g.getFontMetrics().stringWidth(text) > maxWidth) {
            g.setFont(font(--size, bold));
        }
        return size;
    }

    private static void drawCentered(Graphics2D g, String text, int centerX, int baseline) {
        g.drawString(text, centerX - g.getFontMetrics().stringWidth(text) / 2, baseline);
    }

    /** 마지막으로 그린 비색상 단서의 패널 좌표. 없으면 빈 사각형 */
    public Rectangle cueBounds(String name) {
        Rectangle source = switch (name) {
            case "target" -> cueTarget;
            case "lucky" -> cueLucky;
            case "next" -> cueNext;
            case "score" -> cueScore;
            default -> new Rectangle();
        };
        return new Rectangle(source);
    }

    private void mark(Rectangle box, int vx, int vy, int vw, int vh) {
        box.setBounds(originX + (int) (vx * scale), originY + (int) (vy * scale),
                Math.max(1, (int) (vw * scale)), Math.max(1, (int) (vh * scale)));
    }

    private record Slot(int x, int y, int w, int h, int boardX, int boardY, int cell, int sideX) {
    }
}
