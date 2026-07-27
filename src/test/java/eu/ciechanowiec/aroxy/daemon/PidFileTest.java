package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PidFileTest {

    private static final String PID_FILE = "aroxy.pid";

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void writeReadClearRoundTrips() {
        Path file = this.directory.resolve(PID_FILE);
        PidFile pid = new PidFile(file);
        long current = ProcessHandle.current().pid();
        pid.write(current);
        assertEquals(current, pid.read().orElseThrow());
        assertTrue(Files.readString(file).startsWith(current + "\t"), "a live pid records its start time");
        pid.clear();
        assertTrue(pid.read().isEmpty());
    }

    @Test
    void currentProcessIsRunning() {
        PidFile pid = new PidFile(this.directory.resolve(PID_FILE));
        pid.write(ProcessHandle.current().pid());
        assertTrue(pid.isRunning());
    }

    @Test
    void absentPidIsNotRunning() {
        PidFile pid = new PidFile(this.directory.resolve("missing.pid"));
        assertFalse(pid.isRunning());
        assertTrue(pid.read().isEmpty());
    }

    @Test
    @SneakyThrows
    void aLivePidWithADifferentStartTimeIsNotRunning() {
        Path file = this.directory.resolve(PID_FILE);
        long current = ProcessHandle.current().pid();
        Files.writeString(file, current + "\t1");
        PidFile pid = new PidFile(file);
        assertEquals(current, pid.read().orElseThrow(), "the pid is still read from the first field");
        assertFalse(pid.isRunning(), "a recycled pid whose recorded start time differs is not the daemon");
    }

    @Test
    @SneakyThrows
    void clearingANonEmptyDirectoryReportsAnError() {
        Path asDirectory = this.directory.resolve(PID_FILE);
        Files.createDirectory(asDirectory);
        Files.writeString(asDirectory.resolve("child"), "x");
        PidFile pid = new PidFile(asDirectory);
        assertThrows(UncheckedIOException.class, pid::clear, "a directory that cannot be deleted surfaces an error");
    }

    @Test
    void writingUnderAMissingParentReportsAnError() {
        PidFile pid = new PidFile(this.directory.resolve("absent").resolve(PID_FILE));
        assertThrows(
            UncheckedIOException.class,
            () -> pid.write(ProcessHandle.current().pid()),
            "a missing parent directory surfaces an error"
        );
    }

    @Test
    @SneakyThrows
    void readingADirectoryReportsAnError() {
        Path asDirectory = this.directory.resolve(PID_FILE);
        Files.createDirectory(asDirectory);
        PidFile pid = new PidFile(asDirectory);
        assertThrows(UncheckedIOException.class, pid::read, "a pid path that is a directory cannot be read");
    }

    @Test
    @SneakyThrows
    void liveHandleResolvesTheMatchingProcessAndIsEmptyForARecycledPid() {
        Path file = this.directory.resolve(PID_FILE);
        long current = ProcessHandle.current().pid();
        new PidFile(file).write(current);
        assertEquals(
            current, new PidFile(file).liveHandle().orElseThrow().pid(),
            "the live handle resolves the recorded daemon"
        );
        Files.writeString(file, current + "\t1");
        assertTrue(
            new PidFile(file).liveHandle().isEmpty(),
            "a recycled pid whose start time differs yields no handle to signal"
        );
    }
}
