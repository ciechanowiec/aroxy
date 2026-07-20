package eu.ciechanowiec.aroxy.daemon;

import java.util.List;

/**
 * The parts of a {@link RuntimeSnapshot} the {@link Daemon} computes on each publish: the live
 * listeners with the upstream each reaches, the active rule count, and the contributing files. The
 * {@link SnapshotPublisher} adds the start time and the generation counter, which it owns, to turn
 * this into a full snapshot. Bundling these keeps the publisher's {@code publish} within the parameter
 * limit.
 *
 * @param gateways  the listeners bound, each with the upstream it forwards to
 * @param ruleCount the number of enabled rules in effect
 * @param files     the contributing files, with kind and load time
 */
record SnapshotData(List<GatewayAddress> gateways, int ruleCount, List<AttachmentInfo> files) {

    SnapshotData {
        gateways = List.copyOf(gateways);
        files = List.copyOf(files);
    }
}
