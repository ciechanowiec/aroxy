package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.daemon.PidFile;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The restart wait polls a pid for liveness with a bounded backoff.
 * An already-stopped daemon returns at once, while a process that never stops exhausts the attempts
 * and returns rather than blocking forever.
 */
class DaemonWaitTest {

    @Test
    @SneakyThrows
    void returnsAtOnceWhenNoProcessIsRecorded(@TempDir Path directory) {
        PidFile pid = new PidFile(directory.resolve("aroxy.pid"));
        DaemonWait.untilStopped(pid);
        assertFalse(pid.isRunning(), "an absent pid is already stopped, so the wait returns immediately");
    }

    @Test
    @SneakyThrows
    void returnsAfterExhaustingAttemptsForALiveProcess(@TempDir Path directory) {
        PidFile pid = new PidFile(directory.resolve("aroxy.pid"));
        pid.write(ProcessHandle.current().pid());
        DaemonWait.untilStopped(pid);
        assertTrue(pid.isRunning(), "a process that never stops exhausts the bounded attempts and still returns");
    }
}
