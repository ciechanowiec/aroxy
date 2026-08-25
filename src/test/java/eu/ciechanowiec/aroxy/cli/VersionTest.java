package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The version line is read from {@code version.txt}, which Maven filters from {@code project.version},
 * so {@code aroxy --version} tracks the POM and never prints a stale, hand-written number.
 */
class VersionTest {

    @Test
    void readsVersionFromTheFilteredResource() {
        String[] lines = new Version().getVersion();
        assertEquals(1, lines.length, "one version line");
        assertTrue(lines[0].matches("aroxy \\d[\\w.+-]*"), "prints 'aroxy <version>': " + lines[0]);
    }

    @Test
    void reportsAResourceThatIsThereButCannotBeRead() {
        UncheckedIOException thrown = assertThrows(
            UncheckedIOException.class, () -> Version.read(new UnreadableStream()),
            "a stream that cannot be read is a packaging fault too, and it names the resource"
        );
        assertTrue(
            String.valueOf(thrown.getMessage()).contains("version.txt"),
            "and says which resource it was: " + thrown.getMessage()
        );
    }

    @Test
    void refusesToInventAVersionWhenTheResourceIsMissing() {
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class, () -> Version.read(null),
            "the version is filtered in at build time, so its absence is a packaging fault to report"
        );
        assertTrue(
            String.valueOf(thrown.getMessage()).contains("version.txt"),
            "and the message names the resource that was not there: " + thrown.getMessage()
        );
    }

    /**
     * A resource that is present but fails on every read. That is the second way reading the version can
     * fail once the resource itself is there, and a stream that refuses says so more directly than a
     * closed one.
     */
    private static final class UnreadableStream extends InputStream {

        @Override
        @SneakyThrows
        public int read() {
            throw new IOException("unreadable");
        }

        @Override
        @SneakyThrows
        public int read(byte[] target, int offset, int length) {
            throw new IOException("unreadable");
        }
    }
}
