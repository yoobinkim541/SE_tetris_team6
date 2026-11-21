package tetris.ui.versus;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerId;
import tetris.ui.theme.GameTheme;
import tetris.ui.theme.UiKit;

/** 대전 결과. 점수는 표시만 하고 저장하지 않는다. */
public final class BattleResultPanel extends JPanel {
    private String summaryText = "대전 결과";
    private String scoresText = "";
    private String hintText = "";
    private String title = "대전 결과";
    private String reason = "";
    private int winner = -1;
    private long scoreOne;
    private long scoreTwo;
    private String nameOne = "P1";
    private String nameTwo = "P2";
    private int selected;
    private final List<Choice> choices = new ArrayList<>();
    private final List<Rectangle> buttons = new ArrayList<>();

    public String getHintText() {
        return hintText;
    }

    public BattleResultPanel(Runnable onModes, Runnable onMain) {
        this("모드 선택", onModes, onMain);
    }

    public BattleResultPanel(String primaryLabel, Runnable onModes, Runnable onMain) {
        this(primaryLabel, onModes, null, null, onMain);
    }

    public BattleResultPanel(String primaryLabel, Runnable onModes, String extraLabel, Runnable onExtra, Runnable onMain) {
        Objects.requireNonNull(primaryLabel);
        Objects.requireNonNull(onModes);
        Objects.requireNonNull(onMain);
        if ((extraLabel == null) != (onExtra == null)) {
            throw new IllegalArgumentException("extra label and action go together");
        }
        setOpaque(false);
        setFocusable(true);
        setPreferredSize(new Dimension(960, 720));
        if (extraLabel != null) choices.add(new Choice(extraLabel, onExtra));
        choices.add(new Choice(primaryLabel, onModes));
        choices.add(new Choice("메인 메뉴", onMain));
        for (int i = 0; i < choices.size(); i++) buttons.add(new Rectangle());
        if (extraLabel != null) {
            hintText = "Enter: " + extraLabel + "   L: " + primaryLabel + "   Esc: 메인 메뉴";
            bind(KeyEvent.VK_ENTER, "extra", onExtra);
            bind(KeyEvent.VK_L, "modes", onModes);
        } else {
            hintText = "Enter: " + primaryLabel + "   Esc: 메인 메뉴";
            bind(KeyEvent.VK_ENTER, "modes", onModes);
        }
        bind(KeyEvent.VK_ESCAPE, "main", onMain);
        bind(KeyEvent.VK_LEFT, "left", () -> move(-1));
        bind(KeyEvent.VK_RIGHT, "right", () -> move(1));
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hover(e.getX(), e.getY()); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int index = hit(e.getX(), e.getY());
                if (index < 0) return;
                selected = index;
                choices.get(index).action().run();
            }
        });
    }

    /** 테스트·마우스가 버튼 글자로 누른다. 키보드 포커스는 유지한다 */
    public void press(String label) {
        requestFocusInWindow();
        for (Choice choice : choices) {
            if (choice.label().equals(label)) {
                choice.action().run();
                return;
            }
        }
    }

    public void setSnapshot(MatchSnapshot snapshot) {
        setSnapshot(snapshot, "");
    }

    public void setSnapshot(MatchSnapshot snapshot, String modeLabel) {
        setSnapshot(snapshot, modeLabel, null);
    }

    public void setSnapshot(MatchSnapshot snapshot, String modeLabel, PlayerId viewer) {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(modeLabel);
        if (snapshot.phase() != MatchPhase.FINISHED || snapshot.result() == null) {
            throw new IllegalArgumentException("result screen requires a finished match");
        }
        var result = snapshot.result();
        String outcome = BattlePanel.outcomeText(result, viewer);
        reason = BattlePanel.reasonText(result.reason());
        title = outcome;
        summaryText = (modeLabel.isEmpty() ? "" : modeLabel + " · ") + outcome + " · " + reason;
        nameOne = BattlePanel.boardTitle(PlayerId.ONE, viewer);
        nameTwo = BattlePanel.boardTitle(PlayerId.TWO, viewer);
        scoreOne = snapshot.one().score();
        scoreTwo = snapshot.two().score();
        scoresText = nameOne + ": " + scoreOne + "   " + nameTwo + ": " + scoreTwo;
        winner = result.winner() == null ? -1 : result.winner() == PlayerId.ONE ? 0 : 1;
        getAccessibleContext().setAccessibleDescription(getSummaryText() + ". " + getScoresText());
        repaint();
    }

    public String getSummaryText() {
        return summaryText;
    }

    public String getScoresText() {
        return scoresText;
    }

    private void move(int delta) {
        selected = Math.floorMod(selected + delta, choices.size());
        repaint();
    }

    private int hit(int x, int y) {
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).contains(x, y)) return i;
        }
        return -1;
    }

    private void hover(int x, int y) {
        int index = hit(x, y);
        setCursor(index >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        if (index >= 0 && index != selected) {
            selected = index;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        GameTheme.applyQuality(g);
        UiKit.paintScene(g, getWidth(), getHeight());
        int w = Math.min(640, getWidth() - 48);
        int h = 360;
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;
        UiKit.paintFrame(g, UiKit.Tone.VIOLET, x, y, w, h);
        g.setFont(GameTheme.display(32));
        g.setColor(Color.WHITE);
        GameTheme.drawCentered(g, title, getWidth() / 2, y + 64);
        if (!reason.isEmpty()) {
            UiKit.paintCapsule(g, getWidth() / 2 - 70, y + 80, 140, 28);
            g.setFont(GameTheme.text(14, false));
            UiKit.drawInBox(g, reason, new Rectangle(getWidth() / 2 - 70, y + 80, 140, 28), UiKit.Align.CENTER);
        }
        paintCard(g, x + 36, y + 128, (w - 88) / 2, 96, nameOne, scoreOne, winner == 0, true);
        paintCard(g, x + 52 + (w - 88) / 2, y + 128, (w - 88) / 2, 96, nameTwo, scoreTwo, winner == 1, false);
        int buttonW = 148;
        int gap = 16;
        int row = choices.size() * buttonW + (choices.size() - 1) * gap;
        int bx = (getWidth() - row) / 2;
        for (int i = 0; i < choices.size(); i++) {
            Rectangle box = buttons.get(i);
            box.setBounds(bx, y + 248, buttonW, 40);
            UiKit.paintButton(g, i == selected, box.x, box.y, box.width, box.height, false, UiKit.pulse());
            g.setFont(GameTheme.display(16));
            g.setColor(Color.WHITE);
            UiKit.drawInBox(g, choices.get(i).label(), box, UiKit.Align.CENTER);
            bx += buttonW + gap;
        }
        UiKit.paintCapsule(g, x + 24, y + h - 48, w - 48, 28);
        g.setFont(GameTheme.text(13, false));
        UiKit.drawInBox(g, hintText, new Rectangle(x + 24, y + h - 48, w - 48, 28), UiKit.Align.CENTER);
    }

    private void paintCard(Graphics2D g, int x, int y, int w, int h, String name, long score, boolean win, boolean one) {
        UiKit.paintFrame(g, win ? UiKit.Tone.CYAN : UiKit.Tone.VIOLET, x, y, w, h);
        g.setFont(GameTheme.text(14, false));
        g.setColor(one ? UiKit.CYAN : UiKit.MAGENTA);
        g.drawString((one ? "◆ " : "★ ") + name, x + 16, y + 28);
        g.setFont(GameTheme.digits(28));
        g.setColor(Color.WHITE);
        GameTheme.drawCentered(g, String.valueOf(score), x + w / 2, y + 68);
    }

    private void bind(int keyCode, String name, Runnable callback) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new AbstractAction(name) {
            @Override public void actionPerformed(ActionEvent event) {
                callback.run();
            }
        });
    }

    private record Choice(String label, Runnable action) {
    }
}
