package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomeTest {

    private static final String PROPERTY = "aroxy.home";
    private static final String OWNER_ONLY = "rwx------";

    @TempDir
    private Path base;

    @AfterEach
    void clear() {
        System.clearProperty(PROPERTY);
    }

    @Test
    void createsDirectoriesOnDemand() {
        Home home = new Home(this.base);
        assertTrue(Files.isDirectory(home.logsDirectory()));
        assertTrue(Files.isDirectory(home.wireDirectory()));
        assertEquals(this.base.resolve("aroxy.pid"), home.pidFile());
        assertEquals(this.base.resolve("logs").resolve("aroxy.log"), home.appLog());
    }

    @Test
    void resolvesFromSystemProperty() {
        System.setProperty(PROPERTY, this.base.toString());
        assertEquals(this.base, Home.resolve().base());
    }

    @Test
    @SneakyThrows
    void createsStateDirectoriesOwnerOnly() {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Home home = new Home(this.base);
        Path wire = home.wireDirectory();
        assertEquals(OWNER_ONLY, PosixFilePermissions.toString(Files.getPosixFilePermissions(home.base())));
        assertEquals(OWNER_ONLY, PosixFilePermissions.toString(Files.getPosixFilePermissions(home.logsDirectory())));
        assertEquals(OWNER_ONLY, PosixFilePermissions.toString(Files.getPosixFilePermissions(wire)));
    }
}
