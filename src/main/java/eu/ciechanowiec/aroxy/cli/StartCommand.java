package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.DaemonJar;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.Launcher;
import eu.ciechanowiec.aroxy.daemon.Launcher.Outcome;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * {@code aroxy start}: launches the daemon. It spawns a detached background JVM via the
 * {@link Launcher} and waits until every listener is ready or the bounded launch attempt fails.
 * Configuration files are attached only through {@code aroxy config attach}, never at start.
 */
@Command(
    name = "start", description = "Start the aroxy daemon."
)
final class StartCommand implements Callable<Integer> {

    private final CliOutput out;

    StartCommand() {
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() throws InterruptedException {
        Home home = Home.resolve();
        new MissingAttachmentsWarning(this.out).check(home);
        Outcome outcome = new Launcher(home).start();
        DaemonOutput daemon = new DaemonOutput(this.out);
        return switch (outcome) {
            case STARTED -> finish(() -> daemon.ready(home, "started"), 0);
            case ALREADY_RUNNING -> finish(() -> daemon.alreadyRunning(home, new DaemonJar(home).isStale()), 0);
            case FAILED -> finish(() -> daemon.failed(home, "start"), 1);
        };
    }

    private static Integer finish(Runnable output, int exit) {
        output.run();
        return exit;
    }
}
