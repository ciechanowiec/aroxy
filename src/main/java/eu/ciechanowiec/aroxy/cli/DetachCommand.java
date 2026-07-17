package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.config.AttachmentRegistry;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.PidFile;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code aroxy config detach <f>}: removes a file from the persistent registry. Detaching is only
 * allowed while the daemon is stopped - the live rule set must not change underneath a running
 * proxy - so this refuses with a clear message and a non-zero exit when a daemon is running.
 */
@Command(
    name = "detach", description = "Detach a configuration file (only while stopped)."
)
final class DetachCommand implements Callable<Integer> {

    private static final int REFUSED = 1;

    // picocli injects this field via reflection
    @Parameters(
        index = "0", paramLabel = "<f>", description = "Configuration file to detach."
    )
    @SuppressWarnings(
        {"UnusedDeclaration", "UnusedVariable", "InstanceVariableMayNotBeInitialized"}
    )
    private Path file;

    private final CliOutput out;

    DetachCommand() {
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() {
        Home home = Home.resolve();
        if (new PidFile(home.pidFile()).isRunning()) {
            this.out.line("Refusing to detach while the daemon is running; stop it first.");
            return REFUSED;
        }
        new AttachmentRegistry(home.attachments()).detach(this.file);
        this.out.line("Detached " + this.file.toAbsolutePath());
        return 0;
    }
}
