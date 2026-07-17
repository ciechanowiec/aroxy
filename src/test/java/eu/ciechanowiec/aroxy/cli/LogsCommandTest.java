package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogsCommandTest {

    private static final String PROPERTY = "aroxy.home";

    @TempDir
    private Path home;

    @BeforeEach
    void pointHome() {
        System.setProperty(PROPERTY, this.home.toString());
    }

    @AfterEach
    void clearHome() {
        System.clearProperty(PROPERTY);
    }

    @Test
    void dumpsTheAuditLog() {
        this.write("logs/audit.log", "audited line\n");
        CliCapture.Result result = CliCapture.run("logs");
        assertEquals(0, result.exit());
        assertTrue(result.out().contains("audited line"), result.out());
    }

    @Test
    void dumpsTheAppLogWithFlag() {
        this.write("logs/aroxy.log", "diagnostic line\n");
        CliCapture.Result result = CliCapture.run("logs", "--app");
        assertTrue(result.out().contains("diagnostic line"), result.out());
    }

    @Test
    void allDumpsBothLogs() {
        this.write("logs/audit.log", "AAA\n");
        this.write("logs/aroxy.log", "BBB\n");
        CliCapture.Result result = CliCapture.run("logs", "--all");
        assertTrue(result.out().contains("AAA"), result.out());
        assertTrue(result.out().contains("BBB"), result.out());
    }

    @Test
    void wireListsDumpFiles() {
        this.write("logs/wire/req-1.txt", "redacted\n");
        CliCapture.Result result = CliCapture.run("logs", "--wire");
        assertEquals(0, result.exit());
        assertTrue(result.out().contains("req-1.txt"), result.out());
    }

    private void write(String relative, CharSequence text) {
        Path file = this.home.resolve(relative);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write " + file, exception);
        }
    }
}
