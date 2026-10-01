package tetris.debug;

import java.util.Random;
import java.util.Scanner;
import tetris.feature.game.GameClock;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.rule.RandomBlockGenerator;

/**
 * UI 없이 GameSession을 직접 조작하며 보드를 텍스트로 확인하는 콘솔.
 * VS Code에서 main 위의 Run을 누르면 된다. 게임마다 시드를 출력하고, 그 시드를 인자로 주면 첫 게임에서 같은 블록 순서가 재현된다.
 * 타이머는 돌지 않으므로 낙하는 명령으로 직접 일으킨다. 조작 메서드가 구현되는 대로 COMMANDS에 추가한다.
 * 보드는 리스너가 아니라 명령이 끝날 때마다 한 번 출력한다 (여러 칸 낙하 중간 그림으로 화면이 넘치지 않도록).
 */
public final class DebugConsole {
    private static final String COMMANDS = "[a/d] 좌우  [s] Soft Drop  [w] 회전  [t] 한 칸 낙하  [t N] N칸  [f] 바닥까지  [Enter] 새 게임  [q] 종료";
    private static final int MAX_TICKS = 100; // f 명령이 끝나지 않는 경우 대비

    public static void main(String[] args) {
        System.out.println(COMMANDS);
        GameSession session = newGame(args.length > 0 ? Long.parseLong(args[0]) : new Random().nextLong());
        try (Scanner in = new Scanner(System.in)) {
            while (in.hasNextLine()) {
                String command = in.nextLine().trim();
                if (command.equals("q")) break;
                if (command.isEmpty()) session = newGame(new Random().nextLong());
                else if (!run(session, command)) System.out.println("모르는 명령: " + command + "   " + COMMANDS);
            }
        }
    }

    // 게임마다 새 시드로 세션을 만든다. 같은 Random을 이어 쓰면 출력한 시드로 그 게임을 재현할 수 없다
    private static GameSession newGame(long seed) {
        System.out.println("=== 새 게임  seed=" + seed + " ===");
        GameSession session = new GameSession(new RandomBlockGenerator(new Random(seed)), new NoOpClock(), new PrintingListener());
        session.start();
        AsciiBoard.print(session.snapshot());
        return session;
    }

    private static boolean run(GameSession session, String command) {
        String[] parts = command.split("\\s+");
        switch (parts[0]) {
            case "t" -> {
                int times = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                for (int i = 0; i < times; i++) session.tick();
            }
            case "f" -> fallToBottom(session);
            case "a" -> session.moveLeft();
            case "d" -> session.moveRight();
            case "s" -> session.moveDown();
            case "w" -> session.rotateRight();
            default -> {
                return false;
            }
        }
        AsciiBoard.print(session.snapshot());
        return true;
    }

    // 하강이 실패해 앵커가 더 이상 바뀌지 않을 때까지 tick
    private static void fallToBottom(GameSession session) {
        for (int i = 0; i < MAX_TICKS; i++) {
            int before = session.snapshot().currentBlock().getRow();
            session.tick();
            if (session.snapshot().currentBlock() == null || session.snapshot().currentBlock().getRow() == before) return;
        }
    }

    private static final class NoOpClock implements GameClock {
        @Override
        public void start(int intervalMillis) {
            System.out.println("(clock.start " + intervalMillis + "ms)");
        }

        @Override
        public void stop() {
            System.out.println("(clock.stop)");
        }
    }

    private static final class PrintingListener implements GameListener {
        @Override
        public void onChanged(GameSnapshot snapshot) {
        }

        @Override
        public void onGameOver(GameSnapshot snapshot) {
            System.out.println("=== GAME OVER ===");
            AsciiBoard.print(snapshot);
        }
    }
}
