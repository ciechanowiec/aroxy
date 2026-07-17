package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.PidFile;
import lombok.experimental.UtilityClass;

/**
 * Waits for a stopping daemon's process to actually exit, polling its pid liveness with a short
 * backoff and a bounded number of attempts so {@code restart} never starts a second daemon while the
 * first is still bound to the proxy port.
 */
@UtilityClass
final class DaemonWait {

    private static final int ATTEMPTS = 50;
    private static final long PAUSE_MILLIS = 100L;

    // bounded liveness poll: the sleep is intentional
    @SuppressWarnings(
        "BusyWait"
    )
    static void untilStopped(PidFile pid) throws InterruptedException {
        int attempt = 0;
        while (attempt < ATTEMPTS && pid.isRunning()) {
            Thread.sleep(PAUSE_MILLIS);
            attempt++;
        }
    }
}
