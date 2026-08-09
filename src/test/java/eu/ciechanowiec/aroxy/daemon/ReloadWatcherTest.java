package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the reload watcher runs its action when, and only when, the control-file trigger's
 * stamp changes, using a real scheduled poll over a real temporary directory.
 */
class ReloadWatcherTest {

    private static final int POLLS = 60;
    private static final int POLLS_TO_OBSERVE = 16;
    private static final Duration PAUSE = Duration.ofMillis(50);

    @Test
    @SneakyThrows
    void runsTheActionWhenTheTriggerChanges(@TempDir Path directory) {
        ControlFiles control = new ControlFiles(new Home(directory));
        AtomicInteger reloads = new AtomicInteger();
        CountDownLatch reloaded = new CountDownLatch(1);
        ReloadWatcher watcher = new ReloadWatcher(
            control,
            () -> {
                reloads.incrementAndGet();
                reloaded.countDown();
            }
        );
        watcher.start();
        try {
            control.touch();
            assertTrue(
                reloaded.await(POLLS * PAUSE.toMillis(), TimeUnit.MILLISECONDS),
                "watcher ran the reload action after a touch"
            );
            assertEquals(1, reloads.get(), "one trigger change runs one reload action");
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

    @Test
    void workerThreadCannotKeepTheProcessAlive() {
        assertTrue(ReloadWatcher.thread(Thread::onSpinWait).isDaemon(), "the watcher uses a daemon thread");
    }

    private static void waitForSeveralPolls() {
        int attempt = 0;
        while (attempt < POLLS_TO_OBSERVE) {
            LockSupport.parkNanos(PAUSE.toNanos());
            attempt++;
        }
    }
}
