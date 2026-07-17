package eu.ciechanowiec.aroxy.daemon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts and stops the background daemon. {@link #start()} spawns a detached JVM that re-invokes this
 * jar's hidden daemon entry, redirecting its stdout and stderr to {@link Home}{@code /logs}, then records the
 * child pid without waiting for it. It is idempotent: if a live daemon is already recorded it does
 * nothing. {@link #buildCommand()} is exposed so tests can assert the launch command without
 * spawning a real process.
 */
@Slf4j
public final class Launcher {

    private final Home home;
    private final PidFile pidFile;

    /**
     * Creates a launcher over the given state directory.
     *
     * @param home the state directory layout the daemon is launched against
     */
    public Launcher(Home home) {
        this.home = home;
        this.pidFile = new PidFile(home.pidFile());
    }

    /**
     * Spawns the detached daemon JVM unless one is already running; returns true if it started one.
     *
     * @return {@code true} if a daemon was started, {@code false} if one was already running
     */
    public boolean start() {
        if (this.pidFile.isRunning()) {
            log.atInfo().addArgument(() -> this.pidFile.read().orElse(-1L)).log(
                "aroxy daemon already running (pid {})"
            );
            return false;
        }
        Process process = this.spawn();
        this.pidFile.write(process.pid());
        return true;
    }

    /**
     * The exact command used to launch the detached daemon JVM.
     */
    List<String> buildCommand() {
        List<String> command = new ArrayList<>();
        command.add(LaunchCommand.java());
        command.addAll(DaemonProcess.loggingFlags(this.home));
        command.addAll(List.of("-jar", LaunchCommand.jar(), DaemonProcess.NAME));
        return command;
    }

    private Process spawn() {
        ProcessBuilder builder = new ProcessBuilder(this.buildCommand());
        builder.redirectOutput(Redirect.appendTo(this.home.stdoutLog().toFile()));
        builder.redirectError(Redirect.appendTo(this.home.stderrLog().toFile()));
        builder.environment().put("AROXY_HOME", this.home.base().toString());
        return this.launch(builder);
    }

    private Process launch(ProcessBuilder builder) {
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not spawn aroxy daemon", exception);
        }
    }
}
