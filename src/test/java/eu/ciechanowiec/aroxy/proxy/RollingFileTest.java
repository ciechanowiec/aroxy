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
}
