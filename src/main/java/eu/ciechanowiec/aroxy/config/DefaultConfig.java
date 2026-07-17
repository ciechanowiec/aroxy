package eu.ciechanowiec.aroxy.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Materializes the bundled {@code default.conf} resource to a target path on first run. An existing
 * file is never overwritten, so a user's edits to their materialized copy survive subsequent starts.
 */
public final class DefaultConfig {

    private static final String RESOURCE = "/default.conf";

    private final Path target;

    /**
     * Creates a materializer that writes the bundled default configuration to {@code target}.
     *
     * @param target the path the bundled {@code default.conf} is materialized to
     */
    public DefaultConfig(Path target) {
        this.target = target;
    }

    /**
     * Writes the bundled default configuration to the target path unless it already exists.
     */
    public void materialize() {
        if (!Files.exists(this.target)) {
            this.copyResource();
        }
    }

    private void copyResource() {
        try {
            Files.write(this.target, this.bundledBytes());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not materialize default config to " + this.target, exception);
        }
    }

    private byte[] bundledBytes() throws IOException {
        try (InputStream resource = DefaultConfig.class.getResourceAsStream(RESOURCE)) {
            return readAll(resource);
        }
    }

    // the stream is closed by the caller's try-with-resources
    @SuppressWarnings(
        "resource"
    )
    private static byte[] readAll(InputStream resource) throws IOException {
        return Optional.ofNullable(resource).orElseThrow(DefaultConfig::missing).readAllBytes();
    }

    private static IllegalStateException missing() {
        return new IllegalStateException("Bundled resource missing: " + RESOURCE);
    }
}
