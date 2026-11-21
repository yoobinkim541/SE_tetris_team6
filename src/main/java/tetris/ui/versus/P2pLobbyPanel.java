package tetris.ui.versus;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import tetris.feature.network.NetworkDefaults;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.relay.InternetRelay;
import tetris.feature.versus.api.BattleMode;
import tetris.relay.RelayRoomMode;
import tetris.ui.fx.AssetStore;
import tetris.ui.fx.StarfieldBackground;
import tetris.ui.theme.GameTheme;
import tetris.ui.theme.UiKit;
import tetris.ui.versus.LobbyChrome.Hit;
import tetris.ui.versus.LobbyChrome.Page;
import tetris.ui.versus.LobbyChrome.RoomRow;
import tetris.ui.versus.LobbyChrome.Scene;
import tetris.ui.versus.LobbyChrome.Space;
import tetris.ui.versus.LobbyChrome.View;

/**
 * P2P 접속·로비 화면. 크리스털 밤하늘에 방 목록·LAN·대기 카드를 그리고, 키와 마우스는 같은 선택 모델을 쓴다.
 * 기존 테스트가 누르는 버튼·입력칸은 화면에 보이지 않게 남겨 둔다. EDT 전용.
 */
public final class P2pLobbyPanel extends JPanel {
    /** 화면이 부르는 동작. P2pLobbyController가 구현한다 */
    public interface Actions {
        void host(String port);
        void connect(String address, String port);
        void selectMode(BattleMode mode);
        void selectRaid(tetris.feature.raid.api.RaidMode mode);
        void toggleReady();
        void cancelOrBack();
        void hostInternet();
        /** 인터넷 대전: 상대가 알려 준 방 코드로 참가한다 */
        void joinInternet(String code);
        /** 대기 방 목록을 다시 받는다 */
        void refreshRooms();
        /** 목록의 공개 방에 바로 참가한다 */
        void joinPublic(int routeIndex, String id);
    }

    private final Actions actions;
    private final StarfieldBackground stars;
    private final GlassField portField = new GlassField(String.valueOf(NetworkDefaults.DEFAULT_PORT), 6, null);
    private final GlassField addressField = new GlassField("", 11, this);
    private final LobbyChoice recentBox = new LobbyChoice(this::pickRecent);
    private final LobbyPress hostButton;
    private final LobbyPress connectButton;
    private final GlassField codeField = new GlassField("", 8, null);
    private final LobbyPress joinInternetButton;
    private final LobbyPress hostInternetButton;
    private final LobbyChoice modeBox = new LobbyChoice(this::pickMode);
    private final LobbyPress readyButton;
    private final LobbyPress backButton;
    private final GlassField editor = new GlassField("", 12, null);
    private final Timer refreshTimer = new Timer(2_000, e -> refreshIfShown());
    private final List<InternetRelay.ListedRoom> rooms = new ArrayList<>();
    private Page page = Page.INTERNET;
    private String selectedId = "tab-net";
    private String editing;
    private String createName = "대전";
    private boolean createPublic = true;
    private int createModeIndex;
    private String codePromptFor;
    private String status = "";
    private String ready = "";
    private String peer = "";
    private String internetRoom;
    private boolean error;
    private boolean rendering;
    private State renderedState = State.INITIAL;

    public P2pLobbyPanel(Actions actions) {
        this.actions = Objects.requireNonNull(actions, "actions");
        hostButton = new LobbyPress("호스트로 대기", () -> this.actions.host(portField.getText()));
        connectButton = new LobbyPress("접속", () -> this.actions.connect(addressField.getText(), portField.getText()));
        joinInternetButton = new LobbyPress("참가", () -> this.actions.joinInternet(codeField.getText()));
        hostInternetButton = new LobbyPress("방 만들기", () -> this.actions.hostInternet());
        readyButton = new LobbyPress("게임 시작", () -> this.actions.toggleReady());
        backButton = new LobbyPress("뒤로", () -> this.actions.cancelOrBack());
        setLayout(null);
        setFont(GameTheme.text(16, false));
        setFocusable(true);
        setBackground(UiKit.NIGHT);
        stars = new StarfieldBackground(this, AssetStore.BG_MENU);
        editor.setOpaque(false);
        editor.setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 12));
        editor.setForeground(Color.WHITE);
        editor.setCaretColor(UiKit.CYAN);
        editor.setHorizontalAlignment(SwingConstants.CENTER);
        editor.setVisible(false);
        editor.setFocusable(false);
        add(editor);
        wireHidden();
        wireInput();
        refreshTimer.setRepeats(true);
        render(new LobbySnapshot(Role.NONE, State.INITIAL, null, 0, false, false, null), "");
    }

    private void wireHidden() {
        modeBox.addItem(BattleMode.CLASSIC);
        modeBox.addItem(BattleMode.ITEM);
        modeBox.addItem(BattleMode.TIMED);
        modeBox.addItem(tetris.feature.raid.api.RaidMode.COOP);
        modeBox.addItem(tetris.feature.raid.api.RaidMode.RACE);
        modeBox.adjust(() -> modeBox.setSelectedIndex(0));
    }

    private void pickRecent(Object value) {
        if (rendering) return;
        if (value instanceof String address && !address.isEmpty()) {
            addressField.setText(address);
            repaint();
        }
    }

    private void pickMode(Object value) {
        if (rendering) return;
        if (value instanceof BattleMode selected) actions.selectMode(selected);
        else if (value instanceof tetris.feature.raid.api.RaidMode raid) actions.selectRaid(raid);
    }

    private void wireInput() {
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hover(e); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { press(e); }
        });
        bind(KeyEvent.VK_UP, () -> moveSelection(0, -1));
        bind(KeyEvent.VK_DOWN, () -> moveSelection(0, 1));
        bind(KeyEvent.VK_LEFT, () -> moveSelection(-1, 0));
        bind(KeyEvent.VK_RIGHT, () -> moveSelection(1, 0));
        bind(KeyEvent.VK_ENTER, this::activateSelected);
        bind(KeyEvent.VK_ESCAPE, this::escape);
        bind(KeyEvent.VK_N, this::newRoom);
        bind(KeyEvent.VK_R, () -> { if (page == Page.INTERNET) actions.refreshRooms(); });
        editor.addActionListener(e -> commitEditor(true));
        editor.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    commitEditor(false);
                    e.consume();
                }
            }
        });
    }

    private void bind(int key, Runnable command) {
        String name = "lobby." + key;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override public boolean isEnabled() { return editing == null; }
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { command.run(); }
        });
    }

    /** 목록을 그린다. 컨트롤러가 두 서버를 합친 결과를 넘긴다 */
    public void setRooms(List<InternetRelay.ListedRoom> next) {
        rooms.clear();
        if (next != null) rooms.addAll(next);
        if (scene().byId(selectedId) == null) selectedId = defaultSelection();
        repaint();
    }

    public String createName() { return createName; }
    public boolean createPublic() { return createPublic; }
    public RelayRoomMode listedMode() { return RelayRoomMode.values()[Math.floorMod(createModeIndex, RelayRoomMode.values().length)]; }
    public String selectedId() { return selectedId; }

    public void prepareCreate(String name, boolean isPublic, RelayRoomMode mode) {
        createName = name;
        createPublic = isPublic;
        createModeIndex = mode.ordinal();
    }

    public List<InternetRelay.ListedRoom> listedRooms() {
        return List.copyOf(rooms);
    }

    /** 테스트·캡처가 클릭할 화면 좌표. 그리는 사각형과 같다 */
    public Rectangle hitScreenBounds(String id) {
        Hit hit = scene().byId(id);
        return hit == null ? new Rectangle() : space().toScreen(hit.bounds());
    }

    public void setInternetRoom(String code) { internetRoom = code; }

    public void setRecentPeers(List<String> peers) {
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
        repaint();
    }

    public void render(LobbySnapshot lobby, String status) { render(lobby, status, false); }

    public void render(LobbySnapshot lobby, String status, boolean error) {
        Objects.requireNonNull(lobby);
        Objects.requireNonNull(status);
        State before = renderedState;
        renderedState = lobby.state();
        this.error = error;
        this.status = status;
        rendering = true;
        try {
            boolean initial = lobby.state() == State.INITIAL;
            boolean inLobby = lobby.state() == State.LOBBY;
            portField.setEnabled(initial);
            addressField.setEnabled(initial);
            hostButton.setEnabled(initial);
            connectButton.setEnabled(initial);
            codeField.setEnabled(initial);
            joinInternetButton.setEnabled(initial);
            hostInternetButton.setEnabled(initial);
            recentBox.setEnabled(initial && recentBox.getItemCount() > 0);
            if (lobby.config() != null) {
                modeBox.setSelectedItem(lobby.config().raid() ? lobby.config().raidMode() : lobby.config().mode());
            }
            modeBox.setEnabled(inLobby && lobby.role() == Role.HOST);
            readyButton.setEnabled(inLobby && lobby.config() != null);
            readyButton.setText(lobby.localReady() ? "준비 취소" : "게임 시작");
            backButton.setText(switch (lobby.state()) {
                case INITIAL -> "뒤로";
                case HOSTING, CONNECTING -> "취소";
                default -> "나가기";
            });
            peer = lobby.peerIdentity() == null ? ""
                    : (lobby.role() == Role.HOST ? "접속한 상대: " : "호스트: ")
                    + (internetRoom != null ? "인터넷 상대 (방 " + internetRoom + ")" : peerName(lobby.peerIdentity()))
                    + (lobby.role() == Role.HOST ? "  (나: P1 왼쪽)" : "  (나: P2 오른쪽)");
            ready = inLobby || lobby.state() == State.STARTING
                    ? "나: " + (lobby.localReady() ? "준비 완료" : "대기") + "   상대: " + (lobby.peerReady() ? "준비 완료" : "대기")
                    + (lobby.config() == null ? "" : "   모드: " + configLabel(lobby.config()))
                    : "";
        } finally {
            rendering = false;
        }
        if (lobby.state() != State.INITIAL) page = Page.WAITING;
        else if (page == Page.WAITING) page = Page.INTERNET;
        if (scene().byId(selectedId) == null) selectedId = defaultSelection();
        if (before != renderedState) requestFocusInWindow();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            stars.paint(g2, getWidth(), getHeight());
            int unit = Math.max(10, getHeight() / 32);
            UiKit.paintMoon(g2, getWidth() - unit * 5, unit, unit * 4);
            UiKit.paintIsland(g2, 0, unit / 2, getHeight() / 3, unit * 4);
            UiKit.paintIsland(g2, 1, getWidth() - unit * 5, getHeight() * 3 / 5, unit * 4);
            Space space = space();
            g2.translate(space.offsetX(), space.offsetY());
            g2.scale(space.scale(), space.scale());
            LobbyChrome.paint(g2, view(), scene(), space.virtualWidth(), space.virtualHeight());
        } finally {
            g2.dispose();
        }
    }

    private void hover(MouseEvent e) {
        Hit hit = hitAt(e);
        setCursor(hit == null ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (hit != null && !hit.id().equals(selectedId)) {
            selectedId = hit.id();
            repaint();
        }
    }

    /** 클릭은 Enter와 같다. 글자 칸만 포커스를 받고, 나머지는 패널 포커스를 유지한다 */
    private void press(MouseEvent e) {
        Hit hit = hitAt(e);
        if (hit == null) return;
        selectedId = hit.id();
        if (hit.text()) openEditor(hit);
        else {
            closeEditor();
            activate(hit.id());
            requestFocusInWindow();
        }
        e.consume();
        repaint();
    }
    @Override
    protected void processKeyEvent(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_PRESSED && editing == null && !e.isConsumed()) {
            onKey(e.getKeyCode());
            e.consume();
        }
        super.processKeyEvent(e);
    }

    private void onKey(int key) {
        if (editing != null) return;
        switch (key) {
            case KeyEvent.VK_UP -> moveSelection(0, -1);
            case KeyEvent.VK_DOWN -> moveSelection(0, 1);
            case KeyEvent.VK_LEFT -> moveSelection(-1, 0);
            case KeyEvent.VK_RIGHT -> moveSelection(1, 0);
            case KeyEvent.VK_ENTER -> activateSelected();
            case KeyEvent.VK_ESCAPE -> escape();
            case KeyEvent.VK_N -> newRoom();
            case KeyEvent.VK_R -> { if (page == Page.INTERNET) actions.refreshRooms(); }
            default -> { }
        }
    }

    private void moveSelection(int dx, int dy) {
        Hit hit = scene().byId(selectedId);
        if (hit == null) {
            selectedId = defaultSelection();
            repaint();
            return;
        }
        if (dx < 0 && ("vis".equals(hit.id()) || "mode".equals(hit.id()) || "wait-mode".equals(hit.id()) || "recent".equals(hit.id()))) {
            cycle(-1, hit.id());
            return;
        }
        if (dx > 0 && ("vis".equals(hit.id()) || "mode".equals(hit.id()) || "wait-mode".equals(hit.id()) || "recent".equals(hit.id()))) {
            cycle(1, hit.id());
            return;
        }
        String next = dy < 0 ? hit.up() : dy > 0 ? hit.down() : dx < 0 ? hit.left() : hit.right();
        if (next == null && dx != 0 && (page == Page.INTERNET || page == Page.LAN)) {
            page = dx < 0 ? Page.INTERNET : Page.LAN;
            selectedId = page == Page.LAN ? "tab-lan" : "tab-net";
        } else if (next != null && scene().byId(next) != null) {
            selectedId = next;
        }
        repaint();
    }

    private void cycle(int delta, String id) {
        switch (id) {
            case "vis" -> createPublic = !createPublic;
            case "mode" -> createModeIndex = Math.floorMod(createModeIndex + delta, RelayRoomMode.values().length);
            case "wait-mode" -> modeBox.setSelectedIndex(Math.floorMod(modeBox.getSelectedIndex() + delta, modeBox.getItemCount()));
            case "recent" -> {
                if (recentBox.getItemCount() == 0) return;
                int index = Math.floorMod(recentBox.getSelectedIndex() + delta, recentBox.getItemCount());
                recentBox.setSelectedIndex(index);
            }
            default -> { }
        }
        repaint();
    }

    private void activateSelected() {
        activate(selectedId);
    }

    private void activate(String id) {
        switch (id) {
            case "tab-net" -> page = Page.INTERNET;
            case "tab-lan" -> page = Page.LAN;
            case "btn-create" -> openCreate();
            case "btn-code" -> openCode(null);
            case "btn-refresh" -> actions.refreshRooms();
            case "btn-back", "btn-lan-back", "btn-leave" -> actions.cancelOrBack();
            case "btn-cancel", "btn-cancel-code" -> { page = Page.INTERNET; selectedId = "tab-net"; }
            case "btn-make" -> hostInternetButton.doClick();
            case "btn-host" -> hostButton.doClick();
            case "btn-connect" -> connectButton.doClick();
            case "btn-ready" -> readyButton.doClick();
            case "btn-join-code" -> submitCode();
            case "field-name", "field-code", "field-port", "field-address" -> {
                Hit hit = scene().byId(id);
                if (hit != null) openEditor(hit);
            }
            case "vis" -> createPublic = !createPublic;
            default -> {
                if (id.startsWith("room-")) joinRow(Integer.parseInt(id.substring(5)));
            }
        }
        repaint();
    }

    private void joinRow(int index) {
        if (index < 0 || index >= rooms.size()) return;
        InternetRelay.ListedRoom room = rooms.get(index);
        if (room.privateRoom()) openCode(room.name());
        else actions.joinPublic(room.routeIndex(), room.id());
    }

    private void submitCode() {
        codeField.setText(codePromptFor == null ? editor.getText() : codeField.getText());
        if (editing != null) codeField.setText(editor.getText());
        joinInternetButton.doClick();
    }

    private void openCreate() {
        page = Page.CREATE;
        selectedId = "field-name";
    }

    private void openCode(String roomName) {
        page = Page.CODE;
        codePromptFor = roomName;
        selectedId = "field-code";
    }

    private void newRoom() {
        if (renderedState != State.INITIAL) return;
        page = Page.CREATE;
        selectedId = "field-name";
        Hit hit = scene().byId("field-name");
        if (hit != null) openEditor(hit);
        repaint();
    }

    private void escape() {
        if (editing != null) {
            commitEditor(false);
            return;
        }
        if (page == Page.CREATE || page == Page.CODE) {
            page = Page.INTERNET;
            selectedId = "tab-net";
            repaint();
            return;
        }
        actions.cancelOrBack();
    }

    private void openEditor(Hit hit) {
        editing = hit.id();
        editor.setFont("field-port".equals(hit.id()) || "field-code".equals(hit.id())
                ? GameTheme.digits(Math.max(14, hit.bounds().height / 2))
                : GameTheme.text(Math.max(14, hit.bounds().height / 2), false));
        editor.setText(switch (hit.id()) {
            case "field-name" -> createName;
            case "field-code" -> codeField.getText();
            case "field-port" -> portField.getText();
            case "field-address" -> addressField.getText();
            default -> "";
        });
        Rectangle screen = space().toScreen(hit.bounds());
        editor.setBounds(screen);
        editor.setVisible(true);
        editor.setFocusable(true);
        editor.requestFocusInWindow();
        editor.selectAll();
    }

    private void commitEditor(boolean activateAfter) {
        if (editing == null) return;
        String text = editor.getText() == null ? "" : editor.getText();
        switch (editing) {
            case "field-name" -> createName = text.trim();
            case "field-code" -> codeField.setText(text.trim());
            case "field-port" -> portField.setText(text.trim());
            case "field-address" -> addressField.setText(text.trim());
            default -> { }
        }
        String was = editing;
        closeEditor();
        requestFocusInWindow();
        if (activateAfter && "field-code".equals(was)) submitCode();
        repaint();
    }

    private void closeEditor() {
        editing = null;
        editor.setVisible(false);
        editor.setFocusable(false);
    }

    private void refreshIfShown() {
        if (isShowing() && page == Page.INTERNET && renderedState == State.INITIAL && editing == null) {
            actions.refreshRooms();
        }
    }

    private Hit hitAt(MouseEvent e) {
        Point virtual = space().toVirtual(e.getX(), e.getY());
        return scene().at(virtual.x, virtual.y);
    }

    private Space space() {
        int preferredW = getPreferredSize() == null ? 0 : getPreferredSize().width;
        int preferredH = getPreferredSize() == null ? 0 : getPreferredSize().height;
        int virtualW = preferredW > 0 ? preferredW : Math.max(1, getWidth());
        int virtualH = preferredH > 0 ? preferredH : Math.max(1, getHeight());
        return Space.of(Math.max(1, getWidth()), Math.max(1, getHeight()), virtualW, virtualH);
    }

    private Scene scene() {
        Space space = space();
        return LobbyChrome.layout(view(), space.virtualWidth(), space.virtualHeight());
    }

    private View view() {
        List<RoomRow> rows = new ArrayList<>();
        for (InternetRelay.ListedRoom room : rooms) {
            rows.add(new RoomRow(room.id(), room.name(), RelayRoomMode.fromToken(room.mode()).map(RelayRoomMode::label).orElse(room.mode()),
                    room.privateRoom(), room.players()));
        }
        String recent = recentBox.getSelectedItem() instanceof String text && !text.isEmpty() ? text : "최근 IP";
        String shownMode = page == Page.WAITING ? modeLabelOf(modeBox.getSelectedItem(), listedMode()) : listedMode().label();
        return new View(page, rows, selectedId, createName, createPublic, shownMode,
                codeField.getText(), portField.getText(), addressField.getText(), recent, recentBox.isEnabled(),
                status.isEmpty() ? hintFor(page) : status, error, internetRoom,
                createName, peer, ready, modeBox.isEnabled(), readyButton.getText(), backButton.getText());
    }

    private static String hintFor(Page page) {
        return switch (page) {
            case INTERNET -> "방을 고르거나 새로 만드세요.";
            case CREATE -> "이름을 적고, 공개와 모드를 고른 뒤 만드세요.";
            case CODE -> "호스트가 알려 준 방 코드를 입력하세요.";
            case LAN -> "포트와 상대 IP를 입력하세요.";
            case WAITING -> "준비가 되면 게임 시작을 누르세요.";
        };
    }

    private static String modeLabelOf(Object selected, RelayRoomMode fallback) {
        if (selected instanceof BattleMode mode) return tetris.ui.versus.BattleModes.label(mode);
        if (selected == tetris.feature.raid.api.RaidMode.COOP) return "협동 레이드";
        if (selected == tetris.feature.raid.api.RaidMode.RACE) return "레이스 레이드";
        return fallback.label();
    }

    private String defaultSelection() {
        return switch (page) {
            case LAN -> "field-port";
            case CREATE -> "field-name";
            case CODE -> "field-code";
            case WAITING -> "btn-ready";
            case INTERNET -> rooms.isEmpty() ? "btn-create" : "room-0";
        };
    }

    private static String peerName(String identity) {
        int hash = identity.lastIndexOf('#');
        return hash < 0 ? identity : identity.substring(0, hash);
    }

    private static String configLabel(tetris.feature.versus.api.MatchConfig config) {
        return config.raid() ? modeLabelOf(config.raidMode(), RelayRoomMode.CLASSIC) : BattleModes.label(config.mode());
    }

    int wrapWidth() {
        int width = getWidth();
        if (width <= 0) return 250;
        return Math.max(120, Math.min(360, (int) ((width - 24) / 1.3)));
    }

    public JComponent focusTarget() {
        return switch (renderedState) {
            case INITIAL -> addressField;
            case LOBBY -> readyButton;
            default -> backButton;
        };
    }

    public boolean isError() { return error; }
    public LobbyChoice recentBox() { return recentBox; }
    public String statusText() { return status; }
    public String readyText() { return ready; }
    public String peerText() { return peer; }
    public JTextField portField() { return portField; }
    public JTextField addressField() { return addressField; }
    public LobbyPress hostButton() { return hostButton; }
    public LobbyPress connectButton() { return connectButton; }
    public JTextField codeField() { return codeField; }
    public LobbyPress joinInternetButton() { return joinInternetButton; }
    public LobbyPress hostInternetButton() { return hostInternetButton; }
    public LobbyChoice modeBox() { return modeBox; }
    public LobbyPress readyButton() { return readyButton; }
    public LobbyPress backButton() { return backButton; }

    private <T extends JComponent> T focusForward(T component) {
        component.setFocusable(false);
        return component;
    }


    @Override
    public void addNotify() {
        super.addNotify();
        if (!refreshTimer.isRunning()) refreshTimer.start();
    }

    @Override
    public void removeNotify() {
        refreshTimer.stop();
        super.removeNotify();
    }
}
