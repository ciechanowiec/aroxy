package eu.ciechanowiec.aroxy.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Loads the bundled {@code default.conf} resource as text for the config tests, so the same content
 * the jar ships is exercised rather than a hand-copied duplicate.
 */
@UtilityClass
final class DefaultConfigFixture {

    private static final String RESOURCE = "/default.conf";

    static String bundledText() {
        try (InputStream resource = DefaultConfigFixture.class.getResourceAsStream(RESOURCE)) {
            return Optional.ofNullable(resource)
                .map(DefaultConfigFixture::decode)
                .orElseThrow(DefaultConfigFixture::missing);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not close bundled default.conf", exception);
        }
    }

    private static String decode(InputStream resource) {
        try {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read bundled default.conf", exception);
        }
    }

    private static IllegalStateException missing() {
        return new IllegalStateException("Bundled default.conf missing");
    }
}
