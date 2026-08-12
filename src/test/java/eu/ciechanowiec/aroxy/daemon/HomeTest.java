package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class HomeTest {

    private static final String PROPERTY = "aroxy.home";
    private static final String OWNER_ONLY = "rwx------";
    private static final String UNUSED_HOME = "/nonexistent-user-home";
    private static final String STATE_DIR = ".aroxy";

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
    void prefersHomeVariableOverUserHomeProperty() {
        Path resolved = Home.userDefault(Optional.of(this.base.toString()), UNUSED_HOME);
        assertEquals(this.base.resolve(STATE_DIR), resolved);
    }

    @Test
    void fallsBackToUserHomePropertyWhenVariableIsUnset() {
        Path resolved = Home.userDefault(Optional.empty(), this.base.toString());
        assertEquals(this.base.resolve(STATE_DIR), resolved);
    }

    @Test
    void fallsBackToUserHomePropertyWhenVariableIsBlank() {
        Path resolved = Home.userDefault(Optional.of("   "), this.base.toString());
        assertEquals(this.base.resolve(STATE_DIR), resolved);
    }

    @Test
    @SneakyThrows
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void createsStateDirectoriesOwnerOnly() {
        Home home = new Home(this.base);
        Path wire = home.wireDirectory();
        assertEquals(OWNER_ONLY, PosixFilePermissions.toString(Files.getPosixFilePermissions(home.base())));
        assertEquals(OWNER_ONLY, PosixFilePermissions.toString(Files.getPosixFilePermissions(home.logsDirectory())));
        assertEquals(OWNER_ONLY, PosixFilePermissions.toString(Files.getPosixFilePermissions(wire)));
    }
}
