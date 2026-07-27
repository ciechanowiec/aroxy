package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveAttachmentsTest {

    private static final long FIRST_LOAD = 100L;
    private static final long LATER_LOAD = 200L;

    @Test
    void stampsEveryPathOnceAndKeepsItsFirstLoadTime(@TempDir Path directory) {
        Path first = directory.resolve("first.conf");
        Path second = directory.resolve("second.conf");
        LiveAttachments attachments = new LiveAttachments();
        attachments.stamp(List.of(first, second), FIRST_LOAD);
        attachments.stamp(List.of(first), LATER_LOAD);
        assertEquals(FIRST_LOAD, attachments.loadedAt(first));
        assertEquals(FIRST_LOAD, attachments.loadedAt(second));
        assertEquals(0, attachments.loadedAt(directory.resolve("missing.conf")));
    }
}
