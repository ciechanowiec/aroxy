package eu.ciechanowiec.aroxy.daemon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Starts and stops the background daemon. {@link #start()} spawns a detached JVM that re-invokes this
 * jar's hidden daemon entry and redirects its stdout and stderr to {@link Home}{@code /logs}. It does
 * not report success merely because the child was spawned: it waits until that same launch publishes
 * a fresh {@link RuntimeSnapshot}, which happens only after every proxy listener has bound. Launch
 * attempts are serialized across CLI processes. A child that exits, times out, or cannot be recorded
 * is terminated and its stale control files are cleared. It is idempotent: if a live daemon is
 * already recorded it does nothing. {@link #buildCommand()} is exposed so tests can assert the launch
 * command without spawning a real process.
 */
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
     * Spawns the detached daemon JVM unless one is already running, and waits for listener readiness.
     *
     * @return the outcome of the bounded launch attempt
     * @throws InterruptedException if interrupted while waiting for readiness or child termination
     */
    public Outcome start() throws InterruptedException {
        try (
            FileChannel channel = FileChannel.open(
                this.home.launchLock(), StandardOpenOption.CREATE, StandardOpenOption.WRITE
            );
            FileLock ignored = channel.lock()
        ) {
            return this.startLocked();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not lock aroxy daemon launch", exception);
        }
    }

    private Outcome startLocked() throws InterruptedException {
        if (!this.pidFile.isRunning()) {
            return this.launchAndWait();
        }
        return Outcome.ALREADY_RUNNING;
    }

    private Outcome launchAndWait() throws InterruptedException {
        this.clearRuntime();
        long launchedAt = System.currentTimeMillis();
        Process process = this.spawn();
        boolean ready = false;
        try {
            this.pidFile.write(process.pid());
            ready = StartupWait.untilReady(
                this.pidFile, this.home.runtimeFile(), launchedAt, process.pid()
            );
            if (ready) {
                return Outcome.STARTED;
            }
            terminate(process);
            return Outcome.FAILED;
        } finally {
            if (!ready) {
                this.cleanupFailedLaunch(process);
            }
        }
    }

    private void cleanupFailedLaunch(Process process) {
        process.destroyForcibly();
        try {
            this.pidFile.clear();
        } finally {
            this.clearRuntime();
        }
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

    private void clearRuntime() {
        try {
            Files.deleteIfExists(this.home.runtimeFile());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not clear stale runtime snapshot", exception);
        }
    }

    private static void terminate(Process process) throws InterruptedException {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        if (!process.waitFor(Duration.of(StartupWait.TIMEOUT_SECONDS, TimeUnit.SECONDS.toChronoUnit()))) {
            process.destroyForcibly();
            process.waitFor();
        }
    }

    /**
     * The observable result of a daemon launch request.
     */
    public enum Outcome {

        /**
         * Every configured listener bound and the daemon published its runtime state.
         */
        STARTED,

        /**
         * The recorded daemon was already alive, so no child was spawned.
         */
        ALREADY_RUNNING,

        /**
         * The child exited or timed out before every listener became ready.
         */
        FAILED
    }
}
