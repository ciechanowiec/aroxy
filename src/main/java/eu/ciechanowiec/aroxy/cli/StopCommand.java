package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.PidFile;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * {@code aroxy stop}: signals the running daemon to shut down (a plain process terminate, which the
 * daemon's shutdown hook turns into a clean teardown), waits for its process to actually exit, then
 * clears the pid file. The wait makes the command synchronous, so a caller (a script, a reinstall, an
 * uninstall) knows the daemon is truly gone and the proxy port is free once it returns. If no daemon
 * is running it reports that and still clears any stale pid file.
 */
@Command(
    name = "stop", description = "Stop the aroxy daemon."
)
final class StopCommand implements Callable<Integer> {

    private static final int STILL_RUNNING = 1;
    private final CliOutput out;

    StopCommand() {
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() throws InterruptedException {
        Home home = Home.resolve();
        PidFile pid = new PidFile(home.pidFile());
        Optional<ProcessHandle> handle = pid.liveHandle();
        handle.ifPresent(ProcessHandle::destroy);
        DaemonWait.untilStopped(pid);
        return this.finish(pid, handle.isPresent());
    }

    private Integer finish(PidFile pid, boolean running) {
        if (pid.isRunning()) {
            this.out.warning("Daemon is still shutting down");
            this.out.note("The PID file was left in place; run 'aroxy status' to check again.");
            return STILL_RUNNING;
        }
        pid.clear();
        if (running) {
            this.out.success("Daemon stopped");
        } else {
            this.out.inactive("Daemon already stopped");
        }
        return 0;
    }
}
