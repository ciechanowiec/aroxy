package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the persistent attachment registry against a real temporary file: attach/detach/list round
 * trips, attach is idempotent, and a missing backing file behaves as an empty registry.
 */
class AttachmentRegistryTest {

    @Test
    void missingFileYieldsEmptyList(@TempDir Path directory) {
        AttachmentRegistry registry = new AttachmentRegistry(directory.resolve("attachments"));
        assertTrue(registry.list().isEmpty(), "absent registry file reads as empty");
    }

    @Test
    void attachDetachListRoundTrip(@TempDir Path directory) {
        AttachmentRegistry registry = new AttachmentRegistry(directory.resolve("attachments"));
        Path first = directory.resolve("a.conf").toAbsolutePath();
        Path second = directory.resolve("b.conf").toAbsolutePath();
        registry.attach(first);
        registry.attach(second);
        assertEquals(List.of(first, second), registry.list(), "both paths listed in order");
        registry.detach(first);
        assertEquals(List.of(second), registry.list(), "detached path removed, other retained");
    }

    @Test
    void detachingUnknownPathCreatesNoFile(@TempDir Path directory) {
        Path backing = directory.resolve("attachments");
        AttachmentRegistry registry = new AttachmentRegistry(backing);
        registry.detach(directory.resolve("never.conf").toAbsolutePath());
        assertFalse(Files.exists(backing), "a no-op detach does not materialize an empty registry file");
    }

    @Test
    @SneakyThrows
    void blankLinesInTheBackingFileAreIgnored(@TempDir Path directory) {
        Path backing = directory.resolve("attachments");
        Files.write(backing, List.of("/tmp/a.conf", "", "   ", "/tmp/b.conf"));
        AttachmentRegistry registry = new AttachmentRegistry(backing);
        assertEquals(2, registry.list().size(), "blank and whitespace-only lines are filtered out, not kept as paths");
    }

    @Test
    void attachIsIdempotent(@TempDir Path directory) {
        AttachmentRegistry registry = new AttachmentRegistry(directory.resolve("attachments"));
        Path path = directory.resolve("dup.conf").toAbsolutePath();
        registry.attach(path);
        registry.attach(path);
        assertEquals(List.of(path), registry.list(), "duplicate attach does not append twice");
    }
}
