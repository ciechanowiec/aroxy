package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.config.AttachmentRegistry;
import eu.ciechanowiec.aroxy.config.ConfigFile;
import eu.ciechanowiec.aroxy.config.InvalidConfigException;
import eu.ciechanowiec.aroxy.daemon.ControlFiles;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.PidFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code aroxy config attach <f>}: attaches a configuration file. {@code --persistent} (the default)
 * records it in the registry so it survives restarts. {@code --temporary} records it in the session
 * temporaries file for this session only. While the daemon runs, the attach also touches the reload
 * trigger and waits for the live rules to swap. While stopped, a persistent attach edits the registry
 * directly. A temporary attach needs a running daemon and is reported as such.
 */
@Command(
    name = "attach", description = "Attach a configuration file."
)
final class AttachCommand implements Callable<Integer> {

    private static final int FAILED = 1;
    private final CliOutput out;
    @Parameters(
        index = "0", paramLabel = "<f>", description = "Configuration file to attach."
    )
    @Justification(
        "picocli injects this field via reflection"
    )
    @SuppressWarnings(
        {"UnusedDeclaration", "UnusedVariable", "InstanceVariableMayNotBeInitialized"}
    )
    private Path file;
    @Option(
        names = "--temporary", description = "Attach for this session only (requires a running daemon)."
    )
    @Justification(
        "picocli injects this field via reflection"
    )
    @SuppressWarnings(
        {"UnusedDeclaration", "UnusedVariable", "InstanceVariableMayNotBeInitialized"}
    )
    private boolean temporary;
    @Option(
        names = "--persistent", description = "Record in the persistent registry (default)."
    )
    @Justification(
        "a CLI default flag injected by picocli via reflection, with behavior keying off !temporary"
    )
    @SuppressWarnings(
        {"UnusedDeclaration", "UnusedVariable", "InstanceVariableMayNotBeInitialized"}
    )
    private boolean persistent;

    AttachCommand() {
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() {
        return this.rejection().orElseGet(this::attachValidated);
    }

    private Optional<Integer> rejection() {
        if (Files.isSymbolicLink(this.file)) {
            this.out.error("Cannot attach configuration");
            this.out.detail("Reason", "Symbolic links are not accepted");
            this.out.detail("File", this.file);
            return Optional.of(FAILED);
        }
        return this.validate();
    }

    private Optional<Integer> validate() {
        try {
            ConfigFile.read(this.file);
            return Optional.empty();
        } catch (InvalidConfigException exception) {
            this.out.error("Cannot attach configuration");
            this.out.note(exception.getMessage());
            return Optional.of(FAILED);
        }
    }

    private Integer attachValidated() {
        Home home = Home.resolve();
        boolean keep = !this.temporary;
        return new PidFile(home.pidFile()).isRunning() ? this.online(home, keep) : this.offline(home, keep);
    }

    private Integer online(Home home, boolean keep) {
        this.store(home, keep);
        return ReloadRequest.send(home) ? this.attached() : this.unconfirmed();
    }

    @Justification(
        "CLI command returns a fixed exit code by contract"
    )
    @SuppressWarnings(
        "SameReturnValue"
    )
    private Integer unconfirmed() {
        this.out.warning("Configuration attached, but reload was not confirmed");
        this.out.detail("File", this.file.toAbsolutePath());
        this.out.note("Run 'aroxy logs' to inspect the daemon.");
        return FAILED;
    }

    private void store(Home home, boolean keep) {
        if (keep) {
            new AttachmentRegistry(home.attachments()).attach(this.file);
        } else {
            new ControlFiles(home).appendTemporary(this.file);
        }
    }

    private Integer offline(Home home, boolean keep) {
        if (keep) {
            new AttachmentRegistry(home.attachments()).attach(this.file);
            return this.attached();
        }
        this.out.error("Cannot attach temporary configuration");
        this.out.detail("Reason", "Daemon is not running");
        return FAILED;
    }

    @Justification(
        "CLI command returns a fixed exit code by contract"
    )
    @SuppressWarnings(
        "SameReturnValue"
    )
    private Integer attached() {
        this.out.success("Configuration attached");
        this.out.detail("File", this.file.toAbsolutePath());
        this.out.detail("Scope", this.temporary ? "Temporary" : "Persistent");
        return 0;
    }
}
