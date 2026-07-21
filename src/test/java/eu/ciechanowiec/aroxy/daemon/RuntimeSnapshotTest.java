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
            List.of(claudeCode()), START_MILLIS, GENERATION,
            List.of(new AttachmentInfo(FILE_PATH, AttachmentKind.PERSISTENT, LOADED_MILLIS)),
            new RuntimeConfig(true, List.of("first", "second"))
        );
        original.writeTo(file);
        RuntimeSnapshot read = RuntimeSnapshot.read(file).orElseThrow();
        assertEquals(List.of(claudeCode()), read.gateways());
        assertEquals(RULE_COUNT, read.toStatusReport(NOW_MILLIS).ruleCount());
        assertEquals(START_MILLIS, read.startMillis());
        assertEquals(GENERATION, read.generation());
        assertEquals(1, read.attachments().size());
        assertEquals(FILE_PATH, read.attachments().getFirst().path());
        assertEquals(AttachmentKind.PERSISTENT, read.attachments().getFirst().kind());
        assertEquals(LOADED_MILLIS, read.attachments().getFirst().loadedAtMillis());
        assertEquals(new RuntimeConfig(true, List.of("first", "second")), read.config());
        assertTrue(read.renderConfig().contains("• second"));
    }

    @Test
    void roundTripsEveryListenerInOrder(@TempDir Path directory) {
        Path file = directory.resolve("runtime");
        new RuntimeSnapshot(
            List.of(claudeCode(), codex()), START_MILLIS, GENERATION,
            List.of(), new RuntimeConfig(true, List.of("first", "second"))
        ).writeTo(file);
        RuntimeSnapshot read = RuntimeSnapshot.read(file).orElseThrow();
        assertEquals(
            List.of(claudeCode(), codex()), read.gateways(),
            "every listener survives the round trip, in the order it was published"
        );
    }

    @Test
    void derivesUptimeFromStart() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
            List.of(claudeCode()), START_MILLIS, 1L, List.of(), new RuntimeConfig(true, List.of())
        );
        assertEquals(UPTIME_MILLIS, snapshot.toStatusReport(NOW_MILLIS).uptimeMillis());
        assertEquals(0L, snapshot.toStatusReport(UPTIME_MILLIS).uptimeMillis());
        assertEquals(List.of(claudeCode()), snapshot.toStatusReport(NOW_MILLIS).gateways());
    }

    @Test
    void rejectsSnapshotWithNoListener(@TempDir Path directory) {
        Path file = directory.resolve("runtime");
        new RuntimeSnapshot(
            List.of(), 0L, 1L, List.of(), new RuntimeConfig(true, List.of())
        ).writeTo(file);
        assertTrue(RuntimeSnapshot.read(file).isEmpty());
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
