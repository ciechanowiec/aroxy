package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.Launcher;
import eu.ciechanowiec.aroxy.daemon.PidFile;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * {@code aroxy restart}: signals a running daemon (if any) to shut down, waits for its process to
 * exit so the proxy port is free, then starts a fresh detached daemon. In-session temporary
 * attachments are not carried across, by design.
 */
@Command(
    name = "restart", description = "Restart the aroxy daemon."
)
final class RestartCommand implements Callable<Integer> {

    private static final int STILL_RUNNING = 1;
    private final CliOutput out;

    RestartCommand() {
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() throws InterruptedException {
        Home home = Home.resolve();
        PidFile pid = new PidFile(home.pidFile());
        pid.liveHandle().ifPresent(ProcessHandle::destroy);
        DaemonWait.untilStopped(pid);
        return this.relaunch(home, pid);
    }

    private Integer relaunch(Home home, PidFile pid) {
        if (pid.isRunning()) {
            this.out.line("aroxy daemon did not stop; not restarting.");
            return STILL_RUNNING;
        }
        pid.clear();
        new Launcher(home).start();
        this.out.line("aroxy daemon restarted.");
        return 0;
    }
}
