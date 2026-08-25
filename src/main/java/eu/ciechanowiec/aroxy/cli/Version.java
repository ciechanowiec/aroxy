package eu.ciechanowiec.aroxy.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
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
        return new String[] {"aroxy " + read(Version.class.getResourceAsStream(RESOURCE)).strip()};
    }

    /**
     * Package-private, and taking the stream rather than opening it, so that both ways this can fail are
     * reachable from a test: a resource that is not there, and one that cannot be read.
     *
     * @param stream the resource stream, or nothing when the resource is absent
     * @return the text of the resource
     */
    static String read(@Nullable InputStream stream) {
        try (InputStream resource = required(stream)) {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + RESOURCE, exception);
        }
    }

    /**
     * Package-private so the missing-resource failure can be exercised. That failure is real: the version
     * is filtered into the artifact at build time, so a packaging change can drop it, and what happens
     * then is a decision this class makes rather than an accident.
     *
     * @param stream the resource stream, or nothing when the resource is absent
     * @return the stream when it is there
     */
    static InputStream required(@Nullable InputStream stream) {
        return Optional.ofNullable(stream)
            .orElseThrow(() -> new IllegalStateException("Missing resource " + RESOURCE));
    }
}
