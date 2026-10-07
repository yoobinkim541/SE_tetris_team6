package tetris.debug;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.Random;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.rule.RandomBlockGenerator;

/**
 * 방향키로 GameSession을 조작하는 개발용 Swing 창. 실제 게임 UI(GamePanel)가 생기기 전까지 조작을 눈으로 확인하는 용도.
 * ← → 이동, ↓ Soft Drop, ↑ 회전, Enter 새 게임. 낙하는 LevelPolicy 간격의 실제 타이머(EDT)로 진행된다.
 * 키 반복 제한·IME 처리 같은 명세 §7.3 규칙은 적용하지 않는다 (실제 UI의 KeyBindingManager 몫).
 */
public final class DebugWindow {
    private GameSession session;
    private final Timer timer = new Timer(0, e -> session.tick()); // 새 게임마다 바뀌는 session을 따라가도록 필드를 참조
    private final JFrame frame = new JFrame();
    private final JTextArea view = new JTextArea(26, 40);

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : new Random().nextLong();
        SwingUtilities.invokeLater(() -> new DebugWindow().open(seed));
    }

    private void open(long seed) {
        view.setEditable(false);
        view.setFocusable(false);
        view.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        frame.add(view);
        bindKeys(frame.getRootPane());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        newGame(seed);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void newGame(long seed) {
        timer.stop();
        frame.setTitle("Tetris Debug  seed=" + seed + "   ← → 이동  ↓ Soft Drop  ↑ 회전  Enter 새 게임");
        session = new GameSession(new RandomBlockGenerator(new Random(seed)), new TimerClock(), new ViewListener());
        session.start();
    }

    private void bindKeys(JComponent root) {
        bind(root, "LEFT", () -> session.moveLeft());
        bind(root, "RIGHT", () -> session.moveRight());
        bind(root, "DOWN", () -> session.moveDown());
        bind(root, "UP", () -> session.rotateRight());
        bind(root, "ENTER", () -> newGame(new Random().nextLong()));
    }

    private static void bind(JComponent component, String key, Runnable action) {
        component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        component.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private final class TimerClock implements GameClock {
        @Override
        public void start(int intervalMillis) {
            timer.setDelay(intervalMillis);
            timer.setInitialDelay(intervalMillis);
            timer.restart();
        }

        @Override
        public void stop() {
            timer.stop();
        }
    }

    private final class ViewListener implements GameListener {
        @Override
        public void onChanged(GameSnapshot snapshot) {
            view.setText(AsciiBoard.render(snapshot));
        }

        @Override
        public void onGameOver(GameSnapshot snapshot) {
            view.setText(AsciiBoard.render(snapshot) + "\n\nGAME OVER   [Enter] 새 게임");
        }
    }
}
