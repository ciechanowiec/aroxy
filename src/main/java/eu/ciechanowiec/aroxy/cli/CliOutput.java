package eu.ciechanowiec.aroxy.cli;

import java.io.PrintStream;

/**
 * The CLI's user-facing output stream. Centralizing it keeps the subcommands free of direct
 * {@link System#out} references (so static analysis sees a single sink) and lets every command print
 * a line the same way.
 */
final class CliOutput {

    private final PrintStream out;

    CliOutput(PrintStream out) {
        this.out = out;
    }

    static CliOutput standard() {
        return new CliOutput(System.out);
    }

    void line(String text) {
        this.out.println(text);
    }
}
