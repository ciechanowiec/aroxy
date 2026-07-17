package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.config.AttachmentRegistry;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachmentLifecycleTest {

    @TempDir
    private Path base;
    private UpstreamStub upstream;
    private Home home;

    @BeforeEach
    @SneakyThrows
    void prepare() {
        this.upstream = new UpstreamStub();
        this.home = DaemonHarness.prepare(this.base);
    }

    @AfterEach
    void stop() {
        this.upstream.stop();
    }

    private boolean hasPath(Path rule) {
        String target = rule.toAbsolutePath().toString();
        return RuntimeSnapshot.read(this.home.runtimeFile()).stream()
            .flatMap(snapshot -> snapshot.attachments().stream())
            .anyMatch(info -> info.path().equals(target));
    }

    @Test
    @SneakyThrows
    void persistentAttachSurvivesRestart() {
        Path rule = DaemonHarness.writeTemporaryRule(this.base, "codename", "Helios");
        Daemon first = new Daemon(this.home, DaemonSeam.onEphemeralPort(this.upstream.uri()));
        first.run();
        new AttachmentRegistry(this.home.attachments()).attach(rule);
        first.reload();
        assertTrue(this.hasPath(rule));
        first.stop();
        Daemon second = new Daemon(this.home, DaemonSeam.onEphemeralPort(this.upstream.uri()));
        second.run();
        assertTrue(this.hasPath(rule));
        assertTrue(new OfflineConfig(this.home).render(List.of()).contains("codename"));
        second.stop();
    }

    @Test
    @SneakyThrows
    void temporaryAttachIsLostOnRestart() {
        Path rule = DaemonHarness.writeTemporaryRule(this.base, "temponly", "Secret");
        Daemon first = new Daemon(this.home, DaemonSeam.onEphemeralPort(this.upstream.uri()));
        first.run();
        new ControlFiles(this.home).appendTemporary(rule);
        first.reload();
        assertTrue(this.hasPath(rule));
        first.stop();
        Daemon second = new Daemon(this.home, DaemonSeam.onEphemeralPort(this.upstream.uri()));
        second.run();
        assertFalse(this.hasPath(rule));
        second.stop();
    }

    @Test
    @SneakyThrows
    void stoppingTheDaemonClearsTheSessionTemporaries() {
        Path rule = DaemonHarness.writeTemporaryRule(this.base, "ephemeral", "Secret");
        Daemon daemon = new Daemon(this.home, DaemonSeam.onEphemeralPort(this.upstream.uri()));
        daemon.run();
        new ControlFiles(this.home).appendTemporary(rule);
        daemon.reload();
        assertTrue(this.hasPath(rule), "the temporary is visible while the daemon runs");
        daemon.stop();
        assertTrue(
            new ControlFiles(this.home).readTemporaries().isEmpty(),
            "stopping clears temporaries so an offline config never shows a stopped session's attachments"
        );
    }

    @Test
    void configListReflectsRegistry() {
        Path rule = DaemonHarness.writeTemporaryRule(this.base, "listed", "Value");
        new AttachmentRegistry(this.home.attachments()).attach(rule);
        OfflineConfig offline = new OfflineConfig(this.home);
        assertTrue(offline.attachments().contains(rule.toAbsolutePath().toString()));
    }
}
