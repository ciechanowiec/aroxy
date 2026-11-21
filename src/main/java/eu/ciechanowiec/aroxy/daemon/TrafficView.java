package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.proxy.TrafficTotals;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * Renders the traffic lines of {@code aroxy status} from a {@link TrafficReport}: requests with how
 * many were redacted, bytes out and in, the replacements made, and how long ago the latest request
 * began. Byte counts use decimal units, so {@code 1.0 kB} is exactly a thousand bytes.
 *
 * <p>The labels are padded to the column {@link StatusView} aligns its own labels to.
 */
@UtilityClass
final class TrafficView {

    private static final List<String> UNITS = List.of("B", "kB", "MB", "GB", "TB");
    private static final double STEP = 1000.0;
    /**
     * The largest value still shown in a unit. Anything above it would round to {@code 1000.0} with one
     * decimal, so it moves to the next unit instead.
     */
    private static final double ROLLOVER = 999.95;

    /**
     * Renders the traffic lines, unindented.
     *
     * @param report the traffic report to render
     * @return the four traffic lines
     */
    static List<String> lines(TrafficReport report) {
        TrafficTotals totals = report.totals();
        return List.of(
            "Requests     " + totals.requests() + " (" + totals.redacted() + " redacted)",
            "Traffic      " + bytes(totals.sentBytes()) + " out, " + bytes(totals.receivedBytes()) + " in",
            "Replaced     " + totals.replaced() + (totals.replaced() == 1 ? " value" : " values"),
            "Last request " + report.sinceLastRequest().map(TrafficView::ago).orElse("never")
        );
    }

    /**
     * Formats a byte count with one decimal in the largest decimal unit that keeps it below a thousand.
     *
     * @param count the number of bytes
     * @return the formatted size, such as {@code 999 B} or {@code 18.4 MB}
     */
    static String bytes(long count) {
        if (count < STEP) {
            return String.format(Locale.ROOT, "%d B", count);
        }
        double value = count;
        int unit = 0;
        while (value >= ROLLOVER && unit < UNITS.size() - 1) {
            value /= STEP;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, UNITS.get(unit));
    }

    private static String ago(Duration elapsed) {
        String span = elapsed.toHours() > 0
            ? String.format(Locale.ROOT, "%dh %dm", elapsed.toHours(), elapsed.toMinutesPart())
            : minutesAndSeconds(elapsed);
        return span + " ago";
    }

    private static String minutesAndSeconds(Duration elapsed) {
        return elapsed.toMinutes() > 0
            ? String.format(Locale.ROOT, "%dm %ds", elapsed.toMinutes(), elapsed.toSecondsPart())
            : String.format(Locale.ROOT, "%ds", elapsed.toSeconds());
    }
}
