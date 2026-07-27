package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the {@link WireDumps} retention sweep: a dump older than the maximum age is pruned, and the
 * directory is held under the maximum file count so a multi-day run stays bounded.
 */
class WireDumpsTest {

    private static final int OVER_MAX = 502;
    private static final int MAX_FILES = 500;
    private static final int AGED_DAYS = 8;

    @Test
    @SneakyThrows
    void prunesDumpsOlderThanTheMaxAge(@TempDir Path directory) {
        Path wire = directory.resolve("wire");
        WireDumps dumps = new WireDumps(wire);
        dumps.redactedRequest("old", "{}".getBytes(StandardCharsets.UTF_8), WireBody.TEXT);
        Path old = wire.resolve("old-redacted-request.json");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(AGED_DAYS, ChronoUnit.DAYS)));
        dumps.rawResponse("fresh", "data".getBytes(StandardCharsets.UTF_8), WireBody.TEXT);
        assertFalse(Files.exists(old), "the aged dump is swept");
        assertTrue(Files.exists(wire.resolve("fresh-raw-response.txt")), "the fresh dump survives");
    }

    @Test
    @SneakyThrows
    void leavesAnAgedNonRegularEntryAlone(@TempDir Path directory) {
        Path wire = directory.resolve("wire");
        WireDumps dumps = new WireDumps(wire);
        Path nested = Files.createDirectory(wire.resolve("nested"));
        Files.setLastModifiedTime(nested, FileTime.from(Instant.now().minus(AGED_DAYS, ChronoUnit.DAYS)));
        dumps.rawResponse("fresh", "data".getBytes(StandardCharsets.UTF_8), WireBody.TEXT);
        assertTrue(Files.isDirectory(nested), "an aged entry that is not a regular file is not a dump to prune");
        assertTrue(Files.exists(wire.resolve("fresh-raw-response.txt")), "the fresh dump survives");
    }

    @Test
    @SneakyThrows
    void capsTheDirectoryAtTheMaxFileCount(@TempDir Path directory) {
        Path wire = directory.resolve("wire");
        WireDumps dumps = new WireDumps(wire);
        byte[] body = "b".getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < OVER_MAX; index++) {
            dumps.redactedRequest("r" + String.format("%04d", index), body, WireBody.TEXT);
        }
        try (Stream<Path> entries = Files.list(wire)) {
            assertTrue(entries.count() <= MAX_FILES, "the directory is capped at the maximum count");
        }
    }
}
