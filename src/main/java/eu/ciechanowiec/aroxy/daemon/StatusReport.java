package eu.ciechanowiec.aroxy.daemon;

import java.util.List;

/**
 * An immutable snapshot of the daemon's runtime state, as returned by {@code GET /status} and
 * rendered by the CLI. Attachments are the files currently contributing to the effective
 * configuration (persistent registry entries plus in-session temporaries), each with its kind and
 * load time.
 *
 * @param listenAddress the {@code host:port} the proxy is bound to
 * @param upstream      the upstream API base address
 * @param ruleCount     the number of enabled rules in effect
 * @param uptimeMillis  how long the daemon has been running
 * @param attachments   the contributing attachment files, with kind and load time
 */
public record StatusReport(
    String listenAddress,
    String upstream,
    int ruleCount,
    long uptimeMillis,
    List<AttachmentInfo> attachments
) {

    public StatusReport {
        attachments = List.copyOf(attachments);
    }

    @Override
    public List<AttachmentInfo> attachments() {
        return List.copyOf(this.attachments);
    }
}
