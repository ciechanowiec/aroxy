package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import java.util.List;

/**
 * The parts of a {@link RuntimeSnapshot} the {@link Daemon} computes on each publish: the live
 * listeners with the upstream each reaches, the active configuration summary, and the contributing files. The
 * {@link SnapshotPublisher} adds the start time and the generation counter, which it owns, to turn
 * this into a full snapshot. Bundling these keeps the publisher's {@code publish} within the parameter
 * limit.
 *
 * @param gateways the listeners bound, each with the upstream it forwards to
 * @param config   the safe-to-display portion of the active configuration
 * @param files    the contributing files, with kind and load time
 */
record SnapshotData(List<GatewayAddress> gateways, RuntimeConfig config, List<AttachmentInfo> files) {

    SnapshotData {
        gateways = List.copyOf(gateways);
        files = List.copyOf(files);
    }

    static SnapshotData from(
        List<GatewayAddress> gateways, EffectiveConfig effective, List<AttachmentInfo> files
    ) {
        RuntimeConfig config = new RuntimeConfig(
            effective.settings().maskSecrets(), effective.rules().stream().map(Rule::id).toList()
        );
        return new SnapshotData(gateways, config, files);
    }
}
