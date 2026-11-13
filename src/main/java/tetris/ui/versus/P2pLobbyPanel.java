package tetris.ui.versus;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import tetris.feature.network.NetworkDefaults;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.versus.api.BattleMode;

/**
 * P2P 접속·로비 화면 (S9-B2, R3-MENU, R3-HOST, R3-READY). 상태를 그리기만 하고 동작은 {@link Actions}로 넘긴다.
 * 호스트 대기/접속, IP·포트 입력, 접속 상태, 호스트만 바꿀 수 있는 모드, 양쪽 "게임 시작"(준비), 취소·뒤로를 보여 준다. EDT 전용.
 */
public final class P2pLobbyPanel extends JPanel {
    /** 화면 버튼이 부르는 동작. P2pLobbyController가 구현한다 */
    public interface Actions {
        void host(String port);
        void connect(String address, String port);
        void selectMode(BattleMode mode);
        void toggleReady();
        void cancelOrBack();
    }

    private static final Color BACKGROUND = new Color(0x1B1E24);
    private final JTextField portField = new JTextField(String.valueOf(NetworkDefaults.DEFAULT_PORT), 6);
    private final JTextField addressField = new JTextField("", 14);
    private final JComboBox<String> recentBox = new JComboBox<>();
    private final JButton hostButton = new JButton("호스트로 대기");
    private final JButton connectButton = new JButton("접속");
    private final JComboBox<BattleMode> modeBox = new JComboBox<>(BattleMode.values());
    private final JLabel statusLabel = label("", 17, true);
    private final JLabel peerLabel = label("", 15, false);
    private final JLabel readyLabel = label("", 15, false);
    private String status = "";
    private String ready = "";
    private final JButton readyButton = new JButton("게임 시작");
    private final JButton backButton = new JButton("뒤로");
    private boolean rendering;
    private static final Color ERROR = new Color(0xFF8A80);
    private State renderedState;
    private boolean error;

    public P2pLobbyPanel(Actions actions) {
        Objects.requireNonNull(actions, "actions");
        setBackground(BACKGROUND);
        setFocusable(true);
        setLayout(new GridBagLayout());
        modeBox.setRenderer((list, value, index, selected, focus) -> {
            var cell = new JLabel(value == null ? "" : BattleModes.label(value));
            cell.setOpaque(true);
            cell.setBackground(selected ? new Color(0x3A4250) : Color.WHITE);
            cell.setForeground(selected ? Color.WHITE : Color.BLACK);
            return cell;
        });
        var buttons = row();
        buttons.add(readyButton);
        buttons.add(backButton);
        var hint = label(wrap("게임 시작: 양쪽이 모두 눌러야 시작합니다. 나가기·취소는 연결을 끊습니다."), 13, false);

        // 좁은 창에서도 잘리지 않도록 "이름 | 입력 | 버튼" 격자로 둔다. 긴 안내는 줄바꿈한다
        int y = 0;
        addWide(label("P2P 대전 (같은 LAN)", 26, true), y++);
        addRow(label("포트", 16, false), portField, hostButton, y++);
        addRow(label("상대 IP", 16, false), addressField, connectButton, y++);
        addRow(label("최근 IP", 16, false), recentBox, label("(고르면 입력됨)", 13, false), y++);
        addWide(statusLabel, y++);
        addWide(peerLabel, y++);
        addRow(label("모드", 16, false), modeBox, label("(호스트만 선택)", 13, false), y++);
        addWide(readyLabel, y++);
        addWide(buttons, y++);
        addWide(hint, y);

        hostButton.addActionListener(e -> actions.host(portField.getText()));
        connectButton.addActionListener(e -> actions.connect(addressField.getText(), portField.getText()));
        recentBox.addActionListener(e -> {
            if (!rendering && recentBox.getSelectedItem() instanceof String address && !address.isEmpty()) {
                addressField.setText(address);
            }
        });
        modeBox.addActionListener(e -> {
            if (!rendering && modeBox.getSelectedItem() instanceof BattleMode selected) actions.selectMode(selected);
        });
        readyButton.addActionListener(e -> actions.toggleReady());
        backButton.addActionListener(e -> actions.cancelOrBack());
        render(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null), "");
    }

    /** 최근 접속 IP 목록 (최신순). 비어 있으면 고를 수 없다 (R3-RECENT) */
    public void setRecentPeers(java.util.List<String> peers) {
        Objects.requireNonNull(peers);
        rendering = true;
        try {
            recentBox.removeAllItems();
            for (String peer : peers) recentBox.addItem(peer);
            recentBox.setSelectedIndex(-1);
        } finally {
            rendering = false;
        }
        recentBox.setEnabled(!peers.isEmpty() && addressField.isEnabled());
        recentBox.setToolTipText(peers.isEmpty() ? "접속에 성공한 IP가 여기에 남습니다" : null);
    }

    private void addRow(JComponent name, JComponent input, JComponent action, int y) {
        JComponent[] parts = {name, input, action};
        for (int x = 0; x < parts.length; x++) {
            var c = new GridBagConstraints();
            c.gridx = x;
            c.gridy = y;
            c.insets = new Insets(4, 4, 4, 4);
            c.fill = x == 1 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            c.anchor = x == 0 ? GridBagConstraints.LINE_END : GridBagConstraints.LINE_START;
            add(parts[x], c);
        }
    }

    private void addWide(JComponent part, int y) {
        var c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 3;
        c.insets = new Insets(6, 4, 6, 4);
        add(part, c);
    }

    /** 긴 문구를 정해진 폭 안에서 줄바꿈하는 HTML. 화면 글자는 그대로다 */
    private static String wrap(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><div style='text-align:center;width:250px'>" + escaped + "</div></html>";
    }

    private static JPanel row() {
        var panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, int size, boolean bold) {
        var label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.DIALOG, bold ? Font.BOLD : Font.PLAIN, size));
        label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        return label;
    }

    /** 로비 상태와 안내 문구로 화면을 다시 그린다 */
    public void render(LobbySnapshot lobby, String status) {
        render(lobby, status, false);
    }

    /** error면 안내를 경고색으로 보인다. 상태가 바뀌면 그 상태에서 누를 곳에 포커스를 둔다 (S10-B2) */
    public void render(LobbySnapshot lobby, String status, boolean error) {
        Objects.requireNonNull(lobby);
        State before = renderedState;
        renderedState = lobby.state();
        this.error = error;
        statusLabel.setForeground(error ? ERROR : Color.WHITE);
        Objects.requireNonNull(status);
        rendering = true;
        try {
            boolean initial = lobby.state() == State.INITIAL;
            boolean inLobby = lobby.state() == State.LOBBY;
            portField.setEnabled(initial);
            addressField.setEnabled(initial);
            hostButton.setEnabled(initial);
            connectButton.setEnabled(initial);
            recentBox.setEnabled(initial && recentBox.getItemCount() > 0);
            if (lobby.config() != null) modeBox.setSelectedItem(lobby.config().mode());
            modeBox.setEnabled(inLobby && lobby.role() == Role.HOST);
            readyButton.setEnabled(inLobby && lobby.config() != null);
            readyButton.setText(lobby.localReady() ? "준비 취소" : "게임 시작");
            backButton.setText(switch (lobby.state()) {
                case INITIAL -> "뒤로";
                case HOSTING, CONNECTING -> "취소";
                default -> "나가기";
            });
            this.status = status;
            statusLabel.setText(wrap(status));
            peerLabel.setText(lobby.peerIdentity() == null ? ""
                    : (lobby.role() == Role.HOST ? "접속한 상대: " : "호스트: ") + peerName(lobby.peerIdentity())
                    + (lobby.role() == Role.HOST ? "  (나: P1 왼쪽)" : "  (나: P2 오른쪽)"));
            ready = inLobby || lobby.state() == State.STARTING
                    ? "나: " + (lobby.localReady() ? "준비 완료" : "대기") + "   상대: " + (lobby.peerReady() ? "준비 완료" : "대기")
                    + (lobby.config() == null ? "" : "   모드: " + BattleModes.label(lobby.config().mode()))
                    : "";
            readyLabel.setText(ready);
        } finally {
            rendering = false;
        }
        if (before != renderedState) focusTarget().requestFocusInWindow();
        repaint();
    }

    /** 지금 상태에서 키보드로 먼저 누를 곳: 첫 화면은 IP 입력, 로비는 게임 시작, 대기·접속 중은 취소 */
    public JComponent focusTarget() {
        return switch (renderedState) {
            case INITIAL -> addressField;
            case LOBBY -> readyButton;
            default -> backButton;
        };
    }

    public boolean isError() { return error; }
    public JComboBox<String> recentBox() { return recentBox; }

    /** "주소#연결순번"에서 사람이 읽을 주소만 */
    static String peerName(String identity) {
        int hash = identity.lastIndexOf('#');
        return hash < 0 ? identity : identity.substring(0, hash);
    }

    public String statusText() { return status; }
    public String readyText() { return ready; }
    public String peerText() { return peerLabel.getText(); }
    public JTextField portField() { return portField; }
    public JTextField addressField() { return addressField; }
    public JButton hostButton() { return hostButton; }
    public JButton connectButton() { return connectButton; }
    public JComboBox<BattleMode> modeBox() { return modeBox; }
    public JButton readyButton() { return readyButton; }
    public JButton backButton() { return backButton; }
}
