package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.daemon.Home;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Requesting a reload waits for the daemon to bump its published generation.
 * When a running daemon advances the generation after the trigger is touched, the request returns a
 * confirmed success rather than merely a fire-and-forget.
 */
class ReloadRequestTest {

    @Test
    void confirmsOnceTheDaemonAdvancesTheGeneration(@TempDir Path directory) {
        Home home = new Home(directory);
        writeRuntime(home.runtimeFile(), 1);
        assertTrue(
            ReloadRequest.send(home, () -> writeRuntime(home.runtimeFile(), 2)),
            "the reload is confirmed once the generation advances"
        );
    }

    @Test
    void reportsFailureWhenNoDaemonEverAdvancesTheGeneration(@TempDir Path directory) {
        assertFalse(
            ReloadRequest.send(new Home(directory), Thread::onSpinWait),
            "with no daemon to advance the generation, the bounded wait exhausts and reports failure"
        );
    }

    @Test
    void anInterruptedPauseRestoresTheFlag() {
        Thread.currentThread().interrupt();
        ReloadRequest.sleep();
        assertTrue(Thread.interrupted(), "the reload wait preserves an interrupt for its caller");
    }

    private static void writeRuntime(Path runtime, int generation) {
        String content = "start\t1\ngeneration\t" + generation
            + "\nconfig.mask_secrets\ttrue\ngateway\tCodex CLI\t127.0.0.1:8789\thttps://example.test\n";
        try {
            Files.writeString(runtime, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write runtime snapshot " + runtime, exception);
        }
    }
}
