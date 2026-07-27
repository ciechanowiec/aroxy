package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.governance.Justification;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prints log files to the CLI output: a one-shot dump of an existing file, and a follow loop that
 * keeps emitting appended bytes via a {@link LogFollower} until the thread is interrupted. The follow
 * loop is the CLI's {@code tail -f}. It is intentionally not exercised by unit tests, which drive the
 * pollable {@link LogFollower} directly.
 */
final class LogPrinter {

    private static final long PAUSE_MILLIS = 250L;
    private final CliOutput out;

    LogPrinter(CliOutput out) {
        this.out = out;
    }

    void line(String text) {
        this.out.line(text);
    }

    void dump(Path file) {
        if (!Files.exists(file)) {
            this.out.line("(no log yet at " + file + ")");
            return;
        }
        this.out.line(this.read(file).stripTrailing());
    }

    @Justification(
        "intentional bounded polling loop"
    )
    @SuppressWarnings(
        "BusyWait"
    )
    void follow(Path file) throws InterruptedException {
        LogFollower follower = new LogFollower(file);
        Thread current = Thread.currentThread();
        while (!current.isInterrupted()) {
            follower.poll().ifPresent(text -> this.out.line(text.stripTrailing()));
            Thread.sleep(PAUSE_MILLIS);
        }
    }

    private String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }
}
