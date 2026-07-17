package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.Daemon;
import eu.ciechanowiec.aroxy.daemon.DaemonProcess;
import eu.ciechanowiec.aroxy.daemon.Home;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * The hidden daemon entry point re-invoked by the {@code Launcher} in the detached background JVM. It
 * starts the {@link Daemon}, registers a shutdown hook so an external {@code stop} or a JVM signal
 * tears the servers down cleanly, and then blocks until the admin {@code /shutdown} endpoint releases
 * it. Diagnostics are routed to the rolling {@code aroxy.log} by the {@code -Dtinylog.*} flags the
 * launcher passes, so this entry needs no logging wiring. It is never shown in help output.
 */
@Command(
    name = DaemonProcess.NAME, hidden = true, description = "Internal: run the daemon process."
)
final class DaemonEntry implements Callable<Integer> {

    @Override
    public Integer call() throws IOException, InterruptedException {
        Home home = Home.resolve();
        Daemon daemon = new Daemon(home);
        Runtime.getRuntime().addShutdownHook(new Thread(daemon::stop));
        daemon.run();
        daemon.awaitStop();
        return 0;
    }
}
