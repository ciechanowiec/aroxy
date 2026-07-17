package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every tracked text file, apart from the roles excluded by {@link TypographyScanner}, uses only plain
 * ASCII typography. This gate runs on every build because it is fast and reads no network.
 */
class TypographyScanTest {

    @Test
    void trackedFilesUseOnlyPlainTypography() {
        List<String> violations = TypographyScanner.scan(Repository.root());
        assertTrue(
            violations.isEmpty(),
            () -> "Banned typography found (use the plain ASCII equivalents):\n"
                + String.join("\n", violations)
        );
    }
}
