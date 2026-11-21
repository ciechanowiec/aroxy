package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A state file is published whole and read back line by line, and every failure is absorbed: the
 * daemon keeps running when a publish fails, and the CLI reads an unreadable file as absent.
 */
class StateFileTest {

    private static final long NUMBER = 42L;

    @Test
    @SneakyThrows
    void replacesTheWholeFileAndLeavesNoTemporaryBehind(@TempDir Path base) {
        Path file = base.resolve("state");
        StateFile.write(file, List.of("old", "lines"));
        StateFile.write(file, List.of("new"));
        assertEquals(List.of("new"), StateFile.read(file));
        try (Stream<Path> entries = Files.list(base)) {
            assertEquals(List.of(file), entries.toList(), "only the published file remains");
        }
    }

    @Test
    void absorbsAPublishThatCannotBeWritten(@TempDir Path base) {
        Path file = base.resolve("missing").resolve("state");
        StateFile.write(file, List.of("lost"));
        assertFalse(Files.exists(file), "nothing is published into a directory that does not exist");
    }

    @Test
    void readsAnAbsentFileAsEmpty(@TempDir Path base) {
        assertTrue(StateFile.read(base.resolve("state")).isEmpty());
    }

    @Test
    void readsAnUnreadableFileAsEmpty(@TempDir Path base) {
        assertTrue(StateFile.read(base).isEmpty(), "a directory exists but cannot be read as lines");
    }

    @Test
    void parsesNumbersAndReadsAnythingElseAsZero() {
        assertEquals(NUMBER, StateFile.number(" 42 "));
        assertEquals(0L, StateFile.number("lots"));
    }
}
