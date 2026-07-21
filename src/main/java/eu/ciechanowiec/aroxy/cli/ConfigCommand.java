package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.ControlFiles;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.OfflineConfig;
import eu.ciechanowiec.aroxy.daemon.PidFile;
import eu.ciechanowiec.aroxy.daemon.RuntimeSnapshot;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * {@code aroxy config}: while the daemon is running, prints the configuration published by that
 * process and therefore actually in force. While it is stopped, computes and clearly labels the
 * configuration that the next start would load from disk. It is also the parent of {@code attach},
 * {@code detach} and {@code list}.
 */
@Command(
    name = "config",
    description = "Show the active or next-start configuration, or manage configuration files.",
    subcommands = {AttachCommand.class, DetachCommand.class, ListCommand.class}
)
final class ConfigCommand implements Callable<Integer> {

    private final CliOutput out;

    ConfigCommand() {
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() {
        Home home = Home.resolve();
        if (new PidFile(home.pidFile()).isRunning()) {
            return this.active(home);
        }
        List<Path> temporaries = new ControlFiles(home).readTemporaries();
        this.out.heading("Configuration for next start");
        this.out.line(new OfflineConfig(home).render(temporaries));
        return 0;
    }

    private Integer active(Home home) {
        Optional<String> summary = RuntimeSnapshot.read(home.runtimeFile()).map(RuntimeSnapshot::renderConfig);
        if (summary.isEmpty()) {
            this.detailsUnavailable();
            return 1;
        }
        this.showActive(summary.orElseThrow());
        return 0;
    }

    private void showActive(String summary) {
        this.out.heading("Active configuration");
        this.out.line(summary);
    }

    private void detailsUnavailable() {
        this.out.warning("Active configuration details unavailable");
        this.out.note("Restart the daemon so it can publish its live configuration.");
    }
}
