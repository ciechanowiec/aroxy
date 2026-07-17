package eu.ciechanowiec.aroxy.daemon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;

/**
 * Sets up an {@code AROXY_HOME} {@code @TempDir} with a {@code default.conf} carrying one sensitive
 * rule. Neither the port nor the upstream is configurable, so a test reaches both through
 * {@link DaemonSeam#onEphemeralPort(java.net.URI)}: a free port instead of the production one, and a
 * real local {@link UpstreamStub} instead of the API. A test can then run a {@link Daemon} in-process and observe
 * it through its published {@link RuntimeSnapshot} and drive it through its {@link ControlFiles}.
 */
@UtilityClass
final class DaemonHarness {

    static final String SECRET = "AKIAIOSFODNN7EXAMPLE";
    private static final String DEFAULT = """
        [gateway]
        [rule]
        id = aws_key
        type = regex
        placeholder = AWS_KEY
        sensitive = true
        pattern = (?:AKIA|ASIA)[0-9A-Z]{16}
        """;

    static Home prepare(Path base) {
        Home home = new Home(base);
        write(home.defaultConfig(), DEFAULT);
        return home;
    }

    static Path writeTemporaryRule(Path directory, String id, String value) {
        String rule = """
            [rule]
            id = %s
            type = literal
            placeholder = COMPANY
            value = %s
            """.formatted(id, value);
        Path file = directory.resolve(id + ".conf");
        write(file, rule);
        return file;
    }

    private static void write(Path file, CharSequence text) {
        try {
            Files.writeString(file, text);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write " + file, exception);
        }
    }
}
