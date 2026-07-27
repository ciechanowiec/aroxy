package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves {@link RollingFile} rotates: once the active file passes its byte limit it is rolled to a
 * {@code .1} backup and a fresh active file continues, so the log never grows without bound.
 */
class RollingFileTest {

    private static final int MEGABYTE = 1024 * 1024;

    @Test
    @SneakyThrows
    void rotatesOnceTheSizeLimitIsExceeded(@TempDir Path directory) {
        Path file = directory.resolve("audit.log");
        RollingFile rolling = new RollingFile(file);
        String chunk = "x".repeat(MEGABYTE) + "\n";
        for (int index = 0; index < 6; index++) {
            rolling.append(chunk);
        }
        rolling.append("tail line\n");
        assertTrue(Files.exists(file.resolveSibling("audit.log.1")), "a rotated backup was created");
        assertTrue(Files.readString(file).contains("tail line"), "the active file keeps the latest line");
    }

    // Rotation shifts the whole chain: .1 becomes .2 before the active file becomes .1. Without the
    // shift each rotation would overwrite .1 and every older generation would be lost at once.
    @Test
    @SneakyThrows
    void shiftsOlderBackupsDownTheChain(@TempDir Path directory) {
        Path file = directory.resolve("audit.log");
        RollingFile rolling = new RollingFile(file);
        String chunk = "x".repeat(MEGABYTE) + "\n";
        rollOnce(rolling, chunk, "first generation\n");
        rollOnce(rolling, chunk, "second generation\n");
        assertTrue(Files.exists(file.resolveSibling("audit.log.2")), "the older backup moved down");
        assertTrue(
            Files.readString(file.resolveSibling("audit.log.2")).contains("first generation"),
            "the oldest content is the one furthest down the chain"
        );
        assertTrue(
            Files.readString(file.resolveSibling("audit.log.1")).contains("second generation"),
            "the newest backup holds what was active most recently"
        );
    }

    @SneakyThrows
    private static void rollOnce(RollingFile rolling, CharSequence chunk, CharSequence marker) {
        rolling.append(marker);
        for (int index = 0; index < 5; index++) {
            rolling.append(chunk);
        }
    }
}
