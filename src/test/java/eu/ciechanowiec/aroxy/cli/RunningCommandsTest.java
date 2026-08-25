package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.daemon.Daemon;
import eu.ciechanowiec.aroxy.daemon.DaemonSeam;
import eu.ciechanowiec.aroxy.daemon.Home;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the CLI commands that talk to a running daemon through the file-and-signal control channel:
 * a real in-process {@link Daemon} publishes its runtime snapshot and polls the reload trigger, and a
 * pid file pointing at this test process makes the commands treat it as running. Covers the
 * {@code status}, {@code reload}, and {@code attach} "while running" paths end to end. The daemon
 * binds a free port through {@link DaemonSeam#onEphemeralPort()}, so it never contends for the
 * production port. These commands read the address back from the published snapshot, so the port is
 * immaterial to them.
 */
class RunningCommandsTest {

    private static final String PROPERTY = "aroxy.home";
    private static final String CONFIG = "config";
    private static final String DEFAULT = """
        [gateway]
        [rule]
        id = aws_key
        type = regex
        placeholder = AWS_KEY
        sensitive = true
        pattern = (?:AKIA|ASIA)[0-9A-Z]{16}
        """;

    private final Path home;
    private final Daemon daemon;

    @SneakyThrows
    RunningCommandsTest(@TempDir Path home) {
        this.home = home;
        System.setProperty(PROPERTY, home.toString());
        Home resolved = new Home(home);
        write(resolved.defaultConfig(), DEFAULT);
        this.daemon = new Daemon(resolved, DaemonSeam.onEphemeralPort());
        this.daemon.run();
        write(resolved.pidFile(), Long.toString(ProcessHandle.current().pid()));
    }

    @AfterEach
    void stopDaemon() {
        this.daemon.stop();
        System.clearProperty(PROPERTY);
    }

    @Test
    void statusWhileRunningShowsState() {
        CliCapture.Result result = CliCapture.run("status");
        assertEquals(0, result.exit());
        assertTrue(result.out().contains("● Daemon running"), result.out());
        assertTrue(result.out().contains("Rules        1"), result.out());
    }

    @Test
    void reloadWhileRunningConfirms() {
        CliCapture.Result result = CliCapture.run("reload");
        assertEquals(0, result.exit());
        assertTrue(result.out().contains("reloaded"), result.out());
    }

    @Test
    void configShowsWhatIsActiveUntilDiskChangesAreReloaded() {
        CliCapture.Result initial = CliCapture.run(CONFIG);
        assertEquals(0, initial.exit());
        assertTrue(initial.out().contains("Active configuration"), initial.out());
        assertTrue(initial.out().contains("• aws_key"), initial.out());
        write(new Home(this.home).defaultConfig(), DEFAULT.replace("aws_key", "new_disk_rule"));
        CliCapture.Result beforeReload = CliCapture.run(CONFIG);
        assertTrue(beforeReload.out().contains("• aws_key"), beforeReload.out());
        assertFalse(beforeReload.out().contains("• new_disk_rule"), beforeReload.out());
        assertEquals(0, CliCapture.run("reload").exit());
        CliCapture.Result afterReload = CliCapture.run(CONFIG);
        assertFalse(afterReload.out().contains("• aws_key"), afterReload.out());
        assertTrue(afterReload.out().contains("• new_disk_rule"), afterReload.out());
    }

    @Test
    void attachTemporaryWhileRunningListsIt() {
        Path rule = this.writeRule("temp_codename", "Helios");
        CliCapture.Result attach = CliCapture.run(CONFIG, "attach", rule.toString(), "--temporary");
        assertEquals(0, attach.exit());
        CliCapture.Result list = CliCapture.run(CONFIG, "list");
        assertTrue(list.out().contains("temporary"), list.out());
        assertTrue(list.out().contains(rule.toAbsolutePath().toString()), list.out());
    }

    @Test
    void attachPersistentWhileRunningListsIt() {
        Path rule = this.writeRule("persist_codename", "Apollo");
        CliCapture.Result attach = CliCapture.run(CONFIG, "attach", rule.toString(), "--persistent");
        assertEquals(0, attach.exit());
        CliCapture.Result list = CliCapture.run(CONFIG, "list");
        assertTrue(list.out().contains("persistent"), list.out());
    }

    @Test
    void attachWhileRunningReportsThatItIsAlreadyInForce() {
        Path rule = this.writeRule("live_codename", "Vega");
        CliCapture.Result attach = CliCapture.run(CONFIG, "attach", rule.toString(), "--persistent");
        assertEquals(0, attach.exit());
        assertTrue(attach.out().contains("attached and applied"), attach.out());
        assertTrue(attach.out().contains("Active now"), attach.out());
        assertTrue(attach.out().contains("in effect"), attach.out());
    }

    @Test
    void reloadReportsWhatIsInForceAfterwards() {
        CliCapture.Result reload = CliCapture.run("reload");
        assertEquals(0, reload.exit());
        assertTrue(reload.out().contains("Active now"), reload.out());
    }

    private Path writeRule(String id, String value) {
        Path rule = this.home.resolve(id + ".conf");
        write(
            rule, "[rule]\nid = " + id + "\ntype = literal\nplaceholder = COMPANY\nvalue = "
                + value + "\n"
        );
        return rule;
    }

    private static void write(Path file, CharSequence text) {
        try {
            Files.writeString(file, text);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write " + file, exception);
        }
    }
}
