package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.proxy.TrafficTotals;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The daemon's published runtime state, written to {@link Home}{@code /runtime} on start and on every reload
 * and read by the CLI in place of the former {@code GET /status} and {@code GET /attachments}
 * endpoints. It carries exactly what the CLI cannot recompute from the on-disk configuration: the
 * listeners actually bound with the upstream each reaches, the daemon start time
 * (so the CLI derives uptime itself), a monotonic {@code generation} the CLI watches to confirm a
 * requested reload has been applied, the contributing files with their load times, and a safe summary
 * of the configuration actually applied. The wire form is
 * one tab-separated record per line, mirroring {@code vault.tsv}.
 *
 * <p>Listeners are written as one repeated {@code gateway <client> <listen> <upstream>} record each,
 * so registering another client neither reshapes this record nor changes the format.
 *
 * @param gateways    the listeners bound, each with the upstream it forwards to
 * @param startMillis when the daemon started, in epoch milliseconds
 * @param generation  a counter bumped on each publish so a reload can be confirmed
 * @param attachments the contributing files, with kind and load time
 * @param config      the safe summary of the applied configuration
 */
public record RuntimeSnapshot(
    List<GatewayAddress> gateways,
    long startMillis,
    long generation,
    List<AttachmentInfo> attachments,
    RuntimeConfig config
) {

    private static final String TAB = "\t";
    private static final String FILE = "file";
    private static final String GATEWAY = "gateway";
    private static final String MASK_KEY = "config.mask_secrets";
    private static final String RULE_KEY_PREFIX = "config.rule.";
    private static final int RECORD_FIELDS = 4;
    private static final int PAIR_FIELDS = 2;

    public RuntimeSnapshot {
        gateways = List.copyOf(gateways);
        attachments = List.copyOf(attachments);
        config = new RuntimeConfig(config.maskSecrets(), config.ruleIds());
    }

    /**
     * Reads a snapshot from {@code file}, empty when the file is absent or unreadable.
     *
     * @param file the runtime snapshot file to read
     * @return the parsed snapshot, or empty when the file is absent or unreadable
     */
    public static Optional<RuntimeSnapshot> read(Path file) {
        return parse(StateFile.read(file));
    }

    private static String fileLine(AttachmentInfo info) {
        return String.join(TAB, FILE, info.kind().name(), Long.toString(info.loadedAtMillis()), info.path());
    }

    private static String gatewayLine(GatewayAddress gateway) {
        return String.join(TAB, GATEWAY, gateway.client(), gateway.listen(), gateway.upstream());
    }

    private static Optional<RuntimeSnapshot> parse(Iterable<String> lines) {
        Map<String, String> head = new HashMap<>();
        List<AttachmentInfo> files = new ArrayList<>();
        List<GatewayAddress> gateways = new ArrayList<>();
        lines.forEach(line -> route(line, head, files, gateways));
        return build(head, files, gateways);
    }

    private static void route(
        String line, Map<String, String> head,
        Collection<AttachmentInfo> files, Collection<GatewayAddress> gateways
    ) {
        String[] parts = line.split(TAB, -1);
        if (isRecord(parts, FILE)) {
            files.add(new AttachmentInfo(parts[3], AttachmentKind.valueOf(parts[1]), StateFile.number(parts[2])));
        } else if (isRecord(parts, GATEWAY)) {
            gateways.add(new GatewayAddress(parts[1], parts[2], parts[3]));
        } else if (parts.length >= PAIR_FIELDS) {
            head.put(parts[0], parts[1]);
        }
    }

    private static boolean isRecord(String[] parts, String name) {
        return parts.length >= RECORD_FIELDS && name.equals(parts[0]);
    }

    private static Optional<RuntimeSnapshot> build(
        Map<String, String> head, List<AttachmentInfo> files, List<GatewayAddress> gateways
    ) {
        if (gateways.isEmpty() || !head.containsKey(MASK_KEY)) {
            return Optional.empty();
        }
        return Optional.of(
            new RuntimeSnapshot(
                gateways, StateFile.number(head.getOrDefault("start", "0")),
                StateFile.number(head.getOrDefault("generation", "0")), files, runtimeConfig(head)
            )
        );
    }

    private static RuntimeConfig runtimeConfig(Map<String, String> head) {
        List<String> ruleIds = Stream.iterate(0, index -> index + 1)
            .map(index -> head.get(RULE_KEY_PREFIX + index))
            .takeWhile(Objects::nonNull)
            .toList();
        return new RuntimeConfig(Boolean.parseBoolean(head.get(MASK_KEY)), ruleIds);
    }

    @Override
    public List<GatewayAddress> gateways() {
        return List.copyOf(this.gateways);
    }

    @Override
    public List<AttachmentInfo> attachments() {
        return List.copyOf(this.attachments);
    }

    /**
     * Renders a {@link StatusReport} for the CLI with no traffic, deriving uptime from {@code now} and the start.
     *
     * @param now the current time in epoch milliseconds, used to derive uptime
     * @return the status report for the CLI
     */
    public StatusReport toStatusReport(long now) {
        return this.toStatusReport(now, Optional.empty());
    }

    /**
     * Renders a {@link StatusReport} for the CLI, deriving uptime and the age of the latest request
     * from {@code now}.
     *
     * @param now     the current time in epoch milliseconds, used to derive uptime and request age
     * @param traffic the traffic counters the daemon published, or empty when none were read
     * @return the status report for the CLI
     */
    public StatusReport toStatusReport(long now, Optional<TrafficTotals> traffic) {
        return new StatusReport(
            this.gateways, this.config.ruleIds().size(), Math.max(0, now - this.startMillis), this.attachments,
            traffic.map(totals -> TrafficReport.at(totals, now))
        );
    }

    /**
     * Renders the configuration actually applied by the daemon.
     *
     * @return the safe active-configuration summary
     */
    public String renderConfig() {
        return new ConfigSummary(
            this.gateways, this.config.maskSecrets(),
            this.attachments.stream().map(AttachmentInfo::path).toList(), this.config.ruleIds()
        ).render();
    }

    /**
     * Writes the snapshot to {@code file}, replacing any previous content.
     */
    void writeTo(Path file) {
        StateFile.write(file, this.lines());
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("start" + TAB + this.startMillis);
        out.add("generation" + TAB + this.generation);
        addConfigLines(out, this.config);
        this.gateways.forEach(gateway -> out.add(gatewayLine(gateway)));
        this.attachments.forEach(info -> out.add(fileLine(info)));
        return out;
    }

    private static void addConfigLines(Collection<String> lines, RuntimeConfig config) {
        lines.add(MASK_KEY + TAB + config.maskSecrets());
        int index = 0;
        for (String ruleId : config.ruleIds()) {
            lines.add(RULE_KEY_PREFIX + index + TAB + ruleId);
            index++;
        }
    }
}
