package tetris.ui.versus;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import javax.swing.*;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerId;

/** Display-only versus result. No score persistence or single-player result dependencies. EDT confined. */
public final class BattleResultPanel extends JPanel {
    private final JLabel summary = new JLabel("대전 결과", SwingConstants.CENTER);
    private String summaryText = "대전 결과";
    private final JLabel scores = new JLabel("", SwingConstants.CENTER);

    public BattleResultPanel(Runnable onModes, Runnable onMain) {
        this("모드 선택", onModes, onMain);
    }

    /** primaryLabel: Enter 버튼 이름 (로컬 "모드 선택", P2P "로비로") */
    public BattleResultPanel(String primaryLabel, Runnable onModes, Runnable onMain) {
        Objects.requireNonNull(primaryLabel); Objects.requireNonNull(onModes); Objects.requireNonNull(onMain);
        setLayout(new GridBagLayout());
        setBackground(new Color(0x1B1E24));
        setPreferredSize(new Dimension(960, 720));
        summary.setFont(new Font(Font.DIALOG, Font.BOLD, 30)); summary.setForeground(Color.WHITE);
        scores.setFont(new Font(Font.DIALOG, Font.PLAIN, 24)); scores.setForeground(Color.WHITE);
        var content = new JPanel(new GridLayout(0, 1, 0, 24)); content.setOpaque(false);
        content.add(summary); content.add(scores);
        var actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 0)); actions.setOpaque(false);
        actions.add(new JButton(navigation(primaryLabel, onModes)));
        actions.add(new JButton(navigation("메인 메뉴", onMain)));
        content.add(actions);
        var hint = new JLabel("Enter: " + primaryLabel + "   Esc: 메인 메뉴", SwingConstants.CENTER);
        hint.setForeground(Color.WHITE); content.add(hint); add(content);
        bind(KeyEvent.VK_ENTER, "modes", onModes);
        bind(KeyEvent.VK_ESCAPE, "main", onMain);
    }

    public void setSnapshot(MatchSnapshot snapshot) {
        setSnapshot(snapshot, "");
    }

    /** modeLabel이 있으면 결과 앞에 모드 이름을 붙인다. 대전 결과는 표시만 하고 저장하지 않는다 (R3-NOSCORE) */
    public void setSnapshot(MatchSnapshot snapshot, String modeLabel) {
        setSnapshot(snapshot, modeLabel, null);
    }

    /** viewer가 있으면(P2P) 승패를 이 PC 기준 "승리!/패배"로, 점수에 "(나)"를 붙인다 (S10-B1) */
    public void setSnapshot(MatchSnapshot snapshot, String modeLabel, PlayerId viewer) {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(modeLabel);
        if (snapshot.phase() != MatchPhase.FINISHED || snapshot.result() == null)
            throw new IllegalArgumentException("result screen requires a finished match");
        var result = snapshot.result();
        String outcome = BattlePanel.outcomeText(result, viewer);
        String reason = BattlePanel.reasonText(result.reason());
        summaryText = (modeLabel.isEmpty() ? "" : modeLabel + " · ") + outcome + " · " + reason;
        // 긴 모드 이름(P2P 표시 포함)이 잘리지 않도록 모드는 위 줄에 작게, 승패는 아래 줄에 크게 쓴다
        summary.setText(modeLabel.isEmpty() ? outcome + " · " + reason
                : "<html><div style='text-align:center'><span style='font-size:16pt'>" + escape(modeLabel)
                + "</span><br>" + outcome + " · " + reason + "</div></html>");
        scores.setText(BattlePanel.boardTitle(PlayerId.ONE, viewer) + ": " + snapshot.one().score()
                + "   " + BattlePanel.boardTitle(PlayerId.TWO, viewer) + ": " + snapshot.two().score());
        getAccessibleContext().setAccessibleDescription(getSummaryText() + ". " + getScoresText());
    }
    public String getSummaryText() { return summaryText; }
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    public String getScoresText() { return scores.getText(); }

    private static Action navigation(String name, Runnable callback) {
        return new AbstractAction(name) {
            @Override public void actionPerformed(ActionEvent event) { callback.run(); }
        };
    }
    private void bind(int keyCode, String name, Runnable callback) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, navigation(name, callback));
    }
}
