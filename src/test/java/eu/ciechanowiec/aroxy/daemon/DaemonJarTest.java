package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DaemonJarTest {

    @TempDir
    private Path base;

    @Test
    @SneakyThrows
    void runningSnapshotSurvivesInstalledJarReplacement() {
        Path installed = this.base.resolve("aroxy.jar");
        Files.writeString(installed, "old");
        Home home = new Home(this.base.resolve("home"));
        DaemonJar jar = new DaemonJar(home, installed.toString());
        Path running = Path.of(jar.stage());
        Files.writeString(installed, "new");
        assertEquals("old", Files.readString(running));
        assertEquals("new", Files.readString(Path.of(jar.stage())));
    }

    // The snapshot is what lets a daemon outlive an upgrade, and also what makes it quietly serve the
    // previous build afterwards. The CLI can only warn about that if the difference is observable.
    @Test
    @SneakyThrows
    void reportsStalenessOnlyOnceTheInstalledJarDiffers() {
        Path installed = this.base.resolve("aroxy.jar");
        Files.writeString(installed, "build-one");
        DaemonJar jar = new DaemonJar(new Home(this.base.resolve("home")), installed.toString());
        assertFalse(jar.isStale(), "nothing is staged yet, so nothing is stale");
        jar.stage();
        assertFalse(jar.isStale(), "a daemon launched from the current jar is current");
        Files.writeString(installed, "xuild-one");
        assertTrue(jar.isStale(), "installing a new jar leaves the running daemon behind");
    }

    @Test
    void leavesNonJarDevelopmentLocationUnchanged() {
        Path classes = this.base.resolve("classes");
        assertEquals(classes.toString(), new DaemonJar(new Home(this.base), classes.toString()).stage());
    }

    @Test
    void locatesTheInstalledArtifactFromTheRuntime() {
        assertFalse(new DaemonJar(new Home(this.base)).isStale());
    }
}
