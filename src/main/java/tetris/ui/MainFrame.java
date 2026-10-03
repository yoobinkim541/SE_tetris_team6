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
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.rule.RandomBlockGenerator;
import tetris.feature.save.ScoreboardRepository;
import tetris.feature.save.SettingsRepository;
import tetris.feature.score.ScoreEntry;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.ScreenSize;
import tetris.feature.setting.Settings;

/**
 * 메인 윈도우. CardLayout으로 화면을 전환한다 (명세 9, 13장). 크기 조절 불가.
 * 화면 흐름(Main Menu → Game → GameOver → Name → Scoreboard)과 저장 호출을 이 클래스가 맡는다.
 * 게임 세션 객체는 하나를 계속 쓰고 새 게임마다 start()로 초기화한다.
 */
public class MainFrame extends JFrame {
    /** 앱 화면 흐름 (GameState와 별개) */
    public enum Screen { MAIN_MENU, GAME, SETTINGS, SCOREBOARD, NAME_INPUT, GAME_OVER }

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
    private final GameSession session;

    private Screen currentScreen;
    private ScreenSize appliedScreenSize;
    private GameSnapshot finalResult; // 방금 끝난 게임의 마지막 상태 (Name 입력·스코어보드 등록용)

    public MainFrame(Settings settings, Scoreboard scoreboard,
                     SettingsRepository settingsRepository, ScoreboardRepository scoreboardRepository) {
        super(Messages.get("app.title"));
        this.settings = settings;
        this.scoreboard = scoreboard;
        this.settingsRepository = settingsRepository;
        this.scoreboardRepository = scoreboardRepository;

        mainMenuPanel = new MainMenuPanel(List.of(
                new MenuItem(Messages.get("menu.start"), this::startGame),
                new MenuItem(Messages.get("menu.settings"), this::openSettings),
                new MenuItem(Messages.get("menu.scoreboard"), this::openScoreboard),
                new MenuItem(Messages.get("menu.exit"), this::exitApplication)));
        PausePanel pausePanel = new PausePanel(this::resumeGame, this::confirmRestart, this::requestQuit);
        gamePanel = new GamePanel(settings, pausePanel, this::onGameOver);
        gameOverPanel = new GameOverPanel(this::afterGameOver);
        nameInputPanel = new NameInputPanel(this::submitName);
        scoreBoardPanel = new ScoreBoardPanel(() -> showScreen(Screen.MAIN_MENU), this::exitApplication);
        settingPanel = new SettingPanel(settings, this::onSettingsChanged, this::resetScoreboard,
                () -> showScreen(Screen.MAIN_MENU));

        session = new GameSession(new RandomBlockGenerator(), new SwingGameClock(this::onTick), gamePanel);
        keyBindings = new KeyBindingManager(this::requestQuit);
        keyBindings.bindGameKeys(gamePanel, settings.getKeyMap(), session);

        addScreen(Screen.MAIN_MENU, mainMenuPanel);
        addScreen(Screen.GAME, gamePanel);
        addScreen(Screen.GAME_OVER, gameOverPanel);
        addScreen(Screen.NAME_INPUT, nameInputPanel);
        addScreen(Screen.SCOREBOARD, scoreBoardPanel);
        addScreen(Screen.SETTINGS, settingPanel);
        setContentPane(root);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // EXT-1: 확인 없이 종료, 진행 중 게임은 폐기
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent e) {
                onFocusLost();
            }
        });
        applyScreenSize(settings.getScreenSize());
    }

    /** 화면 전환. 기존 ReturnToMain 역할 = showScreen(MAIN_MENU) */
    public void showScreen(Screen screen) {
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
        if (scoreboard.qualifies(finalResult.score())) {
            nameInputPanel.prepare(finalResult.score());
            showScreen(Screen.NAME_INPUT);
        } else {
            scoreBoardPanel.show(scoreboard.entries(), -1, true);
            showScreen(Screen.SCOREBOARD);
        }
    }

    // 이름 확정 즉시 저장 (SBD-6). 저장에 실패해도 이번 실행에서는 순위를 보여준다
    private void submitName(String name) {
        ScoreEntry entry = new ScoreEntry(name, finalResult.score(), finalResult.level(), finalResult.lines(), Instant.now());
        int rank = scoreboard.add(entry);
        boolean saved = saveScoreboard();
        scoreBoardPanel.show(scoreboard.entries(), rank, true);
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
        if (state != GameState.PLAYING && state != GameState.PAUSED) return;

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
        if (currentScreen == Screen.GAME) session.onFocusLost();
    }

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
