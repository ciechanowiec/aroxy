package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trips the daemon's published runtime snapshot through its tab-separated file form and checks
 * that a missing or malformed file reads as empty rather than throwing.
 */
class RuntimeSnapshotTest {

    private static final int RULE_COUNT = 2;
    private static final long START_MILLIS = 1000L;
    private static final long GENERATION = 5L;
    private static final long LOADED_MILLIS = 1234L;
    private static final long NOW_MILLIS = 1500L;
    private static final long UPTIME_MILLIS = 500L;
    private static final String LISTEN = "127.0.0.1:8788";
    private static final String UPSTREAM = "https://api.anthropic.com";
    private static final String FILE_PATH = "/etc/a.conf";

    @Test
    void writesAndReadsBackEveryField(@TempDir Path directory) {
        Path file = directory.resolve("runtime");
        RuntimeSnapshot original = new RuntimeSnapshot(
            LISTEN, UPSTREAM, RULE_COUNT, START_MILLIS, GENERATION,
            List.of(new AttachmentInfo(FILE_PATH, AttachmentKind.PERSISTENT, LOADED_MILLIS))
        );
        original.writeTo(file);
        RuntimeSnapshot read = RuntimeSnapshot.read(file).orElseThrow();
        assertEquals(LISTEN, read.listenAddress());
        assertEquals(UPSTREAM, read.upstream());
        assertEquals(RULE_COUNT, read.ruleCount());
        assertEquals(START_MILLIS, read.startMillis());
        assertEquals(GENERATION, read.generation());
        assertEquals(1, read.attachments().size());
        assertEquals(FILE_PATH, read.attachments().getFirst().path());
        assertEquals(AttachmentKind.PERSISTENT, read.attachments().getFirst().kind());
        assertEquals(LOADED_MILLIS, read.attachments().getFirst().loadedAtMillis());
    }

    @Test
    void derivesUptimeFromStart(@TempDir Path directory) {
        RuntimeSnapshot snapshot = new RuntimeSnapshot("h:1", "u", 0, START_MILLIS, 1L, List.of());
        assertEquals(UPTIME_MILLIS, snapshot.toStatusReport(NOW_MILLIS).uptimeMillis());
        assertEquals(0L, snapshot.toStatusReport(UPTIME_MILLIS).uptimeMillis());
    }

    @Test
    void readsAbsentFileAsEmpty(@TempDir Path directory) {
        assertTrue(RuntimeSnapshot.read(directory.resolve("missing")).isEmpty());
    }

    @Test
    @SneakyThrows
    void readsMalformedFileAsEmpty(@TempDir Path directory) {
        Path file = directory.resolve("runtime");
        Files.writeString(file, "upstream\tu\n");
        assertTrue(RuntimeSnapshot.read(file).isEmpty());
    }
}
