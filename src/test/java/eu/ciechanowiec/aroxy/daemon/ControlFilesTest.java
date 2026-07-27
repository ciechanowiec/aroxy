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

/**
 * Exercises the filesystem control channel against a real temporary directory: the reload trigger appears
 * only once touched, temporary paths append without duplicates, and clearing removes them all.
 */
class ControlFilesTest {

    private static final long FUTURE_STAMP = Long.MAX_VALUE - 1;

    @Test
    void triggerStampEmptyUntilTouched(@TempDir Path directory) {
        ControlFiles control = new ControlFiles(new Home(directory));
        assertTrue(control.triggerStamp().isEmpty(), "no trigger before any touch");
        control.touch();
        assertTrue(control.triggerStamp().isPresent(), "trigger present after touch");
    }

    @Test
    void rapidTouchesProduceStrictlyIncreasingStamps(@TempDir Path directory) {
        ControlFiles control = new ControlFiles(new Home(directory));
        control.touch();
        long first = control.triggerStamp().orElseThrow();
        control.touch();
        long second = control.triggerStamp().orElseThrow();
        assertTrue(second > first, "each touch writes a larger stamp, even within one filesystem mtime tick");
    }

    @Test
    @SneakyThrows
    void aFutureStampStillAdvancesByOne(@TempDir Path directory) {
        Home home = new Home(directory);
        Files.writeString(home.reloadTrigger(), Long.toString(FUTURE_STAMP));
        ControlFiles control = new ControlFiles(home);
        control.touch();
        assertEquals(FUTURE_STAMP + 1, control.triggerStamp().orElseThrow());
    }

    @Test
    void appendDedupsAndClearRemoves(@TempDir Path directory) {
        ControlFiles control = new ControlFiles(new Home(directory));
        Path rule = directory.resolve("r.conf");
        control.appendTemporary(rule);
        control.appendTemporary(rule);
        assertEquals(1, control.readTemporaries().size(), "duplicate temporary is not appended twice");
        assertEquals(rule.toAbsolutePath(), control.readTemporaries().getFirst());
        control.clearTemporaries();
        assertTrue(control.readTemporaries().isEmpty(), "clear removes every temporary");
    }

    @Test
    void readTemporariesEmptyWhenAbsent(@TempDir Path directory) {
        assertTrue(new ControlFiles(new Home(directory)).readTemporaries().isEmpty());
    }

    @Test
    @SneakyThrows
    void nonNumericTriggerContentReadsAsEmpty(@TempDir Path directory) {
        Home home = new Home(directory);
        Files.writeString(home.reloadTrigger(), "not-a-number");
        assertTrue(new ControlFiles(home).triggerStamp().isEmpty(), "an unparsable stamp reads as empty");
    }

    @Test
    @SneakyThrows
    void unreadableTriggerReadsAsEmpty(@TempDir Path directory) {
        Home home = new Home(directory);
        Files.createDirectory(home.reloadTrigger());
        assertTrue(new ControlFiles(home).triggerStamp().isEmpty(), "a trigger that cannot be read reads as empty");
    }

    @Test
    @SneakyThrows
    void blankLinesInTheTemporariesFileAreSkipped(@TempDir Path directory) {
        Home home = new Home(directory);
        Files.writeString(home.temporariesFile(), "\n  \n" + directory.resolve("r.conf") + "\n");
        assertEquals(1, new ControlFiles(home).readTemporaries().size(), "blank lines are ignored, the path is kept");
    }

    @Test
    @SneakyThrows
    void clearingANonEmptyTemporariesDirectoryReportsAnError(@TempDir Path directory) {
        Home home = new Home(directory);
        Files.createDirectory(home.temporariesFile());
        Files.writeString(home.temporariesFile().resolve("child"), "x");
        ControlFiles control = new ControlFiles(home);
        assertThrows(
            UncheckedIOException.class, control::clearTemporaries, "a directory that cannot be cleared errors"
        );
    }

    @Test
    void touchWritesAtomicallyLeavingNoTemporaryFileBehind(@TempDir Path directory) {
        Home home = new Home(directory);
        new ControlFiles(home).touch();
        Path trigger = home.reloadTrigger();
        Path leftover = trigger.resolveSibling(trigger.getFileName() + ".tmp");
        assertFalse(Files.exists(leftover), "the temp file is moved into place, never left behind");
        assertTrue(new ControlFiles(home).triggerStamp().isPresent(), "the trigger is written");
    }
}
