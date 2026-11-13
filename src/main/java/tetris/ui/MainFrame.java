package tetris.ui;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.rule.WeightedBlockGenerator;
import tetris.feature.game.GameMode;
import tetris.feature.game.Difficulty;
import java.util.Random;
import tetris.feature.save.ScoreboardRepository;
import tetris.feature.save.SettingsRepository;
import tetris.feature.score.ScoreEntry;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.SocketLobbyClient;
import tetris.feature.versus.api.*;
import tetris.ui.versus.*;
import java.util.function.Function;
import javax.swing.JOptionPane;

/**
 * 메인 윈도우. CardLayout으로 화면을 전환한다 (명세 9, 13장). 크기 조절 불가.
 * 화면 흐름(Main Menu → Game → GameOver → Name → Scoreboard)과 저장 호출을 이 클래스가 맡는다.
 * 게임 세션 객체는 하나를 계속 쓰고 새 게임마다 start()로 초기화한다.
 */
public class MainFrame extends JFrame {
    /** 앱 화면 흐름 (GameState와 별개) */
    public enum Screen { MAIN_MENU, GAME, SETTINGS, SCOREBOARD, NAME_INPUT, GAME_OVER, BATTLE_MODES, BATTLE, BATTLE_RESULT, P2P_LOBBY, P2P_RESULT }

    private static final int MIN_CELL_PIXELS = 12;

    private final Settings settings;
    private final Scoreboard scoreboard;
    private final SettingsRepository settingsRepository;
    private final ScoreboardRepository scoreboardRepository;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final Map<Screen, JComponent> screens = new EnumMap<>(Screen.class);
    private final MainMenuPanel mainMenuPanel;
    private final GamePanel gamePanel;
    private final GameOverPanel gameOverPanel;
    private final NameInputPanel nameInputPanel;
    private final ScoreBoardPanel scoreBoardPanel;
    private final SettingPanel settingPanel;
    private final KeyBindingManager keyBindings;
    private GameSession session;

    private Screen currentScreen;
    private ScreenSize appliedScreenSize;
    private GameSnapshot finalResult; // 방금 끝난 게임의 마지막 상태 (Name 입력·스코어보드 등록용)
    private final Function<MatchConfig, BattleClient> battleFactory;
    private BattleScreenController battleController;
    private long battleGeneration;
    private final BattleResultPanel battleResultPanel;
    private final java.util.function.Supplier<LobbyClient> lobbyFactory;
    private final BattleResultPanel onlineResultPanel;
    private P2pLobbyController p2p;
    private boolean battleOnline;

    public MainFrame(Settings settings, Scoreboard scoreboard,
                     SettingsRepository settingsRepository, ScoreboardRepository scoreboardRepository) {
        this(settings, scoreboard, settingsRepository, scoreboardRepository, LocalBattleClient::new);
    }

    public MainFrame(Settings settings, Scoreboard scoreboard,
                     SettingsRepository settingsRepository, ScoreboardRepository scoreboardRepository,
                     Function<MatchConfig, BattleClient> battleFactory) {
        this(settings, scoreboard, settingsRepository, scoreboardRepository, battleFactory,
                MainFrame::defaultLobby);
    }

    /** 최근 접속 IP 기록용 디스크 실행기. EDT를 막지 않도록 데몬 스레드 하나에서 쓴다 (R3-RECENT) */
    private static final java.util.concurrent.Executor RECENT_PEER_IO =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "recent-peers-io");
                thread.setDaemon(true);
                return thread;
            });

    /** 실제 소켓 로비. 접속에 성공한 IP를 %APPDATA% 아래 recent-peers.json에 남긴다 */
    private static LobbyClient defaultLobby() {
        java.util.concurrent.Executor edt = javax.swing.SwingUtilities::invokeLater;
        return new SocketLobbyClient(edt, new tetris.feature.network.PeerEstablisher(edt),
                tetris.feature.versus.LocalMatchEngine::new, MatchClock.system(),
                new tetris.feature.network.match.DispatcherPulse(edt), tetris.feature.network.HeartbeatPolicy.DEFAULT,
                new tetris.feature.save.JsonRecentPeerRepository(tetris.feature.save.JsonRecentPeerRepository.defaultFile()),
                RECENT_PEER_IO);
    }

    /** lobbyFactory: P2P 화면에 들어갈 때마다 새 로비(소켓) 하나를 만든다. 모든 콜백은 EDT에서 실행된다 */
    public MainFrame(Settings settings, Scoreboard scoreboard,
                     SettingsRepository settingsRepository, ScoreboardRepository scoreboardRepository,
                     Function<MatchConfig, BattleClient> battleFactory,
                     java.util.function.Supplier<LobbyClient> lobbyFactory) {
        super(Messages.get("app.title"));
        this.battleFactory = java.util.Objects.requireNonNull(battleFactory);
        this.lobbyFactory = java.util.Objects.requireNonNull(lobbyFactory);
        this.settings = settings;
        this.scoreboard = scoreboard;
        this.settingsRepository = settingsRepository;
        this.scoreboardRepository = scoreboardRepository;

        mainMenuPanel = new MainMenuPanel(List.of(
                new MenuItem(Messages.get("menu.start"), this::startGame),
                new MenuItem("아이템 모드", () -> startGame(GameMode.ITEM)),
                new MenuItem(Messages.get("menu.settings"), this::openSettings),
                new MenuItem(Messages.get("menu.scoreboard"), this::openScoreboard),
                new MenuItem(Messages.get("menu.exit"), this::exitApplication),
                new MenuItem("대전", this::openBattleModes)));
        PausePanel pausePanel = new PausePanel(this::resumeGame, this::confirmRestart, this::requestQuit);
        gamePanel = new GamePanel(settings, pausePanel, this::onGameOver);
        gameOverPanel = new GameOverPanel(this::afterGameOver);
        nameInputPanel = new NameInputPanel(this::submitName);
        scoreBoardPanel = new ScoreBoardPanel(() -> showScreen(Screen.MAIN_MENU), this::exitApplication);
        settingPanel = new SettingPanel(settings, this::onSettingsChanged, this::resetScoreboard,
                () -> showScreen(Screen.MAIN_MENU));

        session = createSession(GameMode.CLASSIC);
        keyBindings = new KeyBindingManager(this::requestQuit);
        keyBindings.bindGameKeys(gamePanel, settings.getKeyMap(), session);

        addScreen(Screen.MAIN_MENU, mainMenuPanel);
        addScreen(Screen.GAME, gamePanel);
        addScreen(Screen.GAME_OVER, gameOverPanel);
        addScreen(Screen.NAME_INPUT, nameInputPanel);
        addScreen(Screen.SCOREBOARD, scoreBoardPanel);
        addScreen(Screen.SETTINGS, settingPanel);
        var modes = new MainMenuPanel(List.of(
                new MenuItem("로컬 " + BattleModes.label(BattleMode.CLASSIC), () -> startBattle(BattleMode.CLASSIC)),
                new MenuItem("로컬 " + BattleModes.label(BattleMode.ITEM), () -> startBattle(BattleMode.ITEM)),
                new MenuItem("로컬 " + BattleModes.label(BattleMode.TIMED), () -> startBattle(BattleMode.TIMED)),
                new MenuItem("P2P 대전 (같은 LAN)", this::openP2pLobby),
                new MenuItem("메인 메뉴", () -> showScreen(Screen.MAIN_MENU))));
        addScreen(Screen.BATTLE_MODES, modes);
        battleResultPanel = new BattleResultPanel(this::openBattleModes, () -> showScreen(Screen.MAIN_MENU));
        addScreen(Screen.BATTLE_RESULT, battleResultPanel);
        onlineResultPanel = new BattleResultPanel("로비로", this::backToP2pLobby, "다시 대전", this::rematchP2p,
                () -> showScreen(Screen.MAIN_MENU));
        addScreen(Screen.P2P_RESULT, onlineResultPanel);
        addScreen(Screen.P2P_LOBBY, new JPanel());
        setContentPane(root);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // EXT-1: 확인 없이 종료, 진행 중 게임은 폐기
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent e) {
                onFocusLost();
            }
            @Override public void windowClosing(WindowEvent e) { closeBattle(); closeP2p(); }
        });
        applyScreenSize(settings.getScreenSize());
    }

    /** 화면 전환. 기존 ReturnToMain 역할 = showScreen(MAIN_MENU) */
    public void showScreen(Screen screen) {
        if (currentScreen == Screen.BATTLE && screen != Screen.BATTLE) closeBattle();
        // P2P 화면 묶음(로비·온라인 경기·온라인 결과)을 떠나면 연결을 닫는다
        if (p2p != null && screen != Screen.P2P_LOBBY && screen != Screen.P2P_RESULT
                && !(screen == Screen.BATTLE && battleOnline)) closeP2p();
        currentScreen = screen;
        cards.show(root, screen.name());
        if (screen == Screen.NAME_INPUT) nameInputPanel.focusField();
        else screens.get(screen).requestFocusInWindow();
    }

    /** 셀 크기 적용: pack() 후 중앙 배치. 화면에 안 맞으면 셀 크기를 자동 축소 (RND-3) */
    public void applyScreenSize(ScreenSize size) {
        appliedScreenSize = size;
        int cell = fitCellSize(size.cellPixels());
        gamePanel.setCellSize(cell);
        Dimension panelSize = GamePanel.preferredSizeFor(cell);
        for (JComponent screen : screens.values()) screen.setPreferredSize(panelSize);
        pack();
        setLocationRelativeTo(null);
    }

    /** 프로그램 종료 (Main Menu '게임 종료', 창 닫기) */
    public void exitApplication() {
        closeBattle();
        closeP2p();
        session.end();
        dispose();
        System.exit(0);
    }

    /** 시작할 때 읽은 저장 파일 경고를 Main Menu에 1회 표시 */
    public void showWarnings(List<String> warnings) {
        mainMenuPanel.showWarnings(warnings);
    }

    // ── 화면 흐름 ────────────────────────────
    private void addScreen(Screen screen, JComponent panel) {
        screens.put(screen, panel);
        root.add(panel, screen.name());
    }

    private void startGame() {
        startGame(GameMode.CLASSIC);
    }

    private GameSession createSession(GameMode mode) {
        return newSession(mode, settings.getDifficulty(), new Random(), new SwingGameClock(this::onTick), gamePanel);
    }

    /**
     * 한 판을 진행할 세션을 만든다. 블록은 난이도 가중치를 쓰는 WeightedBlockGenerator로 만들어,
     * 분포 테스트를 통과한 생성기가 실제 게임에서도 그대로 쓰이게 한다.
     * 아이템 모드는 항상 NORMAL 규칙이라 가중치도 NORMAL로 맞춘다.
     */
    static GameSession newSession(GameMode mode, Difficulty difficulty, Random random, GameClock clock, GameListener listener) {
        Difficulty effective = mode == GameMode.ITEM ? Difficulty.NORMAL : difficulty;
        return new GameSession(mode, effective, new WeightedBlockGenerator(effective.weights(), random), clock, listener);
    }

    private void startGame(GameMode mode) {
        closeBattle();
        session.end();
        session = createSession(mode);
        keyBindings.bindGameKeys(gamePanel, settings.getKeyMap(), session);
        keyBindings.setSuspended(false);
        keyBindings.clearPressedKeys();
        showScreen(Screen.GAME);
        session.start();
    }

    private void openSettings() {
        settingPanel.reset();
        showScreen(Screen.SETTINGS);
    }

    private void openScoreboard() {
        scoreBoardPanel.show(scoreboard.entries(), -1);
        showScreen(Screen.SCOREBOARD);
    }

    private void onTick() {
        session.tick();
    }

    private void onGameOver(GameSnapshot snapshot) {
        finalResult = snapshot;
        keyBindings.clearPressedKeys();
        gameOverPanel.show(snapshot);
        showScreen(Screen.GAME_OVER);
    }

    // GameOver에서 Enter: GAME_OVER -> ENDED, Top 10이면 이름 입력, 아니면 스코어보드 (명세 9)
    private void afterGameOver() {
        session.end();
        if (scoreboard.qualifies(finalResult.score(), finalResult.mode(), finalResult.difficulty())) {
            nameInputPanel.prepare(finalResult.score());
            showScreen(Screen.NAME_INPUT);
        } else {
            scoreBoardPanel.show(scoreboard.entries(), -1, true, finalResult.mode(), finalResult.difficulty());
            showScreen(Screen.SCOREBOARD);
        }
    }

    // 이름 확정 즉시 저장 (SBD-6). 저장에 실패해도 이번 실행에서는 순위를 보여준다
    private void submitName(String name) {
        ScoreEntry entry = new ScoreEntry(name, finalResult.score(), finalResult.level(), finalResult.lines(), finalResult.mode(), finalResult.difficulty(), Instant.now());
        int rank = scoreboard.add(entry);
        boolean saved = saveScoreboard();
        scoreBoardPanel.show(scoreboard.entries(), rank, true, finalResult.mode(), finalResult.difficulty());
        if (!saved) scoreBoardPanel.showNotice(Messages.get("scoreboard.saveFailed"));
        showScreen(Screen.SCOREBOARD);
    }

    // ── Pause · Restart · Quit ───────────────
    private void resumeGame() {
        session.resume();
    }

    /** Quit 키 또는 Pause 메뉴의 Quit: 확인창 동안 게임 정지 (QIT-1) */
    private void requestQuit() {
        if (gamePanel.isConfirmShowing()) return;
        GameState state = session.getState();
        if (state != GameState.PLAYING && state != GameState.CLEARING && state != GameState.PAUSED) return;

        session.requestQuit();
        showConfirm(new ConfirmDialog(Messages.get("confirm.quit"), () -> {
            closeConfirm();
            session.confirmQuit(); // 점수는 저장하지 않는다
            showScreen(Screen.MAIN_MENU);
        }, () -> {
            closeConfirm();
            session.cancelQuit(); // Quit 전이 PLAYING이었다면 타이머 재시작
        }));
    }

    /** Pause 메뉴의 Restart: Yes면 새 세션, No면 Pause 화면 유지 (RST-1) */
    private void confirmRestart() {
        showConfirm(new ConfirmDialog(Messages.get("confirm.restart"), () -> {
            closeConfirm();
            keyBindings.clearPressedKeys();
            session.restart();
        }, this::closeConfirm));
    }

    private void showConfirm(ConfirmDialog dialog) {
        keyBindings.setSuspended(true);
        gamePanel.showConfirm(dialog);
    }

    private void closeConfirm() {
        gamePanel.closeConfirm();
        keyBindings.setSuspended(false);
    }

    /** 창 포커스 이탈: 게임 중이면 자동 Pause, 눌림 상태 초기화 (PAU-3, KEY-8) */
    private void onFocusLost() {
        keyBindings.clearPressedKeys();
        if (currentScreen == Screen.BATTLE && battleController != null) battleController.focusLost();
        if (currentScreen == Screen.GAME) session.onFocusLost();
    }

    public void openBattleModes() {
        session.end();
        keyBindings.setSuspended(true);
        keyBindings.clearPressedKeys();
        closeBattle();
        showScreen(Screen.BATTLE_MODES);
    }

    /** 로컬 대전 (S9-B1). 결과는 대전 결과 화면으로만 가고 이름 입력·스코어보드 저장을 거치지 않는다 (R3-NOSCORE) */
    public void startBattle(BattleMode mode) {
        java.util.Objects.requireNonNull(mode);
        var keys = new BattleKeyMap();
        String label = BattleModes.label(mode);
        BattlePanel panel = prepareBattleScreen(keys, label, mode, null);
        long generation = battleGeneration;
        battleController = new BattleScreenController(getRootPane(),
                battleFactory.apply(BattleModes.localConfig(mode, settings.getDifficulty())), keys, panel,
                snapshot -> javax.swing.SwingUtilities.invokeLater(() -> {
                    if (currentScreen != Screen.BATTLE || generation != battleGeneration) return;
                    battleResultPanel.setSnapshot(snapshot, label);
                    showScreen(Screen.BATTLE_RESULT);
                }),
                this::confirmBattleForfeit,
                this::openBattleModes);
    }

    /** viewer: P2P에서 이 PC가 조작하는 참가자. 로컬 대전이면 null */
    private BattlePanel prepareBattleScreen(BattleKeyMap keys, String label, BattleMode mode, PlayerId viewer) {
        session.end();
        keyBindings.setSuspended(true);
        keyBindings.clearPressedKeys();
        closeBattle();
        battleOnline = viewer != null;
        JComponent previous = screens.remove(Screen.BATTLE);
        if (previous != null) root.remove(previous);
        var panel = new BattlePanel(settings.isColorBlindMode(), keys.guide(), label, mode, viewer);
        panel.setPreferredSize(GamePanel.preferredSizeFor(fitCellSize(settings.getScreenSize().cellPixels())));
        addScreen(Screen.BATTLE, panel);
        showScreen(Screen.BATTLE);
        return panel;
    }

    // ── P2P 대전 (S9-B2) ─────────────────────
    /** 대전 메뉴의 P2P: 호스트 대기·IP 접속·로비 화면. 들어올 때마다 새 연결을 쓴다 */
    public void openP2pLobby() {
        session.end();
        keyBindings.setSuspended(true);
        keyBindings.clearPressedKeys();
        closeBattle();
        if (p2p == null) {
            LobbyClient lobby = lobbyFactory.get();
            java.util.function.IntSupplier port = lobby instanceof SocketLobbyClient socket ? socket::localPort : () -> -1;
            java.util.function.Supplier<List<String>> recent = List::of;
            if (lobby instanceof SocketLobbyClient saved) recent = saved::recentPeers;
            p2p = new P2pLobbyController(lobby, new P2pLobbyController.Navigation() {
                @Override public void startMatch(BattleClient client, MatchConfig config, LobbySnapshot.Role role) {
                    startOnlineBattle(client, config, role);
                }
                @Override public void connectionLost() {
                    if (currentScreen == Screen.BATTLE || currentScreen == Screen.P2P_RESULT) {
                        showScreen(Screen.P2P_LOBBY);
                        if (p2p != null) p2p.panel().focusTarget().requestFocusInWindow();
                    }
                }
                @Override public void back() { openBattleModes(); }
                @Override public void healthChanged(tetris.feature.network.lobby.LinkHealth health) {
                    if (battleOnline && screens.get(Screen.BATTLE) instanceof BattlePanel battle) {
                        battle.setConnectionNotice(P2pLobbyController.lagText(health));
                    }
                }
            }, port, tetris.feature.network.LanAddresses::current, recent);
            JComponent previous = screens.remove(Screen.P2P_LOBBY);
            if (previous != null) root.remove(previous);
            var panel = p2p.panel();
            panel.setPreferredSize(GamePanel.preferredSizeFor(fitCellSize(settings.getScreenSize().cellPixels())));
            addScreen(Screen.P2P_LOBBY, panel);
        }
        showScreen(Screen.P2P_LOBBY);
    }

    /** 이 화면은 자기 참가자 한 명만 조작한다. 결과는 호스트가 정한 것을 양쪽이 같이 본다 (R3-LIVE, R3-LOSS) */
    private void startOnlineBattle(BattleClient client, MatchConfig config, LobbySnapshot.Role role) {
        var keys = BattleKeyMap.online();
        String label = BattleModes.label(config.mode()) + " · P2P "
                + (role == LobbySnapshot.Role.HOST ? "(나: P1 왼쪽)" : "(나: P2 오른쪽)");
        PlayerId viewer = role == LobbySnapshot.Role.HOST ? PlayerId.ONE : PlayerId.TWO;
        BattlePanel panel = prepareBattleScreen(keys, label, config.mode(), viewer);
        long generation = battleGeneration;
        battleController = BattleScreenController.online(getRootPane(), client, keys, panel,
                snapshot -> javax.swing.SwingUtilities.invokeLater(() -> {
                    if (currentScreen != Screen.BATTLE || generation != battleGeneration) return;
                    onlineResultPanel.setSnapshot(snapshot, label, viewer);
                    showScreen(Screen.P2P_RESULT);
                }));
    }

    private void backToP2pLobby() {
        if (p2p == null) { openBattleModes(); return; }
        p2p.returnToLobby();
        showScreen(Screen.P2P_LOBBY);
        p2p.panel().focusTarget().requestFocusInWindow();
    }

    /** 결과 화면 "다시 대전": 같은 연결의 로비로 가서 이 PC의 준비를 켠다 (R3-REMATCH) */
    private void rematchP2p() {
        if (p2p == null) { openBattleModes(); return; }
        p2p.rematch();
        showScreen(Screen.P2P_LOBBY);
        p2p.panel().focusTarget().requestFocusInWindow();
    }

    private void closeP2p() {
        if (p2p == null) return;
        var closing = p2p;
        p2p = null;
        closing.close();
    }

    private PlayerId confirmBattleForfeit() {
        int choice = JOptionPane.showOptionDialog(this, "기권할 플레이어를 선택하세요.", "대전 기권",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new Object[]{"P1 기권", "P2 기권", "취소"}, "취소");
        return switch (choice) { case 0 -> PlayerId.ONE; case 1 -> PlayerId.TWO; default -> null; };
    }

    private void closeBattle() {
        battleGeneration++;
        if (battleController != null) { battleController.close(); battleController = null; }
    }

    @Override public void dispose() { closeBattle(); closeP2p(); super.dispose(); }

    // ── 설정·저장 ────────────────────────────
    private boolean onSettingsChanged() {
        if (settings.getScreenSize() != appliedScreenSize) applyScreenSize(settings.getScreenSize());
        gamePanel.applySettings(settings);
        keyBindings.rebind(settings.getKeyMap());
        return saveSettings();
    }

    private boolean resetScoreboard() {
        scoreboard.clear();
        return saveScoreboard();
    }

    // 저장 중 예상 못 한 예외도 실패로 보고 게임은 계속한다 (SET-4)
    private boolean saveSettings() {
        try {
            return settingsRepository.save(settings);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean saveScoreboard() {
        try {
            return scoreboardRepository.save(scoreboard);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // 사용 가능한 화면(작업 표시줄 제외)에 맞춘 실제 셀 크기. 설정값은 바꾸지 않는다 (RND-3)
    private int fitCellSize(int desired) {
        if (!isDisplayable()) pack(); // 창 테두리 크기(insets)는 pack 이후에 알 수 있다
        Insets insets = getInsets();
        Rectangle available = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int largestFit = largestFittingCell(available.width - insets.left - insets.right,
                available.height - insets.top - insets.bottom);
        return scaledCellSize(desired, largestFit);
    }

    /** 주어진 공간(창 테두리 제외)에 게임 화면이 들어가는 가장 큰 셀 크기. Large 크기가 상한, MIN_CELL_PIXELS가 하한 */
    static int largestFittingCell(int width, int height) {
        int cell = ScreenSize.LARGE.cellPixels();
        while (cell > MIN_CELL_PIXELS) {
            Dimension size = GamePanel.preferredSizeFor(cell);
            if (size.width <= width && size.height <= height) break;
            cell--;
        }
        return cell;
    }

    /**
     * Large가 화면에 들어가면 설정 크기 그대로. 안 들어가면 세 크기를 같은 비율로 줄여 Small &lt; Medium &lt; Large 구분을 유지한다.
     * (Large만 줄이면 1080p·배율 150% 노트북에서 Large 31 px ≈ Medium 30 px가 되어 크기가 사실상 2가지가 된다)
     */
    static int scaledCellSize(int desired, int largestFit) {
        int largest = ScreenSize.LARGE.cellPixels();
        if (largestFit >= largest) return desired;
        return Math.max(MIN_CELL_PIXELS, desired * largestFit / largest);
    }
}
