package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.proxy.TrafficTotals;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * The wire form of {@link Home}{@code /stats}, the traffic counters the daemon publishes for
 * {@code aroxy status}: one tab-separated {@code key value} line per counter, mirroring
 * {@link RuntimeSnapshot}. The file holds only counts and a time, never a matched value or a rule's
 * text, so it is as safe to read as the rule ids already in {@code runtime}.
 *
 * <p>It is kept apart from {@code runtime} because it changes with every request, while the
 * {@code runtime} generation is the handshake a reload is confirmed by.
 */
@UtilityClass
public final class TrafficFile {

    private static final String TAB = "\t";
    private static final String REQUESTS = "requests";
    private static final String REDACTED = "redacted";
    private static final String REPLACED = "replaced";
    private static final String SENT = "sent";
    private static final String RECEIVED = "received";
    private static final String LAST_REQUEST = "last_request";
    private static final int PAIR_FIELDS = 2;

    /**
     * Where the daemon publishes its traffic counters.
     *
     * @param home the state directory layout
     * @return the {@code stats} path
     */
    public static Path in(Home home) {
        return home.base().resolve("stats");
    }

    /**
     * Reads the published traffic counters.
     *
     * @param file the stats file to read
     * @return the counters, or empty when the file is absent, unreadable, or not a stats file
     */
    public static Optional<TrafficTotals> read(Path file) {
        Map<String, String> fields = new HashMap<>();
        StateFile.read(file).stream()
            .map(line -> line.split(TAB, -1))
            .filter(parts -> parts.length >= PAIR_FIELDS)
            .forEach(parts -> fields.put(parts[0], parts[1]));
        return Optional.of(fields).filter(map -> map.containsKey(REQUESTS)).map(TrafficFile::totals);
    }

    /**
     * Renders the counters as the lines of a stats file.
     *
     * @param totals the counters to render
     * @return one line per counter
     */
    static List<String> lines(TrafficTotals totals) {
        return List.of(
            REQUESTS + TAB + totals.requests(),
            REDACTED + TAB + totals.redacted(),
            REPLACED + TAB + totals.replaced(),
            SENT + TAB + totals.sentBytes(),
            RECEIVED + TAB + totals.receivedBytes(),
            LAST_REQUEST + TAB + totals.lastRequestMillis()
        );
    }

    private static TrafficTotals totals(Map<String, String> fields) {
        return new TrafficTotals(
            field(fields, REQUESTS), field(fields, REDACTED), field(fields, REPLACED),
            field(fields, SENT), field(fields, RECEIVED), field(fields, LAST_REQUEST)
        );
    }

    private static long field(Map<String, String> fields, String key) {
        return StateFile.number(fields.getOrDefault(key, "0"));
    }
}
