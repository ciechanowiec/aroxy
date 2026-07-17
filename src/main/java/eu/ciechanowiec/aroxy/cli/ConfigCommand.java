package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.ControlFiles;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.OfflineConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * {@code aroxy config}: prints the effective configuration, computed from the on-disk default file, the persistent
 * registry, and the session temporaries file. While the daemon is stopped the temporaries file is absent, so the
 * summary reflects exactly what the next start would load. It is also the parent of {@code attach}, {@code detach} and
 * {@code list}.
 */
@Command(
    name = "config",
    description = "Show the effective configuration, or manage configuration files.",
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
        List<Path> temporaries = new ControlFiles(home).readTemporaries();
        this.out.line(new OfflineConfig(home).render(temporaries));
        return 0;
    }
}
