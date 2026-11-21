package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The live counters behind {@code aroxy status}: each exchange is counted with its start time, an
 * exchange counts as redacted only when told it newly is, and a {@link TrafficTotals} read is a copy
 * that later traffic does not change.
 */
class TrafficStatsTest {

    private static final long SENT = 120L;
    private static final long RECEIVED = 4500L;
    private static final int REPLACEMENTS = 3;

    @Test
    void startsAtNone() {
        assertEquals(TrafficTotals.NONE, new TrafficStats().totals());
    }

    @Test
    void countsExchangesMatchesAndBytes() {
        TrafficStats stats = new TrafficStats();
        stats.exchanged();
        stats.matched(REPLACEMENTS, true);
        stats.matched(1, false);
        stats.matched(0, false);
        stats.sent(SENT);
        stats.received(RECEIVED);
        TrafficTotals totals = stats.totals();
        assertEquals(1L, totals.requests(), totals.toString());
        assertEquals(1L, totals.redacted(), "only a newly redacted exchange is counted: " + totals);
        assertEquals(REPLACEMENTS + 1L, totals.replaced(), "every replacement is counted: " + totals);
        assertEquals(SENT, totals.sentBytes(), totals.toString());
        assertEquals(RECEIVED, totals.receivedBytes(), totals.toString());
    }

    @Test
    void stampsTheLatestExchange() {
        TrafficStats stats = new TrafficStats();
        long before = System.currentTimeMillis();
        stats.exchanged();
        assertTrue(stats.totals().lastRequestMillis() >= before, stats.totals().toString());
    }

    @Test
    void totalsAreACopyThatLaterTrafficLeavesAlone() {
        TrafficStats stats = new TrafficStats();
        stats.exchanged();
        TrafficTotals first = stats.totals();
        stats.exchanged();
        assertEquals(1L, first.requests(), first.toString());
        assertEquals(2L, stats.totals().requests());
    }
}
