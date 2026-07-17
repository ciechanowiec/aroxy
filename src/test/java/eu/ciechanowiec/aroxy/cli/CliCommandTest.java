package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliCommandTest {

    private static final String PROPERTY = "aroxy.home";
    private static final String CONFIG = "config";

    @TempDir
    private Path home;

    @BeforeEach
    void pointHome() {
        System.setProperty(PROPERTY, this.home.toString());
    }

    @AfterEach
    void clearHome() {
        System.clearProperty(PROPERTY);
    }

    @Test
    void statusReportsNotRunning() {
        CliCapture.Result result = CliCapture.run("status");
        assertNotEquals(0, result.exit());
        assertTrue(result.out().contains("not running"), result.out());
    }

    @Test
    void attachPersistentWhileStoppedWritesRegistry() {
        Path rule = this.writeRule();
        CliCapture.Result result = CliCapture.run(CONFIG, "attach", rule.toString());
        assertEquals(0, result.exit());
        CliCapture.Result list = CliCapture.run(CONFIG, "list");
        assertTrue(list.out().contains(rule.toAbsolutePath().toString()), list.out());
    }

    @Test
    void detachWhileStoppedSucceeds() {
        Path rule = this.writeRule();
        CliCapture.run(CONFIG, "attach", rule.toString());
        CliCapture.Result detach = CliCapture.run(CONFIG, "detach", rule.toString());
        assertEquals(0, detach.exit());
        CliCapture.Result list = CliCapture.run(CONFIG, "list");
        assertTrue(list.out().contains("default"), list.out());
        assertFalse(list.out().contains(rule.toAbsolutePath().toString()), list.out());
    }

    @Test
    void detachWhileRunningIsRefused() {
        this.markRunning();
        Path rule = this.writeRule();
        CliCapture.Result detach = CliCapture.run(CONFIG, "detach", rule.toString());
        assertNotEquals(0, detach.exit());
        assertTrue(detach.out().contains("Refusing to detach"), detach.out());
    }

    @Test
    void attachingNonConfigFileIsRejectedAndNotRegistered() {
        Path notes = this.home.resolve("notes.md");
        this.write(notes, "# Project notes\n\nThis is prose, not an aroxy config.\n");
        CliCapture.Result attach = CliCapture.run(CONFIG, "attach", notes.toString());
        assertNotEquals(0, attach.exit());
        assertTrue(attach.out().contains("Not a valid aroxy configuration file"), attach.out());
        CliCapture.Result list = CliCapture.run(CONFIG, "list");
        assertFalse(list.out().contains(notes.toAbsolutePath().toString()), list.out());
    }

    @Test
    @SneakyThrows
    void attachingASymbolicLinkIsRejected() {
        Path target = this.writeRule();
        Path link = this.home.resolve("link.conf");
        Files.createSymbolicLink(link, target);
        CliCapture.Result attach = CliCapture.run(CONFIG, "attach", link.toString());
        assertNotEquals(0, attach.exit());
        assertTrue(attach.out().contains("symbolic link"), attach.out());
        CliCapture.Result list = CliCapture.run(CONFIG, "list");
        assertFalse(list.out().contains(link.toAbsolutePath().toString()), list.out());
    }

    private void markRunning() {
        Path pid = this.home.resolve("aroxy.pid");
        this.write(pid, Long.toString(ProcessHandle.current().pid()));
    }

    private Path writeRule() {
        Path rule = this.home.resolve("extra.conf");
        this.write(rule, "[rule]\nid = extra\ntype = literal\nplaceholder = COMPANY\nvalue = Zeus\n");
        return rule;
    }

    private void write(Path file, CharSequence text) {
        try {
            Files.writeString(file, text);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write " + file, exception);
        }
    }
}
