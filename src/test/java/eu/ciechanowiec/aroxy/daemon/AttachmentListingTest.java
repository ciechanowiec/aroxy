package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AttachmentListingTest {

    @Test
    void rendersPlaceholderWhenEmpty() {
        assertEquals("(no configuration files)", AttachmentListing.render(List.of()));
    }

    @Test
    void labelsAndStampsEachAttachment() {
        AttachmentInfo bundled = new AttachmentInfo("/home/default.conf", AttachmentKind.DEFAULT, 2L);
        AttachmentInfo persistent = new AttachmentInfo("/etc/a.conf", AttachmentKind.PERSISTENT, 1L);
        AttachmentInfo temporary = new AttachmentInfo("/tmp/b.conf", AttachmentKind.TEMPORARY, 0L);
        String text = AttachmentListing.render(List.of(bundled, persistent, temporary));
        assertTrue(text.contains("default"), text);
        assertTrue(text.contains("persistent"), text);
        assertTrue(text.contains("temporary"), text);
        assertTrue(text.contains("/home/default.conf"), text);
        assertTrue(text.contains("/etc/a.conf"), text);
        assertTrue(text.contains("/tmp/b.conf"), text);
        assertTrue(text.contains("1970"), text);
        assertTrue(text.contains("temporary   -"), text);
    }
}
