package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the reload watcher runs its action when, and only when, the control-file trigger's
 * stamp changes, using a real scheduled poll over a real temporary directory.
 */
class ReloadWatcherTest {

    private static final int POLLS = 60;
    private static final int POLLS_TO_OBSERVE = 16;
    private static final long PAUSE_MILLIS = 50L;

    @Test
    void runsTheActionWhenTheTriggerChanges(@TempDir Path directory) {
        ControlFiles control = new ControlFiles(new Home(directory));
        AtomicInteger reloads = new AtomicInteger();
        ReloadWatcher watcher = new ReloadWatcher(control, reloads::incrementAndGet);
        watcher.start();
        try {
            control.touch();
            assertTrue(awaitAtLeastOne(reloads), "watcher ran the reload action after a touch");
        } finally {
            watcher.stop();
        }
    }

    @Test
    void leavesTheActionUnrunWhileTheTriggerIsUnchanged(@TempDir Path directory) {
        ControlFiles control = new ControlFiles(new Home(directory));
        AtomicInteger reloads = new AtomicInteger();
        ReloadWatcher watcher = new ReloadWatcher(control, reloads::incrementAndGet);
        watcher.start();
        try {
            waitForSeveralPolls();
            assertEquals(0, reloads.get(), "an unchanged trigger is polled without ever running the action");
        } finally {
            watcher.stop();
        }
    }

    private static void waitForSeveralPolls() {
        int attempt = 0;
        while (attempt < POLLS_TO_OBSERVE) {
            sleep();
            attempt++;
        }
    }

    private static boolean awaitAtLeastOne(AtomicInteger reloads) {
        int attempt = 0;
        while (attempt < POLLS && reloads.get() == 0) {
            sleep();
            attempt++;
        }
        return reloads.get() > 0;
    }

    private static void sleep() {
        try {
            Thread.sleep(PAUSE_MILLIS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
