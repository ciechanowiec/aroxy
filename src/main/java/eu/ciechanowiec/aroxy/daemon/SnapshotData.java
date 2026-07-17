package eu.ciechanowiec.aroxy.daemon;

import java.util.List;

/**
 * The parts of a {@link RuntimeSnapshot} the {@link Daemon} computes on each publish: the live listen
 * address, the upstream, the active rule count, and the contributing files. The {@link
 * SnapshotPublisher} adds the start time and the generation counter, which it owns, to turn this into
 * a full snapshot. Bundling these four keeps the publisher's {@code publish} within the parameter
 * limit.
 *
 * @param listenAddress the {@code host:port} the proxy is bound to
 * @param upstream      the upstream API base address
 * @param ruleCount     the number of enabled rules in effect
 * @param files         the contributing files, with kind and load time
 */
record SnapshotData(String listenAddress, String upstream, int ruleCount, List<AttachmentInfo> files) {

    SnapshotData {
        files = List.copyOf(files);
    }
}
