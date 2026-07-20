package eu.ciechanowiec.aroxy.daemon;

import java.util.Collection;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Renders the attachment list for {@code config list}: one line per file, labelled {@code persistent}
 * or {@code temporary} and stamped with its load time. An empty list renders a single placeholder
 * line so the command always prints something.
 */
@UtilityClass
public final class AttachmentListing {

    private static final String NONE = "(no configuration files)";

    /**
     * Renders the attachment list, one line per file; an empty list renders a placeholder line.
     *
     * @param infos the attachments to render, in order
     * @return the rendered, newline-separated listing
     */
    public static String render(Collection<AttachmentInfo> infos) {
        if (infos.isEmpty()) {
            return NONE;
        }
        return infos.stream().map(AttachmentListing::line).collect(Collectors.joining("\n"));
    }

    private static String line(AttachmentInfo info) {
        return info.kind().label() + "  " + LoadTime.iso(info.loadedAtMillis()) + "  " + info.path();
    }
}
