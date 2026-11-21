package tetris.feature.save;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * 예외를 사용자 데이터 폴더의 error.log에 남긴다. javaw는 콘솔이 없어 System.err이 버려진다.
 * 파일이 한도를 넘으면 error.log.1로 한 번 돌리고 새로 쓴다.
 */
public final class ErrorLog {
    public static final int MAX_BYTES = 256 * 1024;

    private static final ErrorLog SHARED = new ErrorLog(defaultPath());
    private static volatile Consumer<Throwable> reporter = error -> { };

    private final Path file;
    private final Object lock = new Object();

    public ErrorLog(Path file) {
        if (file == null) throw new IllegalArgumentException("file");
        this.file = file;
    }

    public static ErrorLog shared() {
        return SHARED;
    }

    /** %APPDATA%\SETetrisTeam6\error.log. 설정 파일과 같은 폴더다 */
    public static Path defaultPath() {
        return DataDirectory.resolve().resolve("error.log");
    }

    /**
     * AWT가 뜨기 전에 부른다. EDT 예외는 {@code sun.awt.exception.handler}가 받고,
     * 그 밖의 스레드는 기본 핸들러가 받는다.
     */
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> shared().report(thread.getName(), error));
        System.setProperty("sun.awt.exception.handler", "tetris.feature.save.EdtExceptionHandler");
    }

    /** 화면에 짧게 알릴 때. 없으면 파일만 남긴다 */
    public static void setReporter(Consumer<Throwable> next) {
        reporter = next == null ? error -> { } : next;
    }

    public void report(String where, Throwable error) {
        record(where, error);
        try {
            reporter.accept(error);
        } catch (RuntimeException ignored) {
            // 안내가 실패해도 파일에는 이미 남겼다
        }
    }

    public void record(String where, Throwable error) {
        if (error == null) return;
        String text = Instant.now() + " [" + (where == null ? "?" : where) + "]\n" + stack(error) + "\n";
        synchronized (lock) {
            try {
                Path parent = file.getParent();
                if (parent != null) Files.createDirectories(parent);
                rotate();
                Files.writeString(file, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException ignored) {
                System.err.println("error.log를 쓰지 못했습니다: " + ignored);
            }
        }
    }

    public Path file() {
        return file;
    }

    private void rotate() throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) < MAX_BYTES) return;
        Path previous = file.resolveSibling("error.log.1");
        Files.deleteIfExists(previous);
        Files.move(file, previous);
    }

    private static String stack(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
