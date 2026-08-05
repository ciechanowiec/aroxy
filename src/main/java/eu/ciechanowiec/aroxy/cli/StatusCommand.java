package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.PidFile;
import eu.ciechanowiec.aroxy.daemon.RuntimeSnapshot;
import eu.ciechanowiec.aroxy.daemon.StatusReport;
import eu.ciechanowiec.aroxy.daemon.StatusView;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * {@code aroxy status}: when the daemon is running, reads its published runtime snapshot and prints
 * the state (listen address, upstream, rule count, uptime, attachments). When no daemon is running it
 * reports that and exits non-zero so scripts can branch on it.
 */
@Command(
    name = "status", description = "Show whether the daemon is running and its state."
)
final class StatusCommand implements Callable<Integer> {

    private static final int NOT_RUNNING = 1;
    private final CliOutput out;

    StatusCommand() {
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() {
        Home home = Home.resolve();
        return new PidFile(home.pidFile()).isRunning() ? this.report(home) : this.absent();
    }

    private Integer report(Home home) {
        Optional<StatusReport> report = RuntimeSnapshot.read(home.runtimeFile())
            .map(snapshot -> snapshot.toStatusReport(System.currentTimeMillis()));
        if (report.isEmpty()) {
            return this.absent();
        }
        this.out.info("Daemon running");
        this.out.line(StatusView.render(report.orElseThrow()));
        return 0;
    }

    @Justification(
        "CLI command returns a fixed exit code by contract"
    )
    @SuppressWarnings(
        "SameReturnValue"
    )
    private Integer absent() {
        this.out.inactive("Daemon not running");
        return NOT_RUNNING;
    }
}
