package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.governance.Justification;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code aroxy logs}: tails a log file under {@link Home}{@code /logs}. The mutually exclusive
 * {@code --audit} (default), {@code --app}, {@code --wire} and {@code --all} flags choose the source.
 * {@code -f}/{@code --follow} keeps streaming appended lines. The audit log is the default because it
 * is the obfuscation proof artifact users most often want to watch.
 */
@Command(
    name = "logs", description = "Show or follow the aroxy logs."
)
final class LogsCommand implements Callable<Integer> {

    @Option(
        names = {"-f", "--follow"}, description = "Keep streaming new lines (Ctrl-C to stop)."
    )
    @Justification(
        "picocli injects this field via reflection"
    )
    @SuppressWarnings(
        {"UnusedDeclaration", "UnusedVariable", "InstanceVariableMayNotBeInitialized"}
    )
    private boolean follow;

    @ArgGroup
    @Justification(
        "populated by picocli via reflection"
    )
    @SuppressWarnings(
        {"PMD.ImmutableField", "CanBeFinal", "FieldMayBeFinal"}
    )
    private LogSelection selection;

    private final CliOutput out;

    LogsCommand() {
        this.selection = new LogSelection();
        this.out = CliOutput.standard();
    }

    @Override
    public Integer call() throws InterruptedException {
        Home home = Home.resolve();
        LogPrinter printer = new LogPrinter(this.out);
        return this.selection.print(home, printer, this.follow);
    }
}
