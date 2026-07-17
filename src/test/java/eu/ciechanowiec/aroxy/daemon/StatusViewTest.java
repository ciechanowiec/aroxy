package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatusViewTest {

    private static final long UPTIME_MILLIS = 1000L;

    @Test
    void rendersRuntimeFieldsHumanReadably() {
        StatusReport report = new StatusReport(
            "127.0.0.1:8788", "https://api.example", 3, UPTIME_MILLIS,
            List.of(new AttachmentInfo("/etc/a.conf", AttachmentKind.PERSISTENT, 1L))
        );
        String text = StatusView.render(report);
        assertTrue(text.contains("running  = true"), text);
        assertTrue(text.contains("listen   = 127.0.0.1:8788"), text);
        assertTrue(text.contains("upstream = https://api.example"), text);
        assertTrue(text.contains("rules    = 3"), text);
        assertTrue(text.contains("uptime   = 0h 0m 1s"), text);
    }

    @Test
    void rendersContributingFileWithKindAndLoadTime() {
        StatusReport report = new StatusReport(
            "127.0.0.1:8788", "https://api.example", 1, 1L,
            List.of(new AttachmentInfo("/etc/a.conf", AttachmentKind.PERSISTENT, 1L))
        );
        String text = StatusView.render(report);
        assertTrue(text.contains("persistent"), text);
        assertTrue(text.contains("loaded "), text);
        assertTrue(text.contains("/etc/a.conf"), text);
    }
}
