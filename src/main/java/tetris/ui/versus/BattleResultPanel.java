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
    private final JLabel scores = new JLabel("", SwingConstants.CENTER);

    public BattleResultPanel(Runnable onModes, Runnable onMain) {
        Objects.requireNonNull(onModes); Objects.requireNonNull(onMain);
        setLayout(new GridBagLayout());
        setBackground(new Color(0x1B1E24));
        setPreferredSize(new Dimension(960, 720));
        summary.setFont(new Font(Font.DIALOG, Font.BOLD, 30)); summary.setForeground(Color.WHITE);
        scores.setFont(new Font(Font.DIALOG, Font.PLAIN, 24)); scores.setForeground(Color.WHITE);
        var content = new JPanel(new GridLayout(0, 1, 0, 24)); content.setOpaque(false);
        content.add(summary); content.add(scores);
        var actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 0)); actions.setOpaque(false);
        actions.add(new JButton(navigation("모드 선택", onModes)));
        actions.add(new JButton(navigation("메인 메뉴", onMain)));
        content.add(actions);
        var hint = new JLabel("Enter: 모드 선택   Esc: 메인 메뉴", SwingConstants.CENTER);
        hint.setForeground(Color.WHITE); content.add(hint); add(content);
        bind(KeyEvent.VK_ENTER, "modes", onModes);
        bind(KeyEvent.VK_ESCAPE, "main", onMain);
    }

    public void setSnapshot(MatchSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (snapshot.phase() != MatchPhase.FINISHED || snapshot.result() == null)
            throw new IllegalArgumentException("result screen requires a finished match");
        var result = snapshot.result();
        String outcome = result.isDraw() ? "무승부" : result.winner() == PlayerId.ONE ? "P1 승리" : "P2 승리";
        String reason = switch (result.reason()) {
            case TOPPED_OUT -> "블록 쌓임";
            case TIME_UP -> "시간 종료";
            case FORFEIT -> "기권";
        };
        summary.setText(outcome + " · " + reason);
        scores.setText("P1: " + snapshot.one().score() + "   P2: " + snapshot.two().score());
        getAccessibleContext().setAccessibleDescription(getSummaryText() + ". " + getScoresText());
    }
    public String getSummaryText() { return summary.getText(); }
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
