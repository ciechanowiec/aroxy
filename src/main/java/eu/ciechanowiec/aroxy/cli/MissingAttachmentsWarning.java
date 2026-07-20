package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.config.AttachmentRegistry;
import eu.ciechanowiec.aroxy.daemon.Home;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Prints a prominent red warning at {@code aroxy start} when a persistent attachment recorded in the
 * registry can no longer be read, so the user notices that those rules will be absent from the
 * starting daemon. A readable registry produces no output at all.
 */
final class MissingAttachmentsWarning {

    private final CliOutput out;

    MissingAttachmentsWarning(CliOutput out) {
        this.out = out;
    }

    void check(Home home) {
        List<Path> missing = new AttachmentRegistry(home.attachments()).list().stream()
            .filter(path -> !Files.isReadable(path)).toList();
        if (!missing.isEmpty()) {
            this.warn(missing);
        }
    }

    private void warn(Iterable<Path> missing) {
        this.out.warning("Some persistent configuration files are unavailable");
        missing.forEach(path -> this.out.detail("Missing", path));
        this.out.note("Their rules will not be loaded.");
        this.out.note("Fix each path or run 'aroxy config detach <f>'.");
    }
}
