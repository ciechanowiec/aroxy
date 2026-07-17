package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.Launcher;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * {@code aroxy start}: launches the daemon. It spawns a detached background JVM via the
 * {@link Launcher} and returns immediately. Configuration files are attached only through
 * {@code aroxy config attach}, never at start.
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
    public Integer call() {
        Home home = Home.resolve();
        new MissingAttachmentsWarning(this.out).check(home);
        boolean started = new Launcher(home).start();
        this.out.line(started ? "aroxy daemon started." : "aroxy daemon is already running.");
        return 0;
    }
}
