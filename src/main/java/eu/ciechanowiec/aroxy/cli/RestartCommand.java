package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.DaemonJar;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.Launcher;
import eu.ciechanowiec.aroxy.daemon.Launcher.Outcome;
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

    private Integer relaunch(Home home, PidFile pid) throws InterruptedException {
        if (pid.isRunning()) {
            this.out.error("Daemon did not stop");
            this.out.detail("Action", "Restart cancelled");
            return STILL_RUNNING;
        }
        pid.clear();
        Outcome outcome = new Launcher(home).start();
        DaemonOutput daemon = new DaemonOutput(this.out);
        return switch (outcome) {
            case STARTED -> finish(() -> daemon.ready(home, "restarted"), 0);
            case ALREADY_RUNNING -> finish(() -> daemon.alreadyRunning(home, new DaemonJar(home).isStale()), 0);
            case FAILED -> finish(() -> daemon.failed(home, "restart"), 1);
        };
    }

    private static Integer finish(Runnable output, int exit) {
        output.run();
        return exit;
    }
}
