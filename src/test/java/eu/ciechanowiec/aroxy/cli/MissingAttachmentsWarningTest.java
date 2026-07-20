package eu.ciechanowiec.aroxy.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.config.AttachmentRegistry;
import eu.ciechanowiec.aroxy.daemon.Home;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MissingAttachmentsWarningTest {

    @TempDir
    private Path base;

    private String checkWith(Path attached) {
        Home home = new Home(this.base);
        new AttachmentRegistry(home.attachments()).attach(attached);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        new MissingAttachmentsWarning(new CliOutput(new PrintStream(captured, true, UTF_8))).check(home);
        return captured.toString(UTF_8);
    }

    @Test
    void warnsWhenAPersistentFileIsMissing() {
        String out = this.checkWith(this.base.resolve("gone.conf"));
        assertTrue(out.contains("! Some persistent configuration files are unavailable"), out);
        assertTrue(out.contains("gone.conf"), out);
    }

    @Test
    @SneakyThrows
    void staysSilentWhenEveryPersistentFileIsReadable() {
        Path present = this.base.resolve("present.conf");
        Files.writeString(present, "[gateway]\n");
        assertFalse(this.checkWith(present).contains("unavailable"), "no warning when readable");
    }
}
