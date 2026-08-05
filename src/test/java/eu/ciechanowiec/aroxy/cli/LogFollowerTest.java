package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.Justification;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogFollowerTest {

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void seesAppendedLines() {
        Path log = this.directory.resolve("audit.log");
        Files.writeString(log, "first\n");
        LogFollower follower = new LogFollower(log);
        assertEquals("first\n", follower.poll().orElseThrow());
        assertTrue(follower.poll().isEmpty());
        this.append(log, "second\n");
        assertEquals("second\n", follower.poll().orElseThrow());
    }

    @Test
    void missingFileYieldsNothing() {
        LogFollower follower = new LogFollower(this.directory.resolve("absent.log"));
        assertTrue(follower.poll().isEmpty());
    }

    @Test
    @SneakyThrows
    void afterRotationReadsTheFreshFileFromTheStart() {
        Path log = this.directory.resolve("audit.log");
        Files.writeString(log, "a-long-first-generation\n");
        LogFollower follower = new LogFollower(log);
        follower.poll();
        Files.writeString(log, "rolled\n");
        assertEquals(
            "rolled\n", follower.poll().orElseThrow(),
            "a rotated (shorter) file is re-read from byte 0, not skipped to its new EOF"
        );
    }

    @Justification(
        "the single caller appends one fixture line, and parameterizing keeps the append helper reusable"
    )
    @SuppressWarnings(
        "SameParameterValue"
    )
    @SneakyThrows
    private void append(Path log, CharSequence text) {
        Files.writeString(log, text, StandardOpenOption.APPEND);
    }
}
