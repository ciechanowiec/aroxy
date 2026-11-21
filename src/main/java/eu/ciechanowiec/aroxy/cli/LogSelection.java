package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.daemon.Home;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import picocli.CommandLine.Option;

/**
 * The mutually exclusive log source flags for {@code aroxy logs}. {@code --audit} (the implicit
 * default) and {@code --app} select a single appendable file that can be dumped or followed.
 * {@code --wire} lists the redacted/raw wire dumps. {@code --all} dumps the audit and app logs
 * together. Following only applies to a single file, so {@code --all} and {@code --wire} ignore it.
 */
final class LogSelection {

    @Option(
        names = "--audit", description = "The obfuscation audit log (default)."
    )
    @Justification(
        "a CLI default flag injected by picocli via reflection, with behavior falling through to AUDIT"
    )
    @SuppressWarnings(
        "UnusedVariable"
    )
    private boolean audit;

    @Option(
        names = "--app", description = "The daemon diagnostic log."
    )
    @Justification(
        "picocli injects this field via reflection"
    )
    @SuppressWarnings(
        "UnusedVariable"
    )
    private boolean app;

    @Option(
        names = "--wire", description = "List the wire dump files."
    )
    @Justification(
        "picocli injects this field via reflection"
    )
    @SuppressWarnings(
        "UnusedVariable"
    )
    private boolean wire;

    @Option(
        names = "--all", description = "Dump the audit and app logs together."
    )
    @Justification(
        "picocli injects this field via reflection"
    )
    @SuppressWarnings(
        "UnusedVariable"
    )
    private boolean all;

    @Justification(
        "CLI command returns a fixed exit code by contract"
    )
    @SuppressWarnings(
        "SameReturnValue"
    )
    int print(Home home, LogPrinter printer, boolean follow) throws InterruptedException {
        if (this.wire) {
            this.listWire(home, printer);
        } else if (this.all) {
            printer.dump(LogTarget.AUDIT.file(home));
            printer.dump(LogTarget.APP.file(home));
        } else {
            this.single(home, printer, follow);
        }
        return 0;
    }

    private void single(Home home, LogPrinter printer, boolean follow) throws InterruptedException {
        Path file = (this.app ? LogTarget.APP : LogTarget.AUDIT).file(home);
        if (follow) {
            printer.follow(file);
        } else {
            printer.dump(file);
        }
    }

    private void listWire(Home home, LogPrinter printer) {
        Path directory = home.wireDirectory();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.map(Path::toString).sorted().forEach(printer::line);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list wire dumps in " + directory, exception);
        }
    }
}
