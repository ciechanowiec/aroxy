package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.daemon.Home;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Requesting a reload waits for the daemon to bump its published generation.
 * When a running daemon advances the generation after the trigger is touched, the request returns a
 * confirmed success rather than merely a fire-and-forget.
 */
class ReloadRequestTest {

    private static final long BUMP_PAUSE_MILLIS = 150L;
    private static final long JOIN_MILLIS = 1000L;

    @Test
    @SneakyThrows
    void confirmsOnceTheDaemonAdvancesTheGeneration(@TempDir Path directory) {
        Home home = new Home(directory);
        writeRuntime(home.runtimeFile(), 1);
        Thread bumper = new Thread(() -> bumpAfterPause(home.runtimeFile()));
        bumper.start();
        try {
            assertTrue(ReloadRequest.send(home), "the reload is confirmed once the generation advances");
        } finally {
            bumper.join(JOIN_MILLIS);
        }
    }

    @Test
    void reportsFailureWhenNoDaemonEverAdvancesTheGeneration(@TempDir Path directory) {
        assertFalse(
            ReloadRequest.send(new Home(directory)),
            "with no daemon to advance the generation, the bounded wait exhausts and reports failure"
        );
    }

    private static void bumpAfterPause(Path runtime) {
        try {
            Thread.sleep(BUMP_PAUSE_MILLIS);
            writeRuntime(runtime, 2);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static void writeRuntime(Path runtime, int generation) {
        String content = "listen\t127.0.0.1:0\ngeneration\t" + generation + System.lineSeparator();
        try {
            Files.writeString(runtime, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write runtime snapshot " + runtime, exception);
        }
    }
}
