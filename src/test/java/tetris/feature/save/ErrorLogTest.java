package tetris.feature.save;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ErrorLogTest {
    @TempDir Path directory;

    @Test
    void rotatesAndKeepsTheNewStack() throws Exception {
        ErrorLog log = new ErrorLog(directory.resolve("error.log"));
        Files.writeString(log.file(), "x".repeat(ErrorLog.MAX_BYTES));
        log.record("test", new IllegalStateException("boom"));
        assertTrue(Files.exists(directory.resolve("error.log.1")));
        assertTrue(Files.readString(log.file()).contains("boom"));
        assertTrue(Files.size(log.file()) < ErrorLog.MAX_BYTES);
    }
}
