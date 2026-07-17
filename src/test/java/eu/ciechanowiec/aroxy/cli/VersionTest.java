package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The version line is read from {@code version.txt}, which Maven filters from {@code project.version},
 * so {@code aroxy --version} tracks the POM and never prints a stale, hand-written number.
 */
class VersionTest {

    @Test
    void readsVersionFromTheFilteredResource() {
        String[] lines = new Version().getVersion();
        assertEquals(1, lines.length, "one version line");
        assertTrue(lines[0].matches("aroxy \\d[\\w.+-]*"), "prints 'aroxy <version>': " + lines[0]);
    }
}
