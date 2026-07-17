package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies materialization of the bundled {@code default.conf}: writing into an empty directory
 * creates the file with the bundled content, and a second call does not overwrite a user-modified
 * copy.
 */
class DefaultConfigTest {

    @Test
    @SneakyThrows
    void materializesBundledContentIntoEmptyDirectory(@TempDir Path directory) {
        Path target = directory.resolve("default.conf");
        new DefaultConfig(target).materialize();
        assertTrue(Files.exists(target), "file created");
        String written = Files.readString(target);
        assertEquals(DefaultConfigFixture.bundledText(), written, "content matches the bundled resource");
    }

    @Test
    @SneakyThrows
    void doesNotOverwriteUserModifiedFile(@TempDir Path directory) {
        Path target = directory.resolve("default.conf");
        Files.writeString(target, "# my edits\n");
        new DefaultConfig(target).materialize();
        assertEquals("# my edits\n", Files.readString(target), "user edits preserved");
    }

    @Test
    void reportsWhenTheTargetDirectoryIsMissing(@TempDir Path directory) {
        Path target = directory.resolve("absent-dir").resolve("default.conf");
        DefaultConfig config = new DefaultConfig(target);
        assertThrows(
            UncheckedIOException.class, config::materialize, "a missing parent directory surfaces as an error"
        );
    }

    @Test
    void materializedFileParsesBackToRules(@TempDir Path directory) {
        Path target = directory.resolve("default.conf");
        new DefaultConfig(target).materialize();
        ConfigFile file = ConfigFile.read(target);
        assertTrue(
            file.rules().stream().anyMatch(entry -> "aws_access_key".equals(entry.id())),
            "materialized file parses to the bundled rules"
        );
    }
}
