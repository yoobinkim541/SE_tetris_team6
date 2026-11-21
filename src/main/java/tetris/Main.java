package tetris;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import tetris.feature.save.ErrorLog;
import tetris.feature.save.JsonScoreboardRepository;
import tetris.feature.save.JsonSettingsRepository;
import tetris.feature.save.LoadResult;
import tetris.feature.save.ScoreboardRepository;
import tetris.feature.save.SettingsRepository;
import tetris.feature.score.Scoreboard;
import tetris.feature.setting.Settings;
import tetris.ui.MainFrame;
import tetris.ui.Messages;

public class Main {
    public static void main(String[] args) {
        ErrorLog.install(); // javaw는 System.err을 버리므로 EDT 예외를 파일에 남긴다
        DevMode.enableFrom(args); // --dev: 개발자 모드 (P2P 로비 측정 패널)
        SwingUtilities.invokeLater(Main::launch); // Swing과 GameSession은 EDT 단일 스레드에서만 (TMR-1)
    }

    private static void launch() {
        // 1) 저장 파일 로드. 문제가 있어도 기본값으로 실행하고, 경고는 Main Menu에서 1회 표시 (명세 12.2)
        List<String> warnings = new ArrayList<>();
        SettingsRepository settingsRepository = new JsonSettingsRepository();
        ScoreboardRepository scoreboardRepository = new JsonScoreboardRepository();
        Settings settings = load(settingsRepository::load, Settings::new, "warn.settingsLoad", warnings);
        Scoreboard scoreboard = load(scoreboardRepository::load, () -> new Scoreboard(List.of()),
                "warn.scoreboardLoad", warnings);

        // 설정 파일의 개발자 토글도 --dev와 같은 개발자 모드 스위치로 반영한다 (dev-net-overlay)
        DevMode.setSettingEnabled(settings.isDevNetworkOverlay());
        tetris.ui.theme.GameTheme.installSwingDefaults();

        // 2) MainFrame 생성 (화면 크기는 생성자에서 적용). 창 제목의 [개발자 모드] 표시도 MainFrame이 맡는다
        MainFrame frame = new MainFrame(settings, scoreboard, settingsRepository, scoreboardRepository);
        ErrorLog.setReporter(frame::reportFailure);
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> ErrorLog.shared().report(thread.getName(), error));

        // 3) Main Menu 표시
        frame.showScreen(MainFrame.Screen.MAIN_MENU);
        frame.showWarnings(warnings);
    }

    // 저장 파일 문제로 실행 불가가 되면 안 된다. 예상 못 한 예외도 기본값 + 경고로 대체한다
    private static <T> T load(Supplier<LoadResult<T>> loader, Supplier<T> fallback, String warningKey, List<String> warnings) {
        try {
            LoadResult<T> result = loader.get();
            if (result.warnings() != null) warnings.addAll(result.warnings());
            return result.value() != null ? result.value() : fallback.get();
        } catch (RuntimeException e) {
            warnings.add(Messages.get(warningKey));
            return fallback.get();
        }
    }
}
