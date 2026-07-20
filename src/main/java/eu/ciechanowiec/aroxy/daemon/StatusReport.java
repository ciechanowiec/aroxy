package eu.ciechanowiec.aroxy.daemon;

import java.util.List;

/**
 * An immutable snapshot of the daemon's runtime state, as rendered by the CLI. Gateways are the
 * listeners bound, one per client served, each with the upstream it reaches. Attachments are the files
 * currently contributing to the effective configuration (persistent registry entries plus in-session
 * temporaries), each with its kind and load time.
 *
 * @param gateways     the listeners bound, each with the upstream it forwards to
 * @param ruleCount    the number of enabled rules in effect
 * @param uptimeMillis how long the daemon has been running
 * @param attachments  the contributing attachment files, with kind and load time
 */
public record StatusReport(
    List<GatewayAddress> gateways,
    int ruleCount,
    long uptimeMillis,
    List<AttachmentInfo> attachments
) {

    public StatusReport {
        gateways = List.copyOf(gateways);
        attachments = List.copyOf(attachments);
    }

    @Override
    public List<GatewayAddress> gateways() {
        return List.copyOf(this.gateways);
    }

    @Override
    public List<AttachmentInfo> attachments() {
        return List.copyOf(this.attachments);
    }
}
