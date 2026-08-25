package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DaemonLifecycleTest {

    private static final int OK = 200;
    private static final int POLLS = 60;
    private static final Duration PAUSE = Duration.ofMillis(50);
    private static final String PRODUCTION_PORT = ":8788";

    private final Path base;
    private final UpstreamStub upstream;
    private final Daemon daemon;
    private final Home home;

    @SneakyThrows
    DaemonLifecycleTest(@TempDir Path base) {
        this.base = base;
        this.upstream = new UpstreamStub();
        this.home = DaemonHarness.prepare(base);
        this.daemon = new Daemon(this.home, DaemonSeam.onEphemeralPort(this.upstream.uri()));
        this.daemon.run();
    }

    @AfterEach
    void stopDaemon() {
        this.daemon.stop();
        this.upstream.stop();
    }

    @Test
    void statusSnapshotShowsRunningWithOneRule() {
        String text = StatusView.render(this.snapshot().toStatusReport(System.currentTimeMillis()));
        assertTrue(text.contains("Rules        1"), text);
        assertTrue(text.contains("Listeners"), text);
    }

    @Test
    void publishesARuntimeSnapshotAndNoControlPort() {
        assertTrue(Files.exists(this.home.runtimeFile()), "runtime snapshot published");
        assertFalse(Files.exists(this.base.resolve("admin.port")), "no admin control port file is written");
    }

    @Test
    @SneakyThrows
    void proxyObfuscatesTheSecret() {
        this.proxyPost("{\"text\":\"" + DaemonHarness.SECRET + "\"}");
        assertFalse(this.upstream.lastBody().contains(DaemonHarness.SECRET), this.upstream.lastBody());
        assertTrue(this.upstream.lastBody().contains("AWS_KEY"), this.upstream.lastBody());
    }

    @Test
    @SneakyThrows
    void directReloadPicksUpANewTemporaryRule() {
        Path rule = DaemonHarness.writeTemporaryRule(this.base, "codename", "Helios");
        new ControlFiles(this.home).appendTemporary(rule);
        this.daemon.reload();
        this.proxyPost("{\"text\":\"Helios\"}");
        assertFalse(this.upstream.lastBody().contains("Helios"), this.upstream.lastBody());
        assertTrue(this.upstream.lastBody().contains("COMPANY"), this.upstream.lastBody());
    }

    @Test
    @SneakyThrows
    void directReloadUpdatesAuditMasking() {
        Path settings = this.base.resolve("show-secrets.conf");
        Files.writeString(settings, "[gateway]\nmask_secrets = false\n");
        new ControlFiles(this.home).appendTemporary(settings);
        this.daemon.reload();
        this.proxyPost("{\"text\":\"" + DaemonHarness.SECRET + "\"}");
        String audit = Files.readString(this.home.auditLog());
        assertTrue(audit.contains(DaemonHarness.SECRET), audit);
    }

    @Test
    void touchingTheReloadTriggerCausesAReload() {
        long before = this.snapshot().generation();
        new ControlFiles(this.home).touch();
        assertTrue(this.awaitGenerationAfter(before), "the watcher applied the triggered reload");
    }

    /**
     * The daemon under test is already running, so a second one starting here proves neither holds a
     * fixed port: this suite runs green on a machine whose real aroxy daemon owns the production port.
     */
    @Test
    @SneakyThrows
    void aSecondDaemonBindsItsOwnPortRatherThanTheProductionOne() {
        Home other = DaemonHarness.prepare(Files.createDirectory(this.base.resolve("second")));
        Daemon second = new Daemon(other, DaemonSeam.onEphemeralPort(this.upstream.uri()));
        second.run();
        try {
            String mine = listenOf(this.snapshot());
            String theirs = listenOf(RuntimeSnapshot.read(other.runtimeFile()).orElseThrow());
            assertNotEquals(mine, theirs, "two daemons listen at once, so neither can hold one fixed port");
            assertFalse(mine.endsWith(PRODUCTION_PORT), mine);
            assertFalse(theirs.endsWith(PRODUCTION_PORT), theirs);
        } finally {
            second.stop();
        }
    }

    @Test
    void listingLabelsATemporaryAttachment() {
        Path rule = DaemonHarness.writeTemporaryRule(this.base, "listed", "Helios");
        new ControlFiles(this.home).appendTemporary(rule);
        this.daemon.reload();
        String listing = AttachmentListing.render(this.snapshot().attachments());
        assertTrue(listing.contains("temporary"), listing);
        assertTrue(listing.contains(rule.toAbsolutePath().toString()), listing);
    }

    private boolean awaitGenerationAfter(long before) {
        int attempt = 0;
        while (attempt < POLLS && this.snapshot().generation() <= before) {
            LockSupport.parkNanos(PAUSE.toNanos());
            attempt++;
        }
        return this.snapshot().generation() > before;
    }

    private static String listenOf(RuntimeSnapshot snapshot) {
        return snapshot.gateways().getFirst().listen();
    }

    private RuntimeSnapshot snapshot() {
        return RuntimeSnapshot.read(this.home.runtimeFile()).orElseThrow();
    }

    @SneakyThrows
    private void proxyPost(String body) {
        URI proxy = URI.create("http://" + listenOf(this.snapshot()) + "/v1/messages");
        HttpRequest request = HttpRequest.newBuilder().uri(proxy)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(OK, response.statusCode());
        }
    }
}
