package eu.ciechanowiec.aroxy.cli;

import java.io.PrintStream;
import picocli.CommandLine.Help.Ansi;

/**
 * The CLI's user-facing output stream. Centralizing it keeps the subcommands free of direct
 * {@link System#out} references (so static analysis sees a single sink) and lets every command print
 * a line the same way.
 */
final class CliOutput {

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String GREEN = "\033[32m";
    private static final String CYAN = "\033[36m";
    private static final String YELLOW = "\033[33m";
    private static final String RED = "\033[31m";
    private final PrintStream out;
    private final boolean styled;

    CliOutput(PrintStream out) {
        this(out, false);
    }

    CliOutput(PrintStream out, boolean styled) {
        this.out = out;
        this.styled = styled;
    }

    static CliOutput standard() {
        return new CliOutput(System.out, supportsStyle());
    }

    void line(String text) {
        this.out.println(text);
    }

    void success(String text) {
        this.state(GREEN, "✓", text);
    }

    void info(String text) {
        this.state(CYAN, "●", text);
    }

    void inactive(String text) {
        this.state(DIM, "○", text);
    }

    void warning(String text) {
        this.state(YELLOW, "!", text);
    }

    void error(String text) {
        this.state(RED, "✗", text);
    }

    void heading(String text) {
        this.out.println(this.paint(CYAN + BOLD, text));
    }

    void detail(String label, Object value) {
        String padded = String.format("%-12s", label);
        this.out.printf("  %s %s%n", this.paint(DIM, padded), value);
    }

    void note(String text) {
        this.out.println("  " + this.paint(DIM, text));
    }

    private void state(String color, String symbol, String text) {
        this.out.println(this.paint(color, symbol) + " " + this.paint(BOLD, text));
    }

    private String paint(String code, String text) {
        return this.styled ? code + text + RESET : text;
    }

    private static boolean supportsStyle() {
        return Ansi.AUTO.enabled();
    }
}
