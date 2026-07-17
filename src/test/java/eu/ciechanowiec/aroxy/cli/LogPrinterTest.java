package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Printing log files to the CLI output.
 * A one-shot dump announces a missing file, prints an existing file's content, and errors on an
 * unreadable one; the follow loop keeps running until its thread is interrupted.
 */
class LogPrinterTest {

    private static final long JOIN_MILLIS = 1000L;
    private static final long ENTER_LOOP_MILLIS = 100L;

    @TempDir
    private Path directory;

    private ByteArrayOutputStream buffer;
    private LogPrinter printer;

    @BeforeEach
    void setUp() {
        this.buffer = new ByteArrayOutputStream();
        this.printer = new LogPrinter(new CliOutput(new PrintStream(this.buffer, true, StandardCharsets.UTF_8)));
    }

    @Test
    void dumpAnnouncesAMissingLog() {
        this.printer.dump(this.directory.resolve("absent.log"));
        assertTrue(this.captured().contains("(no log yet at"), "a missing file is announced, not read");
    }

    @Test
    @SneakyThrows
    void dumpPrintsExistingContent() {
        Path log = this.directory.resolve("app.log");
        Files.writeString(log, "hello world\n\n");
        this.printer.dump(log);
        assertTrue(this.captured().contains("hello world"), "the file content is printed");
    }

    @Test
    @SneakyThrows
    void dumpReportsAnUnreadableLog() {
        Path asDirectory = this.directory.resolve("app.log");
        Files.createDirectory(asDirectory);
        assertThrows(UncheckedIOException.class, () -> this.printer.dump(asDirectory), "a directory cannot be dumped");
    }

    @Test
    void lineForwardsTextToOutput() {
        this.printer.line("a status line");
        assertTrue(this.captured().contains("a status line"), "the line is forwarded to the output");
    }

    @Test
    @SneakyThrows
    void followReturnsWhenTheThreadIsAlreadyInterrupted() {
        Path log = this.directory.resolve("app.log");
        Files.writeString(log, "x\n");
        Thread.currentThread().interrupt();
        this.printer.follow(log);
        assertTrue(Thread.interrupted(), "an already-interrupted thread skips the loop and returns");
    }

    @Test
    @SneakyThrows
    void followStopsWhenItsThreadIsInterrupted() {
        Path log = this.directory.resolve("app.log");
        Files.writeString(log, "x\n");
        Thread worker = new Thread(() -> this.runFollow(log));
        worker.start();
        Thread.sleep(ENTER_LOOP_MILLIS);
        worker.interrupt();
        worker.join(JOIN_MILLIS);
        assertFalse(worker.isAlive(), "the follow loop stops once its thread is interrupted");
    }

    private void runFollow(Path log) {
        try {
            this.printer.follow(log);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private String captured() {
        return this.buffer.toString(StandardCharsets.UTF_8);
    }
}
