package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.proxy.TrafficTotals;
import java.time.Duration;
import java.util.Optional;

/**
 * The traffic part of a {@link StatusReport}: the published counters, plus how long ago the latest
 * request began. The age is derived from the current time when the report is built, the same way the
 * uptime is, so rendering stays a pure function of the report.
 *
 * @param totals           the published traffic counters
 * @param sinceLastRequest how long ago the latest request began, or empty when none has
 */
public record TrafficReport(TrafficTotals totals, Optional<Duration> sinceLastRequest) {

    /**
     * Builds the report for {@code totals} as seen at {@code now}.
     *
     * @param totals the published traffic counters
     * @param now    the current time in epoch milliseconds
     * @return the traffic report
     */
    static TrafficReport at(TrafficTotals totals, long now) {
        Optional<Duration> age = Optional.of(totals.lastRequestMillis())
            .filter(last -> last > 0)
            .map(last -> Duration.ofMillis(Math.max(0, now - last)));
        return new TrafficReport(totals, age);
    }
}
