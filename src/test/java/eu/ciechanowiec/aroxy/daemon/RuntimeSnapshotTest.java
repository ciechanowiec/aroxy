package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.ClaudeCode;
import eu.ciechanowiec.aroxy.client.CodexCli;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trips the daemon's published runtime snapshot through its tab-separated file form and checks
 * that a missing or malformed file reads as empty rather than throwing.
 *
 * <p>Also pins the two compatibility rules that let a snapshot survive an upgrade, since a daemon
 * started by one build can be read by the CLI of another: a snapshot carrying only the historical
 * single listener still parses, and a snapshot carrying several still writes that historical pair.
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
    private static final String CODEX_LISTEN = "127.0.0.1:8789";
    private static final String CODEX_UPSTREAM = "https://chatgpt.com/backend-api/codex";
    private static final String FILE_PATH = "/etc/a.conf";

    private static GatewayAddress claudeCode() {
        return new GatewayAddress(ClaudeCode.profile().displayName(), LISTEN, UPSTREAM);
    }

    private static GatewayAddress codex() {
        return new GatewayAddress(CodexCli.profile().displayName(), CODEX_LISTEN, CODEX_UPSTREAM);
    }

    @Test
    void writesAndReadsBackEveryField(@TempDir Path directory) {
        Path file = directory.resolve("runtime");
        RuntimeSnapshot original = new RuntimeSnapshot(
            List.of(claudeCode()), RULE_COUNT, START_MILLIS, GENERATION,
            List.of(new AttachmentInfo(FILE_PATH, AttachmentKind.PERSISTENT, LOADED_MILLIS))
        );
        original.writeTo(file);
        RuntimeSnapshot read = RuntimeSnapshot.read(file).orElseThrow();
        assertEquals(List.of(claudeCode()), read.gateways());
        assertEquals(RULE_COUNT, read.ruleCount());
        assertEquals(START_MILLIS, read.startMillis());
        assertEquals(GENERATION, read.generation());
        assertEquals(1, read.attachments().size());
        assertEquals(FILE_PATH, read.attachments().getFirst().path());
        assertEquals(AttachmentKind.PERSISTENT, read.attachments().getFirst().kind());
        assertEquals(LOADED_MILLIS, read.attachments().getFirst().loadedAtMillis());
    }

    @Test
    void roundTripsEveryListenerInOrder(@TempDir Path directory) {
        Path file = directory.resolve("runtime");
        new RuntimeSnapshot(
            List.of(claudeCode(), codex()), RULE_COUNT, START_MILLIS, GENERATION, List.of()
        ).writeTo(file);
        RuntimeSnapshot read = RuntimeSnapshot.read(file).orElseThrow();
        assertEquals(
            List.of(claudeCode(), codex()), read.gateways(),
            "every listener survives the round trip, in the order it was published"
        );
    }

    @Test
    @SneakyThrows
    void alsoWritesTheHistoricalPairSoAnOlderReaderStillFindsIt(@TempDir Path directory) {
        Path file = directory.resolve("runtime");
        new RuntimeSnapshot(List.of(claudeCode(), codex()), 0, 0L, 1L, List.of()).writeTo(file);
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.contains("listen\t" + LISTEN), lines.toString());
        assertTrue(lines.contains("upstream\t" + UPSTREAM), lines.toString());
    }

    @Test
    @SneakyThrows
    void readsASnapshotWithoutListenerRecordsAsTheSingleHistoricalListener(@TempDir Path directory) {
        Path file = directory.resolve("runtime");
        Files.writeString(file, "listen\t" + LISTEN + "\nupstream\t" + UPSTREAM + "\nrules\t3\n");
        RuntimeSnapshot read = RuntimeSnapshot.read(file).orElseThrow();
        assertEquals(
            List.of(claudeCode()), read.gateways(),
            "a snapshot written before listener records existed still names its listener"
        );
        assertEquals(3, read.ruleCount());
    }

    @Test
    void derivesUptimeFromStart() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(List.of(claudeCode()), 0, START_MILLIS, 1L, List.of());
        assertEquals(UPTIME_MILLIS, snapshot.toStatusReport(NOW_MILLIS).uptimeMillis());
        assertEquals(0L, snapshot.toStatusReport(UPTIME_MILLIS).uptimeMillis());
        assertEquals(List.of(claudeCode()), snapshot.toStatusReport(NOW_MILLIS).gateways());
    }

    @Test
    void aSnapshotWithNoListenerStillWritesTheHistoricalKey(@TempDir Path directory) {
        Path file = directory.resolve("runtime");
        new RuntimeSnapshot(List.of(), 0, 0L, 1L, List.of()).writeTo(file);
        assertTrue(
            RuntimeSnapshot.read(file).isPresent(),
            "the guard key is written even with nothing bound, so the file stays parseable"
        );
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
