package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.ControlFiles;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.RuntimeSnapshot;
import lombok.experimental.UtilityClass;

/**
 * Asks a running daemon to reload by touching the {@link ControlFiles} trigger, then waits, with a
 * short bounded backoff, for the daemon to bump the {@code generation} in its {@link RuntimeSnapshot}.
 * The wait keeps {@code reload} and {@code attach} synchronous, as the former admin {@code POST}
 * responses were: a confirmed return means the new rules are live, not merely requested.
 */
@UtilityClass
final class ReloadRequest {

    private static final int ATTEMPTS = 50;
    private static final long PAUSE_MILLIS = 100L;
    private static final long MISSING = -1L;

    static boolean send(Home home) {
        long before = generationOf(home);
        new ControlFiles(home).touch();
        return awaitAdvance(home, before);
    }

    private static boolean awaitAdvance(Home home, long before) {
        int attempt = 0;
        while (attempt < ATTEMPTS && generationOf(home) <= before) {
            sleep();
            attempt++;
        }
        return generationOf(home) > before;
    }

    private static long generationOf(Home home) {
        return RuntimeSnapshot.read(home.runtimeFile()).map(RuntimeSnapshot::generation).orElse(MISSING);
    }

    private static void sleep() {
        try {
            Thread.sleep(PAUSE_MILLIS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
