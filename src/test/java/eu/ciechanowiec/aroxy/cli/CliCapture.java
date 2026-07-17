package eu.ciechanowiec.aroxy.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import lombok.experimental.UtilityClass;

/**
 * Captures everything {@code Aroxy.execute} prints to {@code System.out} during one CLI invocation so
 * a test can assert on the rendered output, restoring the original stream afterwards.
 */
@UtilityClass
final class CliCapture {

    @SuppressWarnings(
        "PMD.CloseResource"
    ) // the replacement stream wraps an in-memory buffer; closing it is moot
    static Result run(String... args) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            int exit = Aroxy.execute(args);
            return new Result(exit, buffer.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(original);
        }
    }

    public record Result(int exit, String out) {
    }
}
