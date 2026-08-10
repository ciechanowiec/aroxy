package eu.ciechanowiec.aroxy.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import picocli.CommandLine.IVersionProvider;

/**
 * Supplies the line printed by {@code aroxy --version}. The version is read from {@code version.txt},
 * a resource that Maven filters at build time from {@code ${project.version}}, so the POM is the one
 * source of the version and no second copy in the code can drift from it.
 */
final class Version implements IVersionProvider {

    private static final String RESOURCE = "/version.txt";

    @Override
    public String[] getVersion() {
        return new String[] {"aroxy " + this.read().strip()};
    }

    private String read() {
        try (InputStream stream = Version.class.getResourceAsStream(RESOURCE)) {
            return new String(required(stream).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + RESOURCE, exception);
        }
    }

    private static InputStream required(InputStream stream) {
        return Optional.ofNullable(stream)
            .orElseThrow(() -> new IllegalStateException("Missing resource " + RESOURCE));
    }
}
