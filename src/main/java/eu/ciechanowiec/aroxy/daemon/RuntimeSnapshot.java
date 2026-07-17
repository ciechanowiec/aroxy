package eu.ciechanowiec.aroxy.daemon;

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
 * actual bound listen address, the upstream, the active rule count, the daemon start time (so the CLI
 * derives uptime itself), a monotonic {@code generation} the CLI watches to confirm a requested
 * reload has been applied, and the contributing files with their load times. The wire form is one
 * tab-separated record per line, mirroring {@code vault.tsv}.
 *
 * @param listenAddress the {@code host:port} the proxy is bound to
 * @param upstream      the upstream API base address
 * @param ruleCount     the number of enabled rules in effect
 * @param startMillis   when the daemon started, in epoch milliseconds
 * @param generation    a counter bumped on each publish so a reload can be confirmed
 * @param attachments   the contributing files, with kind and load time
 */
@Slf4j
public record RuntimeSnapshot(
    String listenAddress,
    String upstream,
    int ruleCount,
    long startMillis,
    long generation,
    List<AttachmentInfo> attachments
) {

    private static final String TAB = "\t";
    private static final String FILE = "file";
    private static final String LISTEN_KEY = "listen";
    private static final int FILE_FIELDS = 4;
    private static final int PAIR_FIELDS = 2;

    public RuntimeSnapshot {
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
        lines.forEach(line -> route(line, head, files));
        return build(head, files);
    }

    private static void route(String line, Map<String, String> head, Collection<AttachmentInfo> files) {
        String[] parts = line.split(TAB, -1);
        if (parts.length >= FILE_FIELDS && FILE.equals(parts[0])) {
            files.add(new AttachmentInfo(parts[3], AttachmentKind.valueOf(parts[1]), parseLong(parts[2])));
        } else if (parts.length >= PAIR_FIELDS) {
            head.put(parts[0], parts[1]);
        }
    }

    private static Optional<RuntimeSnapshot> build(Map<String, String> head, List<AttachmentInfo> files) {
        if (!head.containsKey(LISTEN_KEY)) {
            return Optional.empty();
        }
        return Optional.of(
            new RuntimeSnapshot(
                head.get(LISTEN_KEY), head.getOrDefault("upstream", ""),
                (int) parseLong(head.getOrDefault("rules", "0")), parseLong(head.getOrDefault("start", "0")),
                parseLong(head.getOrDefault("generation", "0")), files
            )
        );
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException _) {
            return 0L;
        }
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
            this.listenAddress, this.upstream, this.ruleCount,
            Math.max(0, now - this.startMillis), this.attachments
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
        out.add(LISTEN_KEY + TAB + this.listenAddress);
        out.add("upstream" + TAB + this.upstream);
        out.add("rules" + TAB + this.ruleCount);
        out.add("start" + TAB + this.startMillis);
        out.add("generation" + TAB + this.generation);
        this.attachments.forEach(info -> out.add(fileLine(info)));
        return out;
    }
}
