package eu.ciechanowiec.aroxy.cli;

import java.util.Optional;
import picocli.AutoComplete.GenerateCompletion;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Entry point and root command of the {@code aroxy} tool ("Anthropic Proxy"). It wires the lifecycle
 * subcommands ({@code start}, {@code stop}, {@code restart}, {@code status}, {@code config},
 * {@code reload}, {@code logs}), the hidden background daemon entry, and picocli's
 * {@code generate-completion} so {@code aroxy generate-completion} prints a bash/zsh completion
 * script covering the whole command and option tree.
 */
@Command(
    name = "aroxy",
    mixinStandardHelpOptions = true,
    versionProvider = Version.class,
    description = "Anthropic Proxy: a local PII/secret-redacting gateway for Claude Code.",
    subcommands = {
        StartCommand.class,
        StopCommand.class,
        RestartCommand.class,
        StatusCommand.class,
        ConfigCommand.class,
        ReloadCommand.class,
        LogsCommand.class,
        DaemonEntry.class,
        GenerateCompletion.class
    }
)
public final class Aroxy implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    static int execute(String... args) {
        return commandLine().execute(args);
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new Aroxy());
        Optional.ofNullable(commandLine.getSubcommands().get("generate-completion"))
            .ifPresent(sub -> sub.getCommandSpec().usageMessage().hidden(true));
        return commandLine;
    }

    @SuppressWarnings(
        "PMD.DoNotTerminateVM"
    ) // CLI entry point: propagate picocli's exit code to the process
    static void main(String... args) {
        int exitCode = execute(args);
        System.exit(exitCode);
    }
}
