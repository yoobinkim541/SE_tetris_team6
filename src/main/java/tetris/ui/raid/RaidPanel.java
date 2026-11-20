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
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JPanel;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.CardId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.versus.api.VersusRules;
import tetris.ui.GamePanel;
import tetris.ui.fx.A11yColors;
import tetris.ui.fx.AssetStore;
import tetris.ui.fx.FxLayer;
import tetris.ui.fx.StarfieldBackground;
import tetris.ui.theme.GameTheme;
import tetris.ui.theme.UiKit;
import tetris.ui.versus.BoardRenderer;

/**
 * 보스 레이드 화면. 보스는 보드 위에서 내려다보고, 가운데 기둥은 없다.
 * 600×720 가상 화면은 게임 창과 같은 비라서 세 크기에서 겹침 없이 커진다.
 */
public final class RaidPanel extends JPanel {
    static final int VW = 600;
    static final int VH = 720;

    private static final Color TEXT = new Color(0xF4F7FF);
    private static final Color DIM = new Color(0xC9D2EA);


    private final BoardRenderer boards;
    private final boolean colorBlind;
    private final StarfieldBackground stars;
    private final FxLayer fx = new FxLayer();
    private final AssetStore assets = AssetStore.SHARED;
    private final Scene scene = new Scene();
    private RaidSnapshot snapshot;
    private String guide = "";
    private int cardSelection = 1;
    private int resultIndex;
    private boolean confirming;
    private boolean confirmYes;
    private String menuLabel = "메뉴로";
    private String retryLabel = "다시 하기";
    private String connectionNotice = "";
    private Runnable onMenu = () -> { };
    private Runnable onRetry = () -> { };
    private Runnable onCancelConfirm = () -> setConfirm(false, false);
    private long countdownAnchor = -1;
    private long lastRevision = Long.MIN_VALUE;
    private int stolenSlower = -1;
    private long stolenUntil;
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
            noteStolen(snapshot.events());
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

    public void setResultLabels(String menu, String retry) {
        menuLabel = menu == null || menu.isBlank() ? "메뉴로" : menu;
        retryLabel = retry == null || retry.isBlank() ? "다시 하기" : retry;
    }

    public void setConnectionNotice(String notice) {
        connectionNotice = notice == null ? "" : notice;
        repaint();
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
        cueTarget.setBounds(0, 0, 0, 0);
        cueLucky.setBounds(0, 0, 0, 0);
        cueNext.setBounds(0, 0, 0, 0);
        cueScore.setBounds(0, 0, 0, 0);
        menuButton.setBounds(0, 0, 0, 0);
        retryButton.setBounds(0, 0, 0, 0);
        compose();
        if (snapshot.mode() == RaidMode.RACE) paintRace(g);
        else paintShared(g);
        paintGuide(g);
        paintNotice(g);
        int[] centers = new int[scene.players];
        int[] mids = new int[scene.players];
        for (int i = 0; i < scene.players; i++) {
            centers[i] = scene.play[i].x + scene.play[i].width / 2;
            mids[i] = scene.play[i].y + scene.play[i].height / 2;
        }
        fx.placeSparks(centers, mids);
        fx.paintSparks(g);
        if (scene.players > 0) fx.paintNumbers(g, centers, scene.play[0].y + 36);
        if (snapshot.phase() != RaidPhase.CARD_PICK && snapshot.phase() != RaidPhase.FINISHED) {
            fx.paintBanner(g, VW / 2, scene.play[0].y + 78);
        }
        paintStolen(g);
        if (snapshot.phase() == RaidPhase.COUNTDOWN) paintVeil(g, String.valueOf(countdownDigit()), "곧 시작합니다");
        if (snapshot.phase() == RaidPhase.PAUSED && !confirming) paintVeil(g, "일시정지", "일시정지 키로 다시 시작합니다");
        if (snapshot.phase() == RaidPhase.CARD_PICK && snapshot.offer() != null) paintCards(g);
        if (confirming) paintConfirm(g);
        if (snapshot.phase() == RaidPhase.FINISHED && snapshot.result() != null) paintResult(g);
        markBoss();
    }

    private void paintShared(Graphics2D g) {
        for (int i = 0; i < scene.players; i++) paintPlayer(g, snapshot.players().get(i), i);
        paintWideBoss(g, snapshot.boss());
        paintHpFrame(g, snapshot.boss());
        paintTelegraph(g, snapshot.boss());
        paintBadges(g);
        paintTopCapsules(g, snapshot.boss());
        paintSharedGauge(g);
    }

    private void paintRace(Graphics2D g) {
        for (int i = 0; i < 2; i++) {
            paintRaceHeader(g, i);
            paintPlayer(g, snapshot.players().get(i), i);
        }
        paintBadges(g);
        paintRaceTop(g);
    }

    private void compose() {
        if (snapshot.mode() == RaidMode.RACE) {
            composeRace();
            return;
        }
        boolean solo = snapshot.mode() == RaidMode.SOLO;
        int cell = solo ? 22 : 20;
        int boardW = cell * 10;
        int boardH = cell * 20;
        int boardY = solo ? 214 : 236;
        scene.cell = cell;
        scene.boardY = boardY;
        scene.players = snapshot.players().size();
        if (solo) {
            int holdW = 72;
            int nextW = 84;
            int gap = 10;
            int left = (VW - (holdW + gap + boardW + gap + nextW)) / 2;
            int boardX = left + holdW + gap;
            placePlayer(0, boardX, boardY, boardW, boardH, 8, 14);
            scene.hold[0].setBounds(left, boardY, holdW, cell * 5);
            scene.next[0].setBounds(boardX + boardW + gap, boardY, nextW, cell * 8);
            scene.score[0].setBounds(boardX, 670, boardW, 16);
            scene.level.setBounds(boardX + boardW + gap, 664, nextW, 22);
            scene.effects[0].setBounds(left, boardY + cell * 5 + 22, holdW, boardH - cell * 5 - 24);
            scene.gauge.setBounds(boardX + boardW + gap, boardY + cell * 8 + 8, nextW, 20);
            clearPlayer(1);
        } else {
            int tabW = 80;
            int sideGap = 6;
            int mid = 10;
            int cluster = (tabW + sideGap + boardW) * 2 + mid;
            int left = (VW - cluster) / 2;
            for (int i = 0; i < 2; i++) {
                int boardX = left + tabW + sideGap + i * (boardW + mid);
                int tabX = i == 0 ? left : boardX + boardW + sideGap;
                placePlayer(i, boardX, boardY, boardW, boardH, 8, 14);
                scene.score[i].setBounds(boardX, boardY + boardH + 18, boardW, 22);
                scene.hold[i].setBounds(tabX, boardY + 8, tabW, 64);
                scene.next[i].setBounds(tabX, boardY + 76, tabW, boardH - 190);
                scene.effects[i].setBounds(tabX, boardY + boardH - 110, tabW, 110);
            }
            scene.level.setBounds(0, 0, 0, 0);
            scene.gauge.setBounds(0, 0, 0, 0);
        }
        scene.boss.setBounds(8, 32, VW - 16, boardY - 34);
        int split = snapshot.boss().splitHp().length > 0 ? 24 : 0;
        int bannerH = 36;
        int badge = 30;
        scene.banner.setBounds(72, boardY - badge - bannerH - 4, VW - 144, bannerH);
        int hpH = (solo ? 44 : 88) + split;
        scene.hp.setBounds(66, scene.banner.y - 6 - hpH, VW - 132, hpH);
        scene.kill.setBounds(10, 4, 118, 24);
        scene.timer.setBounds(VW - 128, 4, 118, 24);
        scene.name.setBounds(136, 4, VW - 272, 24);
        scene.guide.setBounds(12, 688, VW - 24, 26);
        scene.header[0].setBounds(0, 0, 0, 0);
        scene.raceArt[0].setBounds(0, 0, 0, 0);
        scene.raceArt[1].setBounds(0, 0, 0, 0);
    }

    private void composeRace() {
        int cell = 23;
        int boardW = cell * 10;
        int boardH = cell * 20;
        int margin = 8;
        int colGap = 10;
        int colW = (VW - margin * 2 - colGap) / 2;
        int headerH = 84;
        int previewH = 46;
        int top = 36;
        int boardY = top + headerH + previewH + 8;
        scene.cell = cell;
        scene.boardY = boardY;
        scene.players = 2;
        for (int i = 0; i < 2; i++) {
            int colX = margin + i * (colW + colGap);
            int boardX = colX + (colW - boardW) / 2;
            scene.header[i].setBounds(colX, top, colW, headerH);
            scene.raceArt[i].setBounds(colX + 10, top + 20, 44, 44);
            placePlayer(i, boardX, boardY, boardW, boardH, 8, 10);
            scene.hold[i].setBounds(colX + 4, top + headerH + 4, 62, previewH);
            scene.next[i].setBounds(colX + 70, top + headerH + 4, colW - 74, previewH);
            scene.score[i].setBounds(boardX, boardY + boardH + 16, 110, 20);
            scene.effects[i].setBounds(boardX + 116, boardY + boardH + 4, boardW - 116, 20);
        }
        int raceW = 200;
        int timeW = 156;
        int x = (VW - (raceW + 8 + timeW)) / 2;
        scene.kill.setBounds(x, 4, raceW, 26);
        scene.timer.setBounds(x + raceW + 8, 4, timeW, 26);
        scene.name.setBounds(0, 0, 0, 0);
        scene.hp.setBounds(0, 0, 0, 0);
        scene.banner.setBounds(0, 0, 0, 0);
        scene.gauge.setBounds(0, 0, 0, 0);
        scene.level.setBounds(0, 0, 0, 0);
        scene.boss.setBounds(scene.raceArt[0]);
        scene.guide.setBounds(12, 688, VW - 24, 24);
    }

    private void placePlayer(int index, int boardX, int boardY, int boardW, int boardH, int side, int top) {
        scene.play[index].setBounds(boardX, boardY, boardW, boardH);
        scene.frame[index].setBounds(boardX - side, boardY - top, boardW + side * 2, boardH + top + 1);
    }

    private void clearPlayer(int index) {
        scene.play[index].setBounds(0, 0, 0, 0);
        scene.frame[index].setBounds(0, 0, 0, 0);
        scene.hold[index].setBounds(0, 0, 0, 0);
        scene.next[index].setBounds(0, 0, 0, 0);
        scene.score[index].setBounds(0, 0, 0, 0);
        scene.effects[index].setBounds(0, 0, 0, 0);
        scene.header[index].setBounds(0, 0, 0, 0);
    }

    private void paintPlayer(Graphics2D g, RaidSnapshot.PlayerView player, int index) {
        Rectangle play = scene.play[index];
        if (play.width <= 0) return;
        paintFrame(g, index, play);
        paintPlayfield(g, player, play);
        paintSide(g, player, index);
        paintScore(g, player, index);
        paintEffects(g, player, index);
        if (snapshot.mode() != RaidMode.SOLO && isTarget(index)) paintTarget(g, index);
        if (!player.alive()) paintDowned(g, player, play);
    }

    private void paintPlayfield(Graphics2D g, RaidSnapshot.PlayerView player, Rectangle play) {
        var board = player.board();
        boards.paintPlayfield(g, board.cells(), board.items(), play.x, play.y, scene.cell);
        boards.paintGhost(g, board.ghost(), play.x, play.y, scene.cell);
        paintLucky(g, player.lucky(), play);
        paintCloud(g, player, play);
    }

    private void paintFrame(Graphics2D g, int index, Rectangle play) {
        Rectangle frame = scene.frame[index];
        var clip = g.getClip();
        Area outside = new Area(new Rectangle(0, 0, VW, VH));
        outside.subtract(new Area(play));
        g.setClip(outside);
        UiKit.Tone tone = index == 0 ? UiKit.Tone.CYAN : UiKit.Tone.MAGENTA;
        UiKit.paintFrame(g, tone, frame.x, frame.y, frame.width, frame.height);
        g.setClip(clip);
    }

    private void paintBadges(Graphics2D g) {
        if (snapshot.mode() == RaidMode.RACE) return;
        for (int index = 0; index < scene.players; index++) {
            Rectangle frame = scene.frame[index];
            if (frame.width <= 0) continue;
            int size = 28;
            int x = frame.x + frame.width / 2 - size / 2;
            int y = scene.boardY - size;
            UiKit.paintBadge(g, index == 0, x, y, size);
            String name = (colorBlind ? (index == 0 ? "■ " : "● ") : "") + (index == 0 ? "P1" : "P2");
            g.setFont(GameTheme.fontFor(name, 14, false));
            g.setColor(index == 0 ? UiKit.CYAN : UiKit.MAGENTA);
            int nameX = x + size + 4;
            if (nameX + g.getFontMetrics().stringWidth(name) > frame.x + frame.width - 4) {
                nameX = x - 4 - g.getFontMetrics().stringWidth(name);
            }
            g.drawString(name, nameX, y + size / 2 + 5);
        }
    }

    private void paintScore(Graphics2D g, RaidSnapshot.PlayerView player, int index) {
        Rectangle box = scene.score[index];
        if (box.width <= 0) return;
        paintPill(g, box.x, box.y, box.width, box.height);
        String label = "점수";
        g.setFont(GameTheme.fontFor(label, 12, false));
        g.setColor(UiKit.CYAN);
        UiKit.drawCentered(g, label, box.x + box.width / 2, box.y - 3);
        String digits = String.valueOf(player.board().score());
        int size = fit(g, digits, box.width - 20, 15, 11, true);
        g.setFont(GameTheme.digits(size));
        g.setColor(TEXT);
        int baseline = box.y + (box.height + g.getFontMetrics().getAscent()) / 2 - 2;
        UiKit.drawCentered(g, digits, box.x + box.width / 2, baseline);
        if (index == 0) mark(cueScore, box.x + 8, box.y + 2, box.width - 16, box.height - 4);
    }

    private void paintSide(Graphics2D g, RaidSnapshot.PlayerView player, int index) {
        boolean sealed = player.effects().stream().anyMatch(effect -> effect.pattern() == PatternId.SEAL_PREVIEW);
        Rectangle hold = scene.hold[index];
        Rectangle next = scene.next[index];
        boolean cyan = index == 0;
        if (hold.width > 0) {
            UiKit.paintTab(g, cyan, hold.x, hold.y, hold.width, hold.height);
            String holdLabel = colorBlind ? (index == 0 ? "■ 홀드" : "● 홀드") : "홀드";
            labelTab(g, holdLabel, hold);
            var held = player.board().hold();
            int cell = Math.max(8, Math.min(12, (hold.width - 8) / 4));
            int pieceY = hold.y + 16;
            if (!sealed) boards.paint(g, held.length == 0 ? new int[0][] : held, hold.x + 4, pieceY, cell);
            else paintLock(g, hold.x + 4, pieceY, Math.max(16, hold.width - 8), Math.max(18, hold.height - 22));
        }
        if (next.width <= 0) return;
        UiKit.paintTab(g, index == 0, next.x, next.y, next.width, next.height);
        labelTab(g, sealed ? "다음 1" : "다음", next);
        java.util.ArrayList<int[][]> shapes = new java.util.ArrayList<>();
        if (player.board().previews().isEmpty()) shapes.add(player.board().next());
        else shapes.addAll(player.board().previews());
        shapes.addAll(player.extendedPreviews());
        int cell = Math.max(7, Math.min(11, (next.width - 8) / 4));
        boolean row = next.height < cell * 4 + 18;
        int pieceTop = next.y + 16;
        for (int i = 0; i < shapes.size(); i++) {
            int x = row ? next.x + 4 + i * (cell * 4 + 4) : next.x + (next.width - cell * 4) / 2;
            int y = row ? pieceTop : pieceTop + i * (cell * 3 + 2);
            if (row && x + cell * 4 > next.x + next.width) break;
            if (!row && y + cell * 2 > next.y + next.height) break;
            if (sealed && i > 0) paintLock(g, x, y, cell * 4, Math.max(12, cell * 2));
            else boards.paint(g, shapes.get(i), x, y, cell);
            if (i == 0 && index == 0) mark(cueNext, x + cell, y, cell, cell * 4);
        }
    }

    private void labelTab(Graphics2D g, String text, Rectangle box) {
        int size = fit(g, text, box.width - 4, 11, 8, false);
        g.setFont(GameTheme.text(size, true));
        g.setColor(TEXT);
        UiKit.drawCentered(g, text, box.x + box.width / 2, box.y + 12);
    }

    private void paintEffects(Graphics2D g, RaidSnapshot.PlayerView player, int index) {
        Rectangle box = scene.effects[index];
        if (box.width <= 0) return;
        int y = box.y;
        int row = 22;
        int pending = player.board().pendingRows().size();
        if (pending > 0 && y + row <= box.y + box.height) {
            paintChip(g, box.x, y, box.width, "대기 " + pending, null, A11yColors.warning(colorBlind));
            y += row;
        }
        for (RaidSnapshot.Effect effect : player.effects()) {
            if (y + row > box.y + box.height) break;
            String label = effectLabel(effect);
            if (label == null) continue;
            BufferedImage icon = assets.scaled(AssetStore.patternPath(effect.pattern()), 16, 16);
            paintChip(g, box.x, y, box.width, label, icon, effectColor(effect.pattern()));
            y += row;
        }
    }

    private void paintChip(Graphics2D g, int x, int y, int width, String label, BufferedImage icon, Color color) {
        g.setColor(new Color(8, 12, 28, 210));
        g.fillRoundRect(x, y, width, 20, 8, 8);
        int textX = x + 4;
        if (icon != null) {
            g.drawImage(icon, x + 3, y + 2, null);
            textX += 18;
        }
        int size = fit(g, label, Math.max(12, width - (textX - x) - 4), 16, 14, false);
        g.setFont(GameTheme.fontFor(label, size, false));
        g.setColor(color);
        g.drawString(label, textX, y + 15);
    }

    private String effectLabel(RaidSnapshot.Effect effect) {
        String name = switch (effect.pattern()) {
            case REVERSE -> "반전";
            case OVERLOAD -> "과부하";
            case SEAL_PREVIEW -> "봉인";
            case CLOUD -> "구름";
            default -> null;
        };
        if (name == null) return null;
        return name + " " + effect.remainingPieces();
    }

    private Color effectColor(PatternId pattern) {
        if (!colorBlind) return pattern == PatternId.REVERSE ? UiKit.MAGENTA : UiKit.CYAN;
        return switch (pattern) {
            case REVERSE -> A11yColors.PURPLE;
            case OVERLOAD -> A11yColors.ORANGE;
            case SEAL_PREVIEW -> A11yColors.BLUE;
            default -> A11yColors.WHITE;
        };
    }

    private void paintTarget(Graphics2D g, int index) {
        Rectangle play = scene.play[index];
        Color markColor = A11yColors.target(colorBlind);
        g.setColor(markColor);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {7f, 4f}, 0f));
        g.drawRoundRect(play.x - 3, play.y - 3, play.width + 5, play.height + 5, 8, 8);
        g.setStroke(new BasicStroke(1f));
        String label = "▼ 대상";
        g.setFont(GameTheme.fontFor(label, 16, false));
        int width = g.getFontMetrics().stringWidth(label);
        int x = play.x + (play.width - width) / 2;
        int baseline = play.y + 22;
        g.setColor(new Color(6, 8, 20, 190));
        g.fillRoundRect(x - 8, baseline - 16, width + 16, 20, 8, 8);
        g.setColor(markColor);
        g.drawString(label, x, baseline);
        mark(cueTarget, x, baseline - 15, width + 2, 16);
    }

    private void paintDowned(Graphics2D g, RaidSnapshot.PlayerView player, Rectangle play) {
        g.setColor(new Color(16, 12, 32, 150));
        g.fillRect(play.x, play.y, play.width, play.height);
        int required = Math.max(1, player.reviveRequired());
        int progress = Math.max(0, Math.min(required, player.reviveProgress()));
        int cx = play.x + play.width / 2;
        int cy = play.y + play.height / 2;
        int r = Math.max(28, scene.cell * 3);
        g.setStroke(new BasicStroke(5f));
        g.setColor(new Color(96, 96, 118));
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        g.setColor(colorBlind ? A11yColors.SKY : new Color(0x8CFFC1));
        g.drawArc(cx - r, cy - r, r * 2, r * 2, 90, -(int) (360.0 * progress / required));
        g.setStroke(new BasicStroke(1f));
        String text = "부활 " + progress + "/" + required;
        g.setFont(GameTheme.text(16, true));
        g.setColor(TEXT);
        UiKit.drawCentered(g, text, cx, cy + 6);
    }

    private void paintWideBoss(Graphics2D g, RaidSnapshot.BossView boss) {
        Rectangle window = scene.boss;
        int clipH = Math.max(1, scene.boardY - window.y);
        Graphics2D layer = (Graphics2D) g.create();
        try {
            layer.clipRect(window.x, window.y, window.width, clipH);
            BufferedImage image = assets.boss(boss.id(), boss.level(), window.width);
            if (image == null) paintBossFallback(layer, boss.id(), window.x + window.width / 2 - 90, window.y + 4, 180);
            else layer.drawImage(image, window.x, window.y - window.width / 7, window.width, window.width, null);
        } finally {
            layer.dispose();
        }
        if (fx.flashAlpha() <= 0) return;
        Graphics2D flash = (Graphics2D) g.create();
        try {
            flash.clipRect(window.x, window.y, window.width, clipH);
            flash.setColor(new Color(255, 255, 255, Math.min(140, fx.flashAlpha())));
            flash.fillRect(window.x, window.y, window.width, clipH);
        } finally {
            flash.dispose();
        }
    }

    private void paintHpFrame(Graphics2D g, RaidSnapshot.BossView boss) {
        Rectangle box = scene.hp;
        if (box.width <= 0) return;
        UiKit.paintFrame(g, UiKit.Tone.VIOLET, box.x, box.y, box.width, box.height);
        int inset = 14;
        int barY = box.y + 14;
        int barW = box.width - inset * 2;
        UiKit.paintGauge(g, box.x + inset, barY, barW, 14,
                boss.hp() / (float) Math.max(1, boss.maxHp()), A11yColors.hp(colorBlind));
        paintMixed(g, boss.hp() + "/" + boss.maxHp(), box.x + box.width / 2 - 24, barY + 12, 14);
        String percent = Math.round(100.0 * boss.hp() / Math.max(1, boss.maxHp())) + "%";
        paintMixed(g, percent, box.x + box.width - inset - 22, barY + 12, 14);
        if (snapshot.mode() == RaidMode.COOP) {
            int gaugeY = barY + 30;
            String gaugeName = "협동";
            g.setFont(GameTheme.fontFor(gaugeName, 14, false));
            g.setColor(TEXT);
            g.drawString(gaugeName, box.x + inset, gaugeY + 14);
            int labelW = g.getFontMetrics().stringWidth(gaugeName) + 8;
            g.setFont(GameTheme.digits(14));
            String digits = String.valueOf(snapshot.gauge());
            g.drawString(digits, box.x + inset + labelW, gaugeY + 14);
            int used = labelW + g.getFontMetrics().stringWidth(digits) + 10;
            UiKit.paintGauge(g, box.x + inset + used, gaugeY + 2, Math.max(40, barW - used), 14,
                    snapshot.gauge() / 100f, A11yColors.gauge(colorBlind));
        }
        int[] split = boss.splitHp();
        if (split.length == 0) return;
        int gap = 6;
        int mini = Math.max(24, (box.width - inset * 2 - gap * (split.length - 1)) / split.length);
        int splitY = barY + (snapshot.mode() == RaidMode.COOP ? 38 : 22);
        for (int i = 0; i < split.length; i++) {
            int hp = Math.max(0, split[i]);
            int bx = box.x + inset + i * (mini + gap);
            UiKit.paintGauge(g, bx, splitY, mini, 8, hp / (float) Math.max(hp, 1), A11yColors.splitHp(colorBlind));
            g.setFont(GameTheme.text(9, false));
            g.setColor(TEXT);
            g.drawString("분열" + (i + 1) + " " + hp, bx, splitY + 18);
        }
    }

    private void paintTelegraph(Graphics2D g, RaidSnapshot.BossView boss) {
        Rectangle box = scene.banner;
        if (box.width <= 0 || boss == null) return;
        UiKit.paintBanner(g, box.x, box.y, box.width, box.height);
        PatternId pattern = boss.nextPattern();
        int inset = 108;
        int x = box.x + inset;
        int mid = box.y + box.height / 2 + 1;
        BufferedImage icon = pattern == null ? null : assets.scaled(AssetStore.patternPath(pattern), 18, 18);
        if (icon != null) g.drawImage(icon, x, mid - 9, null);
        else paintPatternFallback(g, x, mid - 9, 18);
        x += 22;
        boolean flee = boss.fleeMillis() >= 0;
        int pieces = flee ? (int) Math.max(0, boss.fleeMillis() / 1000) : Math.max(0, boss.piecesLeft());
        String after = pieces + (flee ? "초 후" : "조각 후");
        String number = String.valueOf(pieces);
        g.setFont(GameTheme.digits(20));
        int numberW = g.getFontMetrics().stringWidth(number);
        g.setFont(GameTheme.text(12, false));
        int afterW = g.getFontMetrics().stringWidth(after);
        int right = box.x + box.width - inset;
        int afterX = right - afterW;
        int numberX = afterX - 8 - numberW;

        String name = pattern == null ? "패턴 없음" : pattern.displayName();
        String who = targetText(boss.target());
        int textW = Math.max(48, numberX - x - 8);
        int nameSize = fit(g, name, textW * 3 / 5, 15, 11, false);
        g.setFont(GameTheme.text(nameSize, true));
        g.setColor(TEXT);
        int baseline = mid + g.getFontMetrics().getAscent() / 2 - 2;
        g.drawString(name, x, baseline);
        int whoX = x + g.getFontMetrics().stringWidth(name) + 6;
        int whoSize = fit(g, who, Math.max(28, numberX - whoX - 4), 13, 10, false);
        g.setFont(GameTheme.text(whoSize, true));
        g.setColor(colorBlind ? A11yColors.ORANGE : new Color(0xFFE27A));
        g.drawString(who, whoX, baseline);
        g.setFont(GameTheme.digits(22));
        g.setColor(TEXT);
        g.drawString(number, numberX, baseline + 1);
        g.setFont(GameTheme.text(13, false));
        g.drawString(after, afterX, baseline);
    }

    private void paintTopCapsules(Graphics2D g, RaidSnapshot.BossView boss) {
        paintCapsuleText(g, scene.kill, "처치 " + snapshot.kills(), false);
        paintCapsuleText(g, scene.name, boss.id().displayName() + "  Lv." + boss.level(), false);
        paintCapsuleText(g, scene.timer, clock(snapshot.elapsedMillis()), true);
    }

    private void paintSharedGauge(Graphics2D g) {
        Rectangle box = scene.gauge;
        if (box.width <= 0) return;
        if (snapshot.mode() == RaidMode.SOLO) {
            paintPill(g, box.x, box.y, box.width, box.height);
            String label = "필살 " + snapshot.gauge();
            int size = fit(g, label, box.width - 8, 12, 10, false);
            g.setFont(GameTheme.fontFor(label, size, false));
            g.setColor(TEXT);
            UiKit.drawCentered(g, label, box.x + box.width / 2, box.y + 14);
            paintCapsuleText(g, scene.level, "Lv." + snapshot.boss().level(), true);
            return;
        }
        UiKit.paintCapsule(g, box.x, box.y, box.width, box.height);
        String name = "협동 " + snapshot.gauge();
        g.setFont(GameTheme.text(11, false));
        g.setColor(TEXT);
        g.drawString(name, box.x + 10, box.y + 12);
        int labelW = g.getFontMetrics().stringWidth(name) + 16;
        UiKit.paintGauge(g, box.x + labelW, box.y + 3, box.width - labelW - 8, box.height - 6,
                snapshot.gauge() / 100f, A11yColors.gauge(colorBlind));
    }

    private void paintRaceTop(Graphics2D g) {
        int[] kills = snapshot.raceKills().length == 2 ? snapshot.raceKills() : new int[] {0, 0};
        Rectangle box = scene.kill;
        UiKit.paintCapsule(g, box.x, box.y, box.width, box.height);
        String left = (colorBlind ? "■ " : "") + "P1";
        String right = "P2" + (colorBlind ? " ●" : "");
        g.setFont(GameTheme.text(14, true));
        int baseline = box.y + (box.height + g.getFontMetrics().getAscent()) / 2 - 2;
        g.setColor(indexColor(0));
        g.drawString(left, box.x + 36, baseline);
        g.setFont(GameTheme.digits(16));
        g.setColor(TEXT);
        String score = kills[0] + " : " + kills[1];
        UiKit.drawCentered(g, score, box.x + box.width / 2, baseline);
        g.setFont(GameTheme.text(14, true));
        g.setColor(indexColor(1));
        int rightW = g.getFontMetrics().stringWidth(right);
        g.drawString(right, box.x + box.width - 36 - rightW, baseline);
        long remain = Math.max(0, tetris.feature.raid.RaidRules.RACE_LIMIT_MILLIS - snapshot.elapsedMillis());
        paintCapsuleText(g, scene.timer, "남은 시간 " + clock(remain), false);
    }

    private void paintRaceHeader(Graphics2D g, int index) {
        Rectangle card = scene.header[index];
        UiKit.Tone tone = index == 0 ? UiKit.Tone.CYAN : UiKit.Tone.MAGENTA;
        UiKit.paintFrame(g, tone, card.x, card.y, card.width, card.height);
        RaidSnapshot.BossView boss = snapshot.bossFor(index);
        Rectangle art = scene.raceArt[index];
        BufferedImage image = assets.boss(boss.id(), boss.level(), art.width);
        if (image != null) g.drawImage(image, art.x, art.y, art.width, art.height, null);
        else paintBossFallback(g, boss.id(), art.x, art.y, art.width);
        if (index == 0 && fx.flashAlpha() > 0) {
            g.setColor(new Color(255, 255, 255, Math.min(130, fx.flashAlpha())));
            g.fillRoundRect(art.x, art.y, art.width, art.height, 10, 10);
        }
        int textX = art.x + art.width + 14;
        int textRight = card.x + card.width - 24;
        int textW = Math.max(20, textRight - textX);
        String title = (colorBlind ? (index == 0 ? "■ " : "● ") : "") + boss.id().displayName();
        int titleSize = fit(g, title, textW, 13, 9, false);
        g.setFont(GameTheme.text(titleSize, true));
        g.setColor(TEXT);
        g.drawString(title, textX, card.y + 22);
        int barY = card.y + 26;
        int barW = Math.max(40, textW - 62);
        UiKit.paintGauge(g, textX, barY, barW, 10, boss.hp() / (float) Math.max(1, boss.maxHp()), A11yColors.hp(colorBlind));
        paintMixed(g, boss.hp() + "/" + boss.maxHp(), textX + barW + 28, barY + 9, 11);
        PatternId pattern = boss.nextPattern();
        boolean flee = boss.fleeMillis() >= 0;
        int pieces = flee ? (int) Math.max(0, boss.fleeMillis() / 1000) : Math.max(0, boss.piecesLeft());
        String next = pattern == null ? "패턴 없음" : pattern.displayName();
        String pieceText = pieces + (flee ? "초" : "조각");
        int pieceSize = 12;
        g.setFont(GameTheme.text(pieceSize, false));
        int pieceW = g.getFontMetrics().stringWidth(pieceText);
        int nextSize = fit(g, next, Math.max(24, textW - pieceW - 8), 12, 9, false);
        g.setFont(GameTheme.text(nextSize, false));
        g.setColor(colorBlind ? A11yColors.SKY : UiKit.CYAN);
        g.drawString(next, textX, card.y + 52);
        g.setFont(GameTheme.digits(13));
        g.setColor(TEXT);
        g.setFont(GameTheme.fontFor(pieceText, 12, false));
        g.drawString(pieceText, textX + g.getFontMetrics().stringWidth(next) + 6, card.y + 52);
        int gauge = snapshot.gaugeFor(index);
        UiKit.paintGauge(g, textX, card.y + 60, textW - 28, 8, gauge / 100f, A11yColors.gauge(colorBlind));
        g.setFont(GameTheme.digits(11));
        g.drawString(String.valueOf(gauge), textX + textW - 36, card.y + 68);
    }

    private void paintStolen(Graphics2D g) {
        if (snapshot.mode() != RaidMode.RACE || stolenSlower < 0 || System.currentTimeMillis() > stolenUntil) return;
        if (stolenSlower >= scene.players) return;
        Rectangle play = scene.play[stolenSlower];
        int w = Math.min(play.width - 16, 168);
        int h = 26;
        int x = play.x + (play.width - w) / 2;
        int y = play.y + 8;
        paintPill(g, x, y, w, h);
        int size = fit(g, "선수를 빼앗김", w - 12, 13, 11, false);
        g.setFont(GameTheme.text(size, true));
        g.setColor(TEXT);
        UiKit.drawCentered(g, "선수를 빼앗김", x + w / 2, y + 18);
    }

    private void paintCapsuleText(Graphics2D g, Rectangle box, String text, boolean digits) {
        if (box.width <= 0 || text == null || text.isEmpty()) return;
        paintPill(g, box.x, box.y, box.width, box.height);
        int size = fit(g, text, box.width - 12, digits ? 14 : 13, 9, digits);
        g.setFont(digits ? GameTheme.digits(size) : GameTheme.fontFor(text, size, false));
        g.setColor(TEXT);
        int baseline = box.y + (box.height + g.getFontMetrics().getAscent()) / 2 - 2;
        UiKit.drawCentered(g, text, box.x + box.width / 2, baseline);
    }

    private void paintPill(Graphics2D g, int x, int y, int w, int h) {
        if (w >= 150) {
            UiKit.paintCapsule(g, x, y, w, h);
            return;
        }
        g.setColor(new Color(8, 14, 32, 220));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(new Color(0x3DE0FF));
        g.drawRoundRect(x, y, w - 1, h - 1, h, h);
    }

    private void paintGlass(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(6, 8, 22, 235));
        g.fillRoundRect(x + 12, y + 12, Math.max(1, w - 24), Math.max(1, h - 24), 16, 16);
    }

    private void paintCards(Graphics2D g) {
        RaidSnapshot.CardOffer offer = snapshot.offer();
        veilBoards(g);
        int panelY = scene.boardY + 8;
        paintGlass(g, 24, panelY, VW - 48, VH - panelY - 40);
        UiKit.paintFrame(g, UiKit.Tone.VIOLET, 24, panelY, VW - 48, VH - panelY - 40);
        String title = offer.chooser() == 0 ? "P1이 카드를 고릅니다" : "P2가 카드를 고릅니다";
        g.setFont(GameTheme.fontFor(title, 20, false));
        g.setColor(TEXT);
        UiKit.drawCentered(g, title, VW / 2, panelY + 32);
        g.setFont(GameTheme.digits(18));
        g.setColor(colorBlind ? A11yColors.YELLOW : new Color(0xFFE27A));
        UiKit.drawCentered(g, String.format("%.1f", offer.remainingMillis() / 1000.0), VW / 2, panelY + 56);
        String hint = "초 - 좌우 이동, 하드 드롭 확정";
        g.setFont(GameTheme.fontFor(hint, 12, false));
        g.setColor(DIM);
        UiKit.drawCentered(g, hint, VW / 2, panelY + 74);
        int cardW = 172;
        int cardH = 196;
        int gap = 14;
        int x0 = (VW - (cardW * 3 + gap * 2)) / 2;
        int y0 = panelY + 88;
        for (int i = 0; i < offer.cards().size() && i < 3; i++) {
            int x = x0 + i * (cardW + gap);
            int y = i == cardSelection ? y0 : y0 + 10;
            paintCard(g, offer.cards().get(i), x, y, cardW, cardH, i == cardSelection);
        }
    }

    private void paintCard(Graphics2D g, CardId card, int x, int y, int w, int h, boolean selected) {
        if (selected) {
            Color glow = A11yColors.selected(colorBlind);
            g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 70));
            g.fillRoundRect(x - 6, y - 8, w + 12, h + 14, 18, 18);
        }
        paintGlass(g, x, y, w, h);
        UiKit.paintFrame(g, selected ? UiKit.Tone.CYAN : UiKit.Tone.VIOLET, x, y, w, h);
        BufferedImage image = assets.scaled(AssetStore.cardPath(card), w - 52, h - 112);
        if (image != null) g.drawImage(image, x + 26, y + 18, null);
        int inset = 38;
        int textW = Math.max(40, w - inset * 2);
        int nameSize = fit(g, card.displayName(), textW, 13, 10, false);
        g.setFont(GameTheme.fontFor(card.displayName(), nameSize, false));
        g.setColor(TEXT);
        UiKit.drawCentered(g, card.displayName(), x + w / 2, y + h - 62);
        g.setFont(GameTheme.fontFor(card.description(), 11, false));
        g.setColor(DIM);
        drawWrapped(g, card.description(), x + inset, y + h - 46, textW);
        if (!selected) return;
        g.setFont(GameTheme.text(18, true));
        g.setColor(A11yColors.selected(colorBlind));
        UiKit.drawCentered(g, "▼", x + w / 2, y - 2);
    }

    private void paintVeil(Graphics2D g, String title, String hint) {
        veilBoards(g);
        int y = scene.play[0].y + scene.play[0].height / 2;
        UiKit.paintGlowText(g, title, GameTheme.popup(VH), TEXT, VW / 2, y);
        g.setFont(GameTheme.text(16, false));
        g.setColor(DIM);
        UiKit.drawCentered(g, hint, VW / 2, y + 28);
    }

    private void paintConfirm(Graphics2D g) {
        veilBoards(g);
        int boxW = 400;
        int boxH = 168;
        int boxX = (VW - boxW) / 2;
        int boxY = scene.boardY + 70;
        paintGlass(g, boxX, boxY, boxW, boxH);
        UiKit.paintFrame(g, UiKit.Tone.VIOLET, boxX, boxY, boxW, boxH);
        g.setFont(GameTheme.text(22, true));
        g.setColor(TEXT);
        UiKit.drawCentered(g, "레이드를 끝낼까요?", VW / 2, boxY + 42);
        g.setFont(GameTheme.text(13, false));
        g.setColor(DIM);
        UiKit.drawCentered(g, "점수는 저장되지 않습니다", VW / 2, boxY + 66);
        menuButton = new Rectangle(boxX + 24, boxY + 96, boxW / 2 - 36, 42);
        retryButton = new Rectangle(VW / 2 + 12, boxY + 96, boxW / 2 - 36, 42);
        paintChoice(g, menuButton, "끝내기", confirmYes);
        paintChoice(g, retryButton, "계속", !confirmYes);
    }

    private void paintResult(Graphics2D g) {
        RaidSnapshot.Result result = snapshot.result();
        veilBoards(g);
        int boxW = 440;
        int boxH = 268;
        int boxX = (VW - boxW) / 2;
        int boxY = scene.boardY + 12;
        paintGlass(g, boxX, boxY, boxW, boxH);
        UiKit.paintFrame(g, UiKit.Tone.MAGENTA, boxX, boxY, boxW, boxH);
        String title = snapshot.mode() == RaidMode.RACE ? raceWinner(result) : "레이드 종료";
        g.setFont(GameTheme.text(24, true));
        g.setColor(TEXT);
        UiKit.drawCentered(g, title, VW / 2, boxY + 36);
        int row = boxY + 52;
        if (snapshot.mode() == RaidMode.RACE && snapshot.raceKills().length == 2) {
            int[] kills = snapshot.raceKills();
            paintStat(g, boxX + 28, row, boxW - 56, playerMark(0) + "  " + kills[0] + "  :  " + kills[1] + "  " + playerMark(1));
            row += 34;
        }
        paintStat(g, boxX + 28, row, boxW - 56, "처치  " + result.kills());
        paintStat(g, boxX + 28, row + 34, boxW - 56, "총 데미지  " + result.totalDamage());
        paintStat(g, boxX + 28, row + 68, boxW - 56, "시간  " + clock(result.survivedMillis()));
        menuButton = new Rectangle(boxX + 28, boxY + boxH - 58, boxW / 2 - 40, 40);
        retryButton = new Rectangle(VW / 2 + 12, boxY + boxH - 58, boxW / 2 - 40, 40);
        paintChoice(g, menuButton, menuLabel, resultIndex == 0);
        paintChoice(g, retryButton, retryLabel, resultIndex == 1);
    }

    private void paintStat(Graphics2D g, int x, int y, int w, String text) {
        UiKit.paintCapsule(g, x, y, w, 28);
        g.setFont(GameTheme.fontFor(text, 15, false));
        g.setColor(TEXT);
        UiKit.drawCentered(g, text, x + w / 2, y + 19);
    }

    private void paintChoice(Graphics2D g, Rectangle box, String label, boolean selected) {
        UiKit.paintButton(g, selected, box.x, box.y, box.width, box.height, colorBlind, UiKit.pulse());
        if (selected) UiKit.paintArrow(g, box.x - 14, box.y + box.height / 2, 10, colorBlind, UiKit.pulse());
        g.setFont(GameTheme.text(15, true));
        g.setColor(selected ? TEXT : DIM);
        UiKit.drawCentered(g, label, box.x + box.width / 2, box.y + box.height / 2 + 5);
    }

    private void veilBoards(Graphics2D g) {
        g.setColor(new Color(4, 6, 16, 150));
        int top = Math.max(0, scene.boardY - 8);
        g.fillRect(0, top, VW, VH - top);
    }

    private void paintGuide(Graphics2D g) {
        if (guide.isEmpty() || snapshot.phase() == RaidPhase.FINISHED) return;
        Rectangle box = scene.guide;
        String[] lines = guide.split("\\R");
        UiKit.paintCapsule(g, box.x, box.y, box.width, box.height);
        int size = lines.length > 2 ? 11 : 12;
        g.setColor(TEXT);
        int y = box.y + 16;
        for (String line : lines) {
            String drawn = line.replace('\u00B7', '-');
            g.setFont(GameTheme.fontFor(drawn, size, false));
            while (drawn.length() > 4 && g.getFontMetrics().stringWidth(drawn) > box.width - 24) {
                drawn = drawn.substring(0, drawn.length() - 1);
            }
            UiKit.drawCentered(g, drawn, VW / 2, y);
            y += g.getFontMetrics().getHeight();
        }
    }

    private void paintNotice(Graphics2D g) {
        if (connectionNotice.isEmpty()) return;
        g.setFont(GameTheme.text(12, true));
        g.setColor(A11yColors.warning(colorBlind));
        UiKit.drawCentered(g, connectionNotice, VW / 2, scene.name.y + scene.name.height + 12);
    }

    private void paintLucky(Graphics2D g, int[][] lucky, Rectangle play) {
        if (lucky == null) return;
        boolean marked = false;
        for (int r = 0; r < lucky.length && r < VersusRules.BOARD_ROWS; r++) {
            for (int c = 0; c < lucky[r].length && c < VersusRules.BOARD_COLS; c++) {
                if (lucky[r][c] == 0) continue;
                int x = play.x + c * scene.cell;
                int y = play.y + r * scene.cell;
                A11yColors.paintLuckyMark(g, x, y, scene.cell, colorBlind);
                if (!marked) {
                    mark(cueLucky, x, y, scene.cell, scene.cell);
                    marked = true;
                }
            }
        }
    }

    private void paintCloud(Graphics2D g, RaidSnapshot.PlayerView player, Rectangle play) {
        for (RaidSnapshot.Effect effect : player.effects()) {
            if (effect.pattern() != PatternId.CLOUD) continue;
            int[] area = effect.area();
            if (area.length < 4) continue;
            int x = play.x + area[1] * scene.cell;
            int y = play.y + area[0] * scene.cell;
            int w = area[3] * scene.cell;
            int h = area[2] * scene.cell;
            Graphics2D layer = (Graphics2D) g.create();
            try {
                layer.clipRect(play.x, play.y, play.width, play.height);
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

    private void paintLock(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(12, 14, 32, 220));
        g.fillRoundRect(x, y, Math.max(1, w), Math.max(1, h), 8, 8);
        g.setColor(colorBlind ? A11yColors.YELLOW : new Color(0xFFC94D));
        int cx = x + w / 2;
        int cy = y + h / 2;
        g.drawArc(cx - 7, cy - 10, 14, 12, 0, 180);
        g.fillRoundRect(cx - 8, cy - 2, 16, 12, 3, 3);
    }

    private void paintBossFallback(Graphics2D g, BossId id, int x, int y, int size) {
        Color body = switch (id) {
            case JELLY_KING -> new Color(0x3DDC8A);
            case STONE_GOLEM -> new Color(0x8D8478);
            case NIGHT_WITCH -> new Color(0x7A4DFF);
            case MECHA_DRAGON -> new Color(0xFF5A3C);
            case GOLDEN_TETRO -> new Color(0xFFC94D);
        };
        g.setColor(body);
        g.fillRoundRect(x + size / 6, y + size / 5, size * 2 / 3, size / 2, 24, 24);
        g.setColor(UiKit.CYAN);
        g.fillPolygon(new int[] {x + size / 2, x + size / 2 - 16, x + size / 2 + 16},
                new int[] {y + 8, y + size / 3, y + size / 3}, 3);
        g.setColor(new Color(0x07091A));
        g.fillOval(x + size / 2 - 18, y + size / 2 - 4, 8, 8);
        g.fillOval(x + size / 2 + 10, y + size / 2 - 4, 8, 8);
    }

    private void paintPatternFallback(Graphics2D g, int x, int y, int size) {
        g.setColor(new Color(20, 16, 40));
        g.fillRoundRect(x, y, size, size, 6, 6);
        g.setColor(UiKit.CYAN);
        g.drawRect(x + 3, y + 3, size - 7, size - 7);
    }

    private boolean isTarget(int index) {
        if (snapshot.mode() == RaidMode.RACE) return false;
        if (snapshot.phase() != RaidPhase.PLAYING && snapshot.phase() != RaidPhase.COUNTDOWN) return false;
        int target = snapshot.boss().target();
        return target < 0 || target == index;
    }

    private String targetText(int target) {
        if (target < 0) return "→ 모두";
        if (snapshot.mode() == RaidMode.SOLO) return "→ 나";
        return target == 0 ? "→ P1" : "→ P2";
    }

    private String playerMark(int index) {
        return index == 0 ? "■ P1" : "● P2";
    }

    private String raceWinner(RaidSnapshot.Result result) {
        if (result.winner() == null) return "무승부";
        return result.winner() == 0 ? "■ P1 승" : "● P2 승";
    }

    private Color indexColor(int index) {
        if (index == 0) return colorBlind ? A11yColors.mine(true) : UiKit.CYAN;
        return colorBlind ? A11yColors.SKY : UiKit.MAGENTA;
    }

    private void noteStolen(List<RaidEvent> events) {
        if (events == null) return;
        for (RaidEvent event : events) {
            if (event.type() == RaidEvent.Type.BOSS_DEFEATED && event.player() >= 0) {
                stolenSlower = 1 - event.player();
                stolenUntil = System.currentTimeMillis() + 3200;
            }
        }
    }

    private void markBoss() {
        Rectangle source = snapshot.mode() == RaidMode.RACE ? scene.raceArt[0] : scene.boss;
        int height = snapshot.mode() == RaidMode.RACE
                ? source.height
                : Math.min(source.height, Math.max(1, scene.boardY - source.y));
        bossBounds.setBounds(originX + (int) (source.x * scale), originY + (int) (source.y * scale),
                Math.max(1, (int) (source.width * scale)), Math.max(1, (int) (height * scale)));
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

    /** 화면에 쓰는 문구. 테스트가 고른 글꼴로 깨지지 않는지 본다 */
    public static java.util.List<String> captions() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (PatternId id : PatternId.values()) {
            lines.add(shown(id.displayName()));
            lines.add(shown(id.description()));
        }
        for (CardId id : CardId.values()) {
            lines.add(shown(id.displayName()));
            lines.add(shown(id.description()));
        }

        lines.add("▼ 대상");
        lines.add("점수");
        lines.add("필살");
        lines.add("협동");
        lines.add("남은 시간");
        lines.add("P1이 카드를 고릅니다");
        lines.add("P2가 카드를 고릅니다");
        lines.add("초 - 좌우 이동, 하드 드롭 확정");
        lines.add("선수를 빼앗김");
        lines.add("부활 3/8");
        lines.add("대기 1");
        lines.add("구름 4");
        lines.add("봉인 3");
        lines.add("반전 2");
        lines.add("과부하 5");
        lines.add("홀드");
        lines.add("다음");
        lines.add("다음 1");
        lines.add("레이드 종료");
        lines.add("레이드를 끝낼까요?");
        lines.add("점수는 저장되지 않습니다");
        lines.add("P1 A D 이동 - R 필살");
        return lines;

    }

    /** 고른 글꼴이 못 그리는 가운뎃점은 하이픈으로 바꾼다 */
    public static String shown(String text) {
        return text == null ? "" : text.replace('\u00B7', '-');
    }

    private void paintMixed(Graphics2D g, String text, int centerX, int baseline, int size) {
        Font digits = GameTheme.digits(size);
        Font hangul = GameTheme.text(size, false);
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int j = runEnd(text, i);
            g.setFont(fontForRun(text, i, j, digits, hangul));
            width += g.getFontMetrics().stringWidth(text.substring(i, j));
            i = j;
        }
        int x = centerX - width / 2;
        g.setColor(TEXT);
        for (int i = 0; i < text.length(); ) {
            int j = runEnd(text, i);
            String part = text.substring(i, j);
            g.setFont(fontForRun(text, i, j, digits, hangul));
            g.drawString(part, x, baseline);
            x += g.getFontMetrics().stringWidth(part);
            i = j;
        }
    }

    private static int runEnd(String text, int start) {
        boolean numeric = digitish(text.charAt(start));
        int j = start + 1;
        while (j < text.length() && digitish(text.charAt(j)) == numeric) j++;
        return j;
    }

    private static Font fontForRun(String text, int start, int end, Font digits, Font hangul) {
        String part = text.substring(start, end);
        if (digitish(text.charAt(start)) && digits.canDisplayUpTo(part) == -1) return digits;
        return hangul;
    }

    private static boolean digitish(char c) {
        return Character.isDigit(c) || c == ':' || c == '.' || c == '/' || c == '%';
    }

    private static int fit(Graphics2D g, String text, int maxWidth, int preferred, int min, boolean digits) {
        int size = preferred;
        g.setFont(digits ? GameTheme.digits(size) : GameTheme.fontFor(text, size, false));
        while (size > min && g.getFontMetrics().stringWidth(text) > maxWidth) {
            size--;
            g.setFont(digits ? GameTheme.digits(size) : GameTheme.fontFor(text, size, false));
        }
        return size;
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int width) {
        if (text == null || text.isEmpty()) return;
        FontMetrics metrics = g.getFontMetrics();
        String rest = text;
        for (int line = 0; line < 2 && !rest.isEmpty(); line++) {
            int count = rest.length();
            while (count > 1 && metrics.stringWidth(rest.substring(0, count)) > width) count--;
            UiKit.drawCentered(g, rest.substring(0, count), x + width / 2, y + line * 13);
            rest = rest.substring(count).stripLeading();
        }
    }

    private void mark(Rectangle box, int vx, int vy, int vw, int vh) {
        box.setBounds(originX + (int) (vx * scale), originY + (int) (vy * scale),
                Math.max(1, (int) (vw * scale)), Math.max(1, (int) (vh * scale)));
    }

    /** 한 프레임의 배치. 사각형은 다시 써서 그리기마다 새로 만들지 않는다 */
    private static final class Scene {
        int players;
        int cell;
        int boardY;
        final Rectangle boss = new Rectangle();
        final Rectangle hp = new Rectangle();
        final Rectangle gauge = new Rectangle();
        final Rectangle banner = new Rectangle();
        final Rectangle kill = new Rectangle();
        final Rectangle timer = new Rectangle();
        final Rectangle name = new Rectangle();
        final Rectangle guide = new Rectangle();
        final Rectangle level = new Rectangle();
        final Rectangle[] play = {new Rectangle(), new Rectangle()};
        final Rectangle[] frame = {new Rectangle(), new Rectangle()};
        final Rectangle[] hold = {new Rectangle(), new Rectangle()};
        final Rectangle[] next = {new Rectangle(), new Rectangle()};
        final Rectangle[] score = {new Rectangle(), new Rectangle()};
        final Rectangle[] effects = {new Rectangle(), new Rectangle()};
        final Rectangle[] header = {new Rectangle(), new Rectangle()};
        final Rectangle[] raceArt = {new Rectangle(), new Rectangle()};
    }
}
