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

    private static final String RED = "\033[1;31m";
    private static final String RESET = "\033[0m";
    private static final String BAR = "============================================================";
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

    private void warn(List<Path> missing) {
        this.out.line(RED + BAR);
        this.out.line("  WARNING: " + missing.size() + " persistent configuration file(s) cannot be read.");
        this.out.line("  Their rules will NOT be applied to the daemon:");
        missing.forEach(path -> this.out.line("    " + path));
        this.out.line("  Fix the path, or remove it with 'aroxy config detach <f>'.");
        this.out.line(BAR + RESET);
    }
}
