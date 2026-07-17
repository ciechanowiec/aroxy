package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.PidFile;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * {@code aroxy reload}: when the daemon is running, touches the control-file reload trigger and waits
 * for the daemon to apply the new effective configuration (default file plus persistent registry plus
 * session temporaries) before reporting success. Reports clearly when no daemon is running.
 */
@Command(
    name = "reload", description = "Reload the running daemon's configuration."
)
final class ReloadCommand implements Callable<Integer> {

    private static final int NOT_RUNNING = 1;
    private static final int UNCONFIRMED = 1;
    private final CliOutput out;

    ReloadCommand() {
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() {
        Home home = Home.resolve();
        return new PidFile(home.pidFile()).isRunning() ? this.request(home) : this.absent();
    }

    private Integer request(Home home) {
        boolean confirmed = ReloadRequest.send(home);
        this.out.line(
            confirmed
                ? "aroxy configuration reloaded."
                : "aroxy reload requested but the daemon did not confirm it; check the logs."
        );
        return confirmed ? 0 : UNCONFIRMED;
    }

    // CLI command returns a fixed exit code by contract
    @SuppressWarnings(
        "SameReturnValue"
    )
    private Integer absent() {
        this.out.line("aroxy daemon is not running.");
        return NOT_RUNNING;
    }
}
