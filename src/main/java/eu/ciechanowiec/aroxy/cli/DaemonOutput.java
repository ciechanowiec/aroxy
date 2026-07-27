package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.GatewayAddress;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.PidFile;
import eu.ciechanowiec.aroxy.daemon.RuntimeSnapshot;

/**
 * Presents daemon lifecycle outcomes as a compact status block shared by start and restart.
 */
final class DaemonOutput {

    private static final String SCHEME = "http://";
    private final CliOutput out;

    DaemonOutput(CliOutput out) {
        this.out = out;
    }

    void ready(Home home, String action) {
        this.out.success("Daemon " + action);
        this.details(home);
    }

    /**
     * Reports a daemon that was already up. A running daemon executes the snapshot it staged at launch,
     * so an install that lands afterwards does not reach it. Saying so is the difference between
     * "already running" meaning "you are covered" and it quietly meaning "you are on the previous
     * build". Whether that is the case is decided by the caller, which knows where the installed jar
     * is, leaving this class with only the rendering.
     *
     * @param home       the state directory to read the running details from
     * @param staleBuild whether the running daemon predates the installed jar
     */
    void alreadyRunning(Home home, boolean staleBuild) {
        this.out.info("Daemon already running");
        this.details(home);
        if (staleBuild) {
            this.out.detail("Build", "older than the installed jar; restart to pick it up");
        }
    }

    void failed(Home home, String action) {
        this.out.error("Daemon failed to " + action);
        this.out.detail("State", "No proxy is running");
        this.out.detail("Log", home.stderrLog());
    }

    private void details(Home home) {
        new PidFile(home.pidFile()).read().ifPresent(pid -> this.out.detail("PID", pid));
        RuntimeSnapshot.read(home.runtimeFile()).ifPresent(
            snapshot -> snapshot.gateways().forEach(this::gateway)
        );
    }

    private void gateway(GatewayAddress gateway) {
        this.out.detail(gateway.client(), endpoint(gateway.listen()));
    }

    private static String endpoint(String listen) {
        return listen.contains("://") ? listen : SCHEME + listen;
    }
}
