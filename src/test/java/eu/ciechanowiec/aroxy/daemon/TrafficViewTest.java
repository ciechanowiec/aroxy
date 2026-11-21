package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.aroxy.proxy.TrafficTotals;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The traffic lines of {@code aroxy status}: counts in plain numbers, sizes in decimal units with one
 * decimal, and the latest request as a short age, or {@code never} before the first one.
 */
class TrafficViewTest {

    private static final long LAST_MILLIS = 1_000_000L;
    private static final long SECONDS_LATER = LAST_MILLIS + Duration.ofSeconds(4).toMillis();
    private static final long MINUTES_LATER = LAST_MILLIS + Duration.ofSeconds(192).toMillis();
    private static final long HOURS_LATER = LAST_MILLIS + Duration.ofMinutes(125).toMillis();
    private static final long SENT = 18_400_000L;
    private static final long JUST_UNDER_KILO = 999L;
    private static final long KILO = 1000L;
    private static final long ROUNDS_UP = 999_950L;
    private static final long GIGA_AND_HALF = 2_500_000_000L;
    private static final TrafficTotals BUSY = new TrafficTotals(
        412L, 57L, 203L, SENT, 96_100_000L, LAST_MILLIS
    );

    @Test
    void rendersTheFourLines() {
        assertEquals(
            List.of(
                "Requests     412 (57 redacted)",
                "Traffic      18.4 MB out, 96.1 MB in",
                "Replaced     203 values",
                "Last request 4s ago"
            ),
            TrafficView.lines(TrafficReport.at(BUSY, SECONDS_LATER))
        );
    }

    @Test
    void rendersAnIdleDaemon() {
        assertEquals(
            List.of(
                "Requests     0 (0 redacted)",
                "Traffic      0 B out, 0 B in",
                "Replaced     0 values",
                "Last request never"
            ),
            TrafficView.lines(TrafficReport.at(TrafficTotals.NONE, SECONDS_LATER))
        );
    }

    @Test
    void rendersOneReplacementInTheSingular() {
        TrafficTotals one = new TrafficTotals(1L, 1L, 1L, 0L, 0L, LAST_MILLIS);
        assertEquals("Replaced     1 value", TrafficView.lines(TrafficReport.at(one, LAST_MILLIS)).get(2));
    }

    @Test
    void rendersTheAgeInItsLargestUnits() {
        assertEquals("Last request 3m 12s ago", lastLine(MINUTES_LATER));
        assertEquals("Last request 2h 5m ago", lastLine(HOURS_LATER));
    }

    @Test
    void rendersSizesInDecimalUnits() {
        assertEquals("999 B", TrafficView.bytes(JUST_UNDER_KILO));
        assertEquals("1.0 kB", TrafficView.bytes(KILO));
        assertEquals("1.0 MB", TrafficView.bytes(ROUNDS_UP), "a size that would round to 1000.0 kB moves up");
        assertEquals("18.4 MB", TrafficView.bytes(SENT));
        assertEquals("2.5 GB", TrafficView.bytes(GIGA_AND_HALF));
    }

    @Test
    void neverReportsANegativeAge() {
        assertEquals(Optional.of(Duration.ZERO), TrafficReport.at(BUSY, 0L).sinceLastRequest());
    }

    private static String lastLine(long now) {
        return TrafficView.lines(TrafficReport.at(BUSY, now)).getLast();
    }
}
