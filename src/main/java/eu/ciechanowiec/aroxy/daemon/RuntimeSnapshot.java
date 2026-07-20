package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.ClientRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * The daemon's published runtime state, written to {@link Home}{@code /runtime} on start and on every reload
 * and read by the CLI in place of the former {@code GET /status} and {@code GET /attachments}
 * endpoints. It carries exactly what the CLI cannot recompute from the on-disk configuration: the
 * listeners actually bound with the upstream each reaches, the active rule count, the daemon start time
 * (so the CLI derives uptime itself), a monotonic {@code generation} the CLI watches to confirm a
 * requested reload has been applied, and the contributing files with their load times. The wire form is
 * one tab-separated record per line, mirroring {@code vault.tsv}.
 *
 * <p>Listeners are written as one repeated {@code gateway <client> <listen> <upstream>} record each,
 * so registering another client neither reshapes this record nor changes the format. Two
 * compatibility rules keep a snapshot
 * readable across an upgrade, since a daemon started by one build can be read by the CLI of another:
 * the first listener is <em>also</em> written as the historical {@code listen}/{@code upstream} pair
 * that an older CLI looks for, and a snapshot carrying only that pair is read back as a single
 * listener.
 *
 * @param gateways    the listeners bound, each with the upstream it forwards to
 * @param ruleCount   the number of enabled rules in effect
 * @param startMillis when the daemon started, in epoch milliseconds
 * @param generation  a counter bumped on each publish so a reload can be confirmed
 * @param attachments the contributing files, with kind and load time
 */
@Slf4j
public record RuntimeSnapshot(
    List<GatewayAddress> gateways,
    int ruleCount,
    long startMillis,
    long generation,
    List<AttachmentInfo> attachments
) {

    private static final String TAB = "\t";
    private static final String FILE = "file";
    private static final String GATEWAY = "gateway";
    private static final String LISTEN_KEY = "listen";
    private static final String UPSTREAM_KEY = "upstream";
    private static final String NO_CLIENT = "(none)";
    private static final int RECORD_FIELDS = 4;
    private static final int PAIR_FIELDS = 2;

    public RuntimeSnapshot {
        gateways = List.copyOf(gateways);
        attachments = List.copyOf(attachments);
    }

    /**
     * Reads a snapshot from {@code file}, empty when the file is absent or unreadable.
     *
     * @param file the runtime snapshot file to read
     * @return the parsed snapshot, or empty when the file is absent or unreadable
     */
    public static Optional<RuntimeSnapshot> read(Path file) {
        return Files.exists(file) ? parse(readLines(file)) : Optional.empty();
    }

    private static String fileLine(AttachmentInfo info) {
        return String.join(TAB, FILE, info.kind().name(), Long.toString(info.loadedAtMillis()), info.path());
    }

    private static String gatewayLine(GatewayAddress gateway) {
        return String.join(TAB, GATEWAY, gateway.client(), gateway.listen(), gateway.upstream());
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException exception) {
            log.warn("Could not read runtime snapshot {}: {}", file, exception.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings(
        "PMD.UseConcurrentHashMap"
    ) // parsed once on one thread from a small fixed file
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
            files.add(new AttachmentInfo(parts[3], AttachmentKind.valueOf(parts[1]), parseLong(parts[2])));
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
        if (!head.containsKey(LISTEN_KEY)) {
            return Optional.empty();
        }
        return Optional.of(
            new RuntimeSnapshot(
                listeners(head, gateways),
                (int) parseLong(head.getOrDefault("rules", "0")), parseLong(head.getOrDefault("start", "0")),
                parseLong(head.getOrDefault("generation", "0")), files
            )
        );
    }

    /**
     * The listeners a snapshot declares, falling back to the historical single {@code listen}/
     * {@code upstream} pair when it carries no listener record - which is what a snapshot written
     * before this format existed looks like.
     */
    private static List<GatewayAddress> listeners(Map<String, String> head, List<GatewayAddress> gateways) {
        return gateways.isEmpty() ? List.of(legacyListener(head)) : gateways;
    }

    private static GatewayAddress legacyListener(Map<String, String> head) {
        return new GatewayAddress(
            primaryClientName(), head.getOrDefault(LISTEN_KEY, ""), head.getOrDefault(UPSTREAM_KEY, "")
        );
    }

    private static String primaryClientName() {
        return ClientRegistry.profiles().stream()
            .findFirst()
            .map(ClientProfile::displayName)
            .orElse(NO_CLIENT);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException _) {
            return 0L;
        }
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
     * Renders a {@link StatusReport} for the CLI, deriving uptime from {@code now} and the start.
     *
     * @param now the current time in epoch milliseconds, used to derive uptime
     * @return the status report for the CLI
     */
    public StatusReport toStatusReport(long now) {
        return new StatusReport(
            this.gateways, this.ruleCount, Math.max(0, now - this.startMillis), this.attachments
        );
    }

    /**
     * Writes the snapshot to {@code file}, replacing any previous content.
     */
    void writeTo(Path file) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.write(
                temporary, this.lines(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            );
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            log.warn("Could not publish runtime snapshot {}: {}", file, exception.getMessage());
        }
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(LISTEN_KEY + TAB + this.primary().listen());
        out.add(UPSTREAM_KEY + TAB + this.primary().upstream());
        out.add("rules" + TAB + this.ruleCount);
        out.add("start" + TAB + this.startMillis);
        out.add("generation" + TAB + this.generation);
        this.gateways.forEach(gateway -> out.add(gatewayLine(gateway)));
        this.attachments.forEach(info -> out.add(fileLine(info)));
        return out;
    }

    /**
     * The listener written as the historical {@code listen}/{@code upstream} pair, so an older CLI
     * still finds the key it guards on. A snapshot with nothing bound has no listener to name, so it
     * renders blanks under a neutral label rather than crediting one agent arbitrarily.
     */
    private GatewayAddress primary() {
        return this.gateways.stream().findFirst().orElseGet(() -> new GatewayAddress(NO_CLIENT, "", ""));
    }
}
