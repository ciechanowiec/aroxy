package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.AttachmentKind;
import eu.ciechanowiec.aroxy.daemon.AttachmentListing;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.OfflineConfig;
import eu.ciechanowiec.aroxy.daemon.PidFile;
import eu.ciechanowiec.aroxy.daemon.RuntimeSnapshot;
import eu.ciechanowiec.governance.Justification;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * {@code aroxy config list}: lists the contributing configuration files, labelling each as the
 * bundled default, persistent, or temporary. The always-present {@code default.conf} is listed first.
 * When the daemon is running it reads the published runtime snapshot (default plus persistent registry
 * entries plus session temporaries, each with its load time). When stopped it reads the registry
 * directly, where the default and every entry are shown as not yet loaded.
 */
@Command(
    name = "list", description = "List the persistent and temporary configuration files."
)
final class ListCommand implements Callable<Integer> {

    private final CliOutput out;

    ListCommand() {
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() {
        Home home = Home.resolve();
        return new PidFile(home.pidFile()).isRunning() ? this.live(home) : this.offline(home);
    }

    private Integer live(Home home) {
        Optional<RuntimeSnapshot> snapshot = RuntimeSnapshot.read(home.runtimeFile());
        if (snapshot.isEmpty()) {
            return this.offline(home);
        }
        this.out.heading("Configuration files");
        this.out.line(AttachmentListing.render(snapshot.orElseThrow().attachments()));
        return 0;
    }

    @Justification(
        "CLI command returns a fixed exit code by contract"
    )
    @SuppressWarnings(
        "SameReturnValue"
    )
    private Integer offline(Home home) {
        this.out.heading("Configuration files");
        this.out.line(notLoaded(AttachmentKind.DEFAULT, home.defaultConfig().toString()));
        new OfflineConfig(home).attachments()
            .forEach(path -> this.out.line(notLoaded(AttachmentKind.PERSISTENT, path)));
        return 0;
    }

    private static String notLoaded(AttachmentKind kind, String path) {
        return "  " + kind.label() + "  not loaded  " + path;
    }
}
