package eu.ciechanowiec.aroxy.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * A state directory is created owner-only where the filesystem can say so, and is still created where it
 * cannot. Both answers are passed in rather than read from the machine, because a test on one kind of
 * filesystem could otherwise never reach the other path.
 */
class RestrictedDirTest {

    private static final String OWNER_ONLY = "rwx------";

    @TempDir
    private Path base;

    @Test
    @SneakyThrows
    void createsEveryMissingParentOnTheWayDown() {
        Path nested = this.base.resolve("one").resolve("two").resolve("three");
        assertEquals(nested, RestrictedDir.create(nested), "the directory it was asked for is returned");
        assertTrue(Files.isDirectory(nested), "and it is there, parents and all");
    }

    @Test
    @SneakyThrows
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void locksTheDirectoryToItsOwnerWhereTheFilesystemSaysSo() {
        Path directory = RestrictedDir.create(this.base.resolve("secured"), true);
        assertEquals(
            OWNER_ONLY, PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)),
            "nobody but the owner reads a directory holding a running daemon's state"
        );
    }

    @Test
    @SneakyThrows
    void stillCreatesTheDirectoryWhereTheFilesystemCannotSaySo() {
        Path directory = RestrictedDir.create(this.base.resolve("plain"), false);
        assertTrue(
            Files.isDirectory(directory),
            "a filesystem without POSIX permissions is a filesystem that still needs the directory"
        );
    }
}
